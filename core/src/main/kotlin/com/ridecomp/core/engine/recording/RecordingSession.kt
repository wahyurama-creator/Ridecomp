package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.metrics.AltitudeSource
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.RouteTurn
import com.ridecomp.core.engine.sensors.SensorReading
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.source.IdGenerator
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.RideNameProvider
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.onSuccess
import com.ridecomp.core.util.runCatchingCancellable

/** Everything that configures one ride. */
public data class RideSetup(
    val settings: RideSettings,
    val altitudeSource: AltitudeSource,
    val profile: RiderProfile = RiderProfile(),
    val ageYears: Int? = null,
    /** Activity profile of this ride (12-FEATURE-SPECS §4); stored on the activity. */
    val profileId: Long? = null,
    /** False for Indoor: no GPS, speed and distance from the wheel sensor. */
    val isGpsEnabled: Boolean = true,
    /** Followed route (T3.6); stored on the activity. */
    val routeId: Long? = null,
    val routePoints: List<RoutePoint> = emptyList(),
    val routeClimbs: List<RouteClimb> = emptyList(),
    val routeTurns: List<RouteTurn> = emptyList(),
)

/** Why a session command could not run. */
public sealed interface SessionError {
    public data object NoActiveRide : SessionError

    public data object AlreadyRecording : SessionError

    public data class Rejected(val reason: InvalidRideCommand) : SessionError
}

/**
 * One ride from start to save/discard: drives [RideRecorder] and persists its output — events and laps
 * immediately, track points in 5 s batches followed by a checkpoint (03-ARCHITECTURE §4.5).
 *
 * Database failures are logged and retried on the next flush; they never stop the recording (STB-08,
 * ERR-04). Confine to one coroutine (CONC-01): the RecordingService calls everything from its scope.
 */
public class RecordingSession(
    private val repository: ActivityRepository,
    private val ids: IdGenerator,
    private val names: RideNameProvider,
    private val logger: Logger,
) {
    private var recorder: RideRecorder? = null
    private val buffer = TrackPointBuffer()

    /** Id of the ride being recorded, null when none. */
    public var activityId: Long? = null
        private set

    public val status: RideStatus get() = recorder?.currentStatus ?: RideStatus.IDLE

    public val pointsDropped: Int get() = buffer.droppedCount

    /** Starts a new ride and creates its activity row. */
    public suspend fun start(setup: RideSetup, nowMs: Long): Outcome<RecorderOutput, SessionError> {
        if (recorder != null) return SessionError.AlreadyRecording.asFailure()
        val newRecorder = setup.newRecorder()
        val output = newRecorder.handle(RideAction.START, nowMs)
        return output.mapErrorToSession().onSuccess { started ->
            recorder = newRecorder
            activityId =
                repository.create(
                    NewActivity(ids.newId(), names.defaultName(nowMs), nowMs, setup.profileId, setup.routeId),
                )
            persist(started, nowMs)
        }
    }

    /** Continues a ride interrupted by a crash/kill, from its last checkpoint (03-ARCHITECTURE §4.5). */
    public suspend fun resume(activity: Activity, setup: RideSetup, resumeAs: RideStatus, nowMs: Long) {
        val restored = setup.newRecorder()
        val totals = activity.totals
        restored.restore(
            startedAtMs = activity.startedAtMs,
            totals = RecoveredTotals(
                distanceM = totals.distanceM,
                movingMs = totals.movingS * Units.MS_PER_SECOND,
                maxSpeedMps = totals.maxSpeedMps,
                ascentM = totals.ascentM,
                descentM = totals.descentM,
                lapIndex = repository.getLaps(activity.id).size,
            ),
            resumeAs = resumeAs,
            nowMs = nowMs,
        )
        recorder = restored
        activityId = activity.id
    }

    public fun onLocation(fix: LocationFix) {
        recorder?.onLocation(fix)?.let { rejection -> logger.debug(TAG) { "GPS fix dropped: $rejection" } }
    }

    /** Parsed BLE data (heart rate, speed/cadence, power, battery, connection). */
    public fun onSensor(reading: SensorReading) {
        recorder?.onSensor(reading)
    }

    public fun onAltitude(altitudeM: Double, timeMs: Long) {
        recorder?.onAltitude(altitudeM, timeMs)
    }

    public fun onBattery(batteryStatus: BatteryStatus, nowMs: Long) {
        recorder?.onBattery(batteryStatus, nowMs)
    }

    /** GPS interval the service should request now (BAT-03). */
    public fun desiredGpsIntervalMs(nowMs: Long, defaultMs: Long): Long =
        recorder?.desiredGpsIntervalMs(nowMs) ?: defaultMs

    /** One pipeline step (1 Hz). Returns null when no ride is active. */
    public suspend fun tick(nowMs: Long): RecorderOutput? {
        val output = recorder?.tick(nowMs) ?: return null
        persist(output, nowMs)
        return output
    }

    /** Applies a user action and persists the result; SAVE/DISCARD end the session. */
    public suspend fun perform(action: RideAction, nowMs: Long): Outcome<RecorderOutput, SessionError> {
        val active = recorder ?: return SessionError.NoActiveRide.asFailure()
        return active.handle(action, nowMs).mapErrorToSession().onSuccess { output ->
            persist(output, nowMs)
            when (action) {
                RideAction.FINISH -> flush(nowMs)
                RideAction.SAVE -> save(nowMs)
                RideAction.DISCARD -> discard()
                RideAction.START, RideAction.PAUSE, RideAction.RESUME, RideAction.LAP, RideAction.ACK_REMINDER,
                RideAction.SNOOZE_REMINDER, RideAction.KEEP_RIDING,
                -> Unit
            }
        }
    }

    /** Battery ≤ 5 %: write everything now so the ride survives the phone dying (12-FEATURE-SPECS §3). */
    public suspend fun emergencyCheckpoint(nowMs: Long) {
        flush(nowMs)
    }

    private suspend fun persist(output: RecorderOutput, nowMs: Long) {
        val id = activityId ?: return
        output.sample?.let(buffer::add)
        if (output.events.isNotEmpty()) safely("events") { repository.appendEvents(id, output.events) }
        output.laps.forEach { lap -> safely("lap") { repository.saveLap(id, lap) } }
        if (buffer.isFlushDue(nowMs)) flush(nowMs)
    }

    /** Writes pending points then the checkpoint. On failure points stay buffered for the next try. */
    private suspend fun flush(nowMs: Long) {
        val id = activityId ?: return
        val active = recorder ?: return
        val points = buffer.peekAll()
        safely("flush") {
            if (points.isNotEmpty()) repository.appendSamples(id, points)
            buffer.onFlushed(points.size, nowMs)
            repository.checkpoint(id, active.currentStatus.toActivityStatus(), active.totals(nowMs), nowMs)
        }
    }

    private suspend fun save(nowMs: Long) {
        val id = activityId ?: return
        val active = recorder ?: return
        flush(nowMs)
        val name = repository.get(id)?.name ?: names.defaultName(nowMs)
        safely("finish") { repository.finish(id, name, active.totals(nowMs), nowMs) }
        end()
    }

    private suspend fun discard() {
        activityId?.let { id -> safely("delete") { repository.delete(id) } }
        end()
    }

    private fun end() {
        recorder = null
        activityId = null
        buffer.onFlushed(buffer.size, 0L)
    }

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        runCatchingCancellable { block() }.onFailure { error ->
            logger.error(TAG, error) { "Database write failed ($what); will retry" }
        }
    }

    private fun RideSetup.newRecorder() = RideRecorder(
        settings,
        altitudeSource,
        profile,
        ageYears,
        RideContext(profileId, isGpsEnabled, routeId, routePoints),
    )

    private fun RideStatus.toActivityStatus(): ActivityStatus = when (this) {
        RideStatus.RECORDING, RideStatus.AUTO_PAUSED -> ActivityStatus.RECORDING
        RideStatus.PAUSED, RideStatus.FINISHED, RideStatus.IDLE -> ActivityStatus.PAUSED
    }

    private fun Outcome<RecorderOutput, InvalidRideCommand>.mapErrorToSession(): Outcome<RecorderOutput, SessionError> =
        when (this) {
            is Outcome.Success -> this
            is Outcome.Failure -> SessionError.Rejected(error).asFailure()
        }

    private companion object {
        const val TAG = "RecordingSession"
    }
}
