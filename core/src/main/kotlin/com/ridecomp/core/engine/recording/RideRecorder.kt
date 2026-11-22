package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.AutoPauseConfig
import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.config.RecordingConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.geo.GpsRejection
import com.ridecomp.core.engine.metrics.AltitudeSource
import com.ridecomp.core.engine.metrics.ElevationSnapshot
import com.ridecomp.core.engine.metrics.ElevationTracker
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RouteFollower
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.RouteProgress
import com.ridecomp.core.engine.route.RouteTurn
import com.ridecomp.core.engine.safety.IncidentState
import com.ridecomp.core.engine.segments.LiveSegment
import com.ridecomp.core.engine.segments.LiveSegmentFollower
import com.ridecomp.core.engine.segments.LiveSegmentState
import com.ridecomp.core.engine.sensors.SensorReading
import com.ridecomp.core.engine.sensors.SensorSnapshot
import com.ridecomp.core.engine.training.TargetStatus
import com.ridecomp.core.model.BatteryMode
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.map

/** User actions on a ride. */
public enum class RideAction {
    START,
    PAUSE,
    RESUME,
    LAP,
    FINISH,
    SAVE,
    DISCARD,
    ACK_REMINDER,
    SNOOZE_REMINDER,

    /** "Continue" on the "Finish ride?" prompt: hide it and keep waiting for movement. */
    KEEP_RIDING,
}

/** Everything the ride screen and notification show, refreshed at most once per second. */
public data class LiveRide(
    val status: RideStatus = RideStatus.IDLE,
    val nowMs: Long = 0,
    val startedAtMs: Long? = null,
    val elapsedMs: Long = 0,
    val movingMs: Long = 0,
    val distanceM: Double = 0.0,
    val speedMps: Double? = null,
    val avgSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val elevation: ElevationSnapshot = ElevationSnapshot(null, 0.0, 0.0, null),
    val lap: LapStatus? = null,
    val hasGpsFix: Boolean = false,
    val gpsAccuracyM: Double? = null,
    val batteryMode: BatteryMode = BatteryMode.NORMAL,
    /** Last phone battery reading (status strip, 13-UI-SCREENS §2); null before the first one. */
    val phoneBatteryPercent: Int? = null,
    /** Start of the current manual or auto pause ("diam 1:42", 13-UI-SCREENS #14). */
    val pausedSinceMs: Long? = null,
    val reminder: ActiveReminder? = null,
    val isIdlePromptVisible: Boolean = false,
    /** Heart rate, cadence, power, estimates and sensor states (Phase 2). */
    val sensors: LiveSensors = LiveSensors(),
    /** False for an Indoor ride: GPS state is not shown (12-FEATURE-SPECS §4). */
    val isGpsEnabled: Boolean = true,
    val profileId: Long? = null,
    /** Incident countdown / alert state, merged in by the recording service (T2.16). */
    val incident: IncidentState = IncidentState.None,
    /** Followed route (T3.6), null without a route or before the first fix. */
    val route: RouteProgress? = null,
    /** Live personal segment (T4.3); null without segments nearby. */
    val segment: LiveSegmentState? = null,
    /** Current position for the breadcrumb page (fresh fix only); stays in memory, never logged. */
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val routeId: Long? = null,
    /** Today's planned session against the ride (T7.4b); set by the recording service, null without one. */
    val target: TargetStatus? = null,
)

/** Ride facts that do not change during the ride. */
public data class RideContext(
    val profileId: Long? = null,
    val isGpsEnabled: Boolean = true,
    val routeId: Long? = null,
    /** Points of the followed route; tracking starts when there are at least two. */
    val routePoints: List<RoutePoint> = emptyList(),
    val routeClimbs: List<RouteClimb> = emptyList(),
    val routeTurns: List<RouteTurn> = emptyList(),
    /** Personal segments with their records, for live segments (T4.3). */
    val segments: List<LiveSegment> = emptyList(),
)

/** Result of a tick or an action: new state plus what must be persisted. */
public data class RecorderOutput(
    val live: LiveRide,
    val events: List<RideEvent> = emptyList(),
    val laps: List<Lap> = emptyList(),
    /** Record of this second, only while the timer runs (RECORDING). */
    val sample: RideSample? = null,
    val batteryChange: BatteryModeChange? = null,
)

/**
 * The recording pipeline (03-ARCHITECTURE §4, ARCH-08): pure domain logic driven by the
 * RecordingService. Inputs arrive through [onLocation]/[onAltitude]/[onBattery]; [tick] is called once
 * per second and produces one record per second (SampleMerger, §4.3) plus events and laps.
 *
 * Not thread-safe by design: confine to one coroutine (CONC-01).
 */
public class RideRecorder(
    settings: RideSettings,
    private val altitudeSource: AltitudeSource,
    profile: RiderProfile = RiderProfile(),
    ageYears: Int? = null,
    private val context: RideContext = RideContext(),
) {
    private val route = RouteFollower(context.routePoints, context.routeClimbs, context.routeTurns)
    private val metrics = RideMetricsAccumulator(route.alongRoute)
    private val segments = LiveSegmentFollower(context.segments)
    private val sensors = SensorRideMetrics(profile, ageYears)
    private var sensorSnapshot = SensorSnapshot()
    private var estimatedPowerW: Int? = null
    private val elevation = ElevationTracker(altitudeSource)
    private val laps = LapTracker(settings.autoLapM)
    private val autoPause = AutoPauseDetector(
        AutoPauseSettings(
            isEnabled = settings.isAutoPauseEnabled,
            speedThresholdMps = settings.autoPauseSpeedMps,
            delayS = settings.autoPauseDelayS,
            idlePromptMin = settings.idlePromptMin,
        ),
    )
    private val reminders = RideReminders(
        mapOf(ReminderType.DRINK to settings.drinkIntervalMin, ReminderType.EAT to settings.eatIntervalMin),
    )
    private val battery = BatteryModePolicy(settings.isBatterySaverEnabled)
    private val pendingEvents = mutableListOf<RideEvent>()
    private var pendingBatteryChange: BatteryModeChange? = null
    private var status = RideStatus.IDLE
    private var startedAtMs: Long? = null
    private var lastTickMs: Long? = null
    private var lastSampleSecond = Long.MIN_VALUE
    private var latestAltitudeM: Double? = null
    private var latestAltitudeTimeMs: Long? = null
    private var isIdlePromptDismissed = false

    private var pausedSinceMs: Long? = null
    private var phoneBatteryPercent: Int? = null
    private val idlePromptMs: Long = settings.idlePromptMin * Units.MS_PER_MINUTE
    private val resumeSpeedMps: Double = settings.autoPauseSpeedMps + AutoPauseConfig.RESUME_HYSTERESIS_MPS

    public val currentStatus: RideStatus get() = status

    /**
     * GPS interval to request now (BAT-03): 1 Hz while riding; 5 s after a manual pause > 10 min or
     * while auto-paused past the idle prompt time. Raised again on resume.
     */
    public fun desiredGpsIntervalMs(nowMs: Long): Long {
        val pausedForMs = pausedSinceMs?.let { nowMs - it } ?: 0L
        val isLongPause = when (status) {
            RideStatus.PAUSED -> pausedForMs >= GpsConfig.LOW_POWER_AFTER_PAUSE_MS
            RideStatus.AUTO_PAUSED -> pausedForMs >= idlePromptMs
            RideStatus.IDLE, RideStatus.RECORDING, RideStatus.FINISHED -> false
        }
        return if (isLongPause) GpsConfig.LOW_POWER_INTERVAL_MS else GpsConfig.INTERVAL_MS
    }

    /**
     * Continues a ride interrupted by a crash/kill from its checkpoint, as [resumeAs] (03-ARCH §4.5). The
     * running lap starts where the last saved lap ended ([RecoveredTotals.lapStart]), not at the recovery.
     */
    public fun restore(startedAtMs: Long, totals: RecoveredTotals, resumeAs: RideStatus, nowMs: Long) {
        this.startedAtMs = startedAtMs
        metrics.restore(totals)
        elevation.restore(totals.ascentM, totals.descentM)
        reminders.restore(totals.movingMs)
        laps.start(totals.lapStart ?: progress(nowMs), startIndex = totals.lapIndex)
        status = resumeAs
        lastTickMs = nowMs
        pendingEvents += RideEvent(nowMs, RideEventType.GAP)
    }

    /** After [restore]: one stored second, so sensor totals and the running lap include it. */
    public fun replay(sample: RideSample, lapStartMs: Long) {
        sensors.restoreSecond(sample)
        if (sample.timeMs >= lapStartMs) laps.replaySecond(sample)
    }

    /** New GPS fix; returns why it was dropped, if it was (ERR-04). */
    public fun onLocation(fix: LocationFix): GpsRejection? =
        metrics.onFix(fix, countingMode(fix.speedMps ?: metrics.currentSpeedMps(fix.timeMs)))

    /** New sensor data from the BLE layer; connection changes are logged as events (T2.9). */
    public fun onSensor(reading: SensorReading) {
        sensors.onReading(reading)
        if (reading is SensorReading.Connection && startedAtMs != null) {
            val type = if (reading.isConnected) RideEventType.SENSOR_BACK else RideEventType.SENSOR_LOST
            pendingEvents += RideEvent(reading.receivedAtMs, type)
        }
    }

    /** New barometric altitude (already converted from pressure). */
    public fun onAltitude(altitudeM: Double, timeMs: Long) {
        latestAltitudeM = altitudeM
        latestAltitudeTimeMs = timeMs
    }

    /** New battery reading (≤ every 30 s). The change is reported with the next tick. */
    public fun onBattery(batteryStatus: BatteryStatus, nowMs: Long) {
        phoneBatteryPercent = batteryStatus.percent
        battery.update(batteryStatus)?.let { change ->
            pendingBatteryChange = change
            if (startedAtMs != null) change.event?.let { pendingEvents += RideEvent(nowMs, it) }
        }
    }

    /** Applies a user action. Invalid actions (e.g. finish while recording) are rejected, not thrown. */
    public fun handle(action: RideAction, nowMs: Long): Outcome<RecorderOutput, InvalidRideCommand> {
        val closed = mutableListOf<Lap>()
        val command = STATUS_COMMANDS[action]
        val result: Outcome<Unit, InvalidRideCommand> = if (command != null) {
            transition(command, nowMs, closed)
        } else {
            Outcome.Success(handleInRideAction(action, nowMs, closed))
        }
        return result.map { RecorderOutput(live(nowMs), drainEvents(), closed.toList()) }
    }

    /** Actions that do not change the ride status. */
    private fun handleInRideAction(action: RideAction, nowMs: Long, closed: MutableList<Lap>) {
        when (action) {
            RideAction.LAP -> laps.manualLap(progress(nowMs))?.let {
                closed += it
                pendingEvents += RideEvent(nowMs, RideEventType.LAP)
            }
            RideAction.ACK_REMINDER ->
                if (reminders.acknowledge(metrics.movingMs)) {
                    pendingEvents +=
                        RideEvent(nowMs, RideEventType.REMINDER_ACK)
                }
            RideAction.SNOOZE_REMINDER -> reminders.snooze(metrics.movingMs)
            RideAction.KEEP_RIDING -> isIdlePromptDismissed = true
            RideAction.START, RideAction.PAUSE, RideAction.RESUME, RideAction.FINISH, RideAction.SAVE,
            RideAction.DISCARD,
            -> Unit
        }
    }

    /** One pipeline step; call once per second while a ride exists. */
    public fun tick(nowMs: Long): RecorderOutput {
        val closed = mutableListOf<Lap>()
        val interval = lastTickMs?.let { nowMs - it } ?: 0L
        sensorSnapshot = sensors.snapshot(nowMs)
        metrics.updateWheel(sensorSnapshot.wheelSpeedMps)
        val mode = countingMode(metrics.currentSpeedMps(nowMs))
        if (status == RideStatus.AUTO_PAUSED && mode == CountingMode.NONE) metrics.discardTentative()
        val isGap = metrics.onTickInterval(interval, mode)
        if (isGap && RideStateMachine.isActive(status)) pendingEvents += RideEvent(nowMs, RideEventType.GAP)
        lastTickMs = nowMs
        evaluateAutoPause(nowMs)
        updateGuidance(nowMs)
        val elevationSnapshot = updateElevation(nowMs)
        estimatedPowerW = null
        if (status == RideStatus.RECORDING) {
            estimatedPowerW =
                sensors.onMovingSecond(sensorSnapshot, metrics.currentSpeedMps(nowMs), elevationSnapshot.gradePercent)
            laps.onSensorSecond(
                sensorSnapshot.heartRateBpm,
                sensorSnapshot.cadenceRpm,
                sensorSnapshot.powerW,
                sensorSnapshot.hasPowerMeter,
            )
            laps.onTick(progress(nowMs), metrics.currentSpeedMps(nowMs))?.let {
                closed += it
                pendingEvents += RideEvent(nowMs, RideEventType.LAP)
            }
        }
        if (RideStateMachine.isActive(status)) {
            reminders.onTick(nowMs, metrics.movingMs)?.let { pendingEvents += RideEvent(nowMs, it) }
        }
        val sample = buildSample(nowMs, elevationSnapshot)
        val batteryChange = pendingBatteryChange.also { pendingBatteryChange = null }
        return RecorderOutput(live(nowMs), drainEvents(), closed.toList(), sample, batteryChange)
    }

    /** Route following and live segments, from the fresh fix while riding. */
    private fun updateGuidance(nowMs: Long) {
        val isRiding = status == RideStatus.RECORDING || status == RideStatus.AUTO_PAUSED
        val freshFix = metrics.latestFix?.takeIf { metrics.isFixFresh(nowMs) }
        val speed = metrics.currentSpeedMps(nowMs) ?: 0.0
        route.onSecond(isRiding, freshFix, nowMs, speed, metrics.averageSpeedMps())?.let {
            pendingEvents += RideEvent(nowMs, it)
        }
        segments.onSecond(isRiding, freshFix?.latDeg, freshFix?.lonDeg, nowMs)
    }

    /** Totals for the activity checkpoint and the saved ride. */
    public fun totals(nowMs: Long): RideTotals {
        val elevationSnapshot = elevation.snapshot()
        return sensors.totals(
            RideTotals(
                elapsedS = elapsedMs(nowMs) / Units.MS_PER_SECOND,
                movingS = metrics.movingMs / Units.MS_PER_SECOND,
                distanceM = metrics.distanceM,
                ascentM = elevationSnapshot.ascentM,
                descentM = elevationSnapshot.descentM,
                avgSpeedMps = metrics.averageSpeedMps(),
                maxSpeedMps = metrics.maxSpeedMps,
            ),
        )
    }

    private fun transition(
        command: RideCommand,
        nowMs: Long,
        closed: MutableList<Lap>,
    ): Outcome<Unit, InvalidRideCommand> = RideStateMachine.apply(status, command).map { transition ->
        if (command == RideCommand.START) {
            startedAtMs = nowMs
            lastTickMs = nowMs
            laps.start(progress(nowMs))
        }
        if (command == RideCommand.FINISH) laps.finish(progress(nowMs))?.let(closed::add)
        if (command == RideCommand.PAUSE || command == RideCommand.RESUME) autoPause.reset()
        if (command == RideCommand.RESUME) metrics.resetGpsHistory()
        val wasPaused = status == RideStatus.PAUSED || status == RideStatus.AUTO_PAUSED
        status = transition.status
        val isPaused = status == RideStatus.PAUSED || status == RideStatus.AUTO_PAUSED
        pausedSinceMs = when {
            !isPaused -> null
            wasPaused -> pausedSinceMs
            else -> nowMs
        }
        transition.event?.let { pendingEvents += RideEvent(nowMs, it) }
    }

    private fun evaluateAutoPause(nowMs: Long) {
        val fix = metrics.latestFix?.takeIf { metrics.isFixFresh(nowMs) }
        val motion = MotionSample(
            timeMs = nowMs,
            speedMps = metrics.currentSpeedMps(nowMs),
            latDeg = fix?.latDeg,
            lonDeg = fix?.lonDeg,
            accuracyM = fix?.accuracyM,
            isFromWheelSensor = metrics.isWheelSpeed,
        )
        val command = when (status) {
            RideStatus.RECORDING ->
                RideCommand.AUTO_PAUSE.takeIf { autoPause.onRecording(motion) == AutoPauseDecision.PAUSE }
            RideStatus.AUTO_PAUSED ->
                RideCommand.AUTO_RESUME.takeIf { autoPause.onAutoPaused(motion) == AutoPauseDecision.RESUME }
            RideStatus.IDLE, RideStatus.PAUSED, RideStatus.FINISHED -> null
        }
        when (command) {
            RideCommand.AUTO_PAUSE -> {
                // The rider stopped when the slow period began: the pause starts there, and the delay
                // seconds already counted as moving time are removed (L5: moving time must not grow).
                val stoppedAtMs = autoPause.lastStopStartedAtMs ?: nowMs
                metrics.removeMovingMs(nowMs - stoppedAtMs)
                isIdlePromptDismissed = false
                transition(command, stoppedAtMs, mutableListOf())
            }
            RideCommand.AUTO_RESUME -> {
                metrics.commitTentative()
                transition(command, nowMs, mutableListOf())
            }
            else -> Unit
        }
    }

    /** How the current movement counts: see [CountingMode]. */
    private fun countingMode(speedMps: Double?): CountingMode = when (status) {
        RideStatus.RECORDING -> CountingMode.COUNT
        RideStatus.AUTO_PAUSED ->
            if (speedMps != null && speedMps > resumeSpeedMps) CountingMode.TENTATIVE else CountingMode.NONE
        RideStatus.IDLE, RideStatus.PAUSED, RideStatus.FINISHED -> CountingMode.NONE
    }

    private fun updateElevation(nowMs: Long): ElevationSnapshot {
        val fix = metrics.latestFix?.takeIf { metrics.isFixFresh(nowMs) }
        val barometric = latestAltitudeM?.takeIf { isFresh(latestAltitudeTimeMs, nowMs) }
        return elevation.add(
            // One source per ride (D-148): a GPS altitude in place of a late barometer reading would carry the
            // barometer's calibration offset and count as a climb each time the source switched.
            rawAltitudeM = if (altitudeSource == AltitudeSource.BAROMETER) barometric else fix?.altitudeM,
            gpsAltitudeM = fix?.altitudeM,
            distanceM = metrics.distanceM,
            isMoving = status == RideStatus.RECORDING,
        )
    }

    /** SampleMerger: one record per whole second, only while the timer runs (03-ARCHITECTURE §4.3). */
    private fun buildSample(nowMs: Long, elevationSnapshot: ElevationSnapshot): RideSample? {
        val second = nowMs / Units.MS_PER_SECOND
        if (status != RideStatus.RECORDING || second <= lastSampleSecond) return null
        lastSampleSecond = second
        val fix = metrics.latestFix?.takeIf { metrics.isFixFresh(nowMs) }
        return RideSample(
            timeMs = second * Units.MS_PER_SECOND,
            latDeg = fix?.latDeg,
            lonDeg = fix?.lonDeg,
            altitudeM = elevationSnapshot.altitudeM,
            gpsAccuracyM = fix?.accuracyM,
            distanceM = metrics.distanceM,
            speedMps = metrics.currentSpeedMps(nowMs),
            heartRateBpm = sensorSnapshot.heartRateBpm,
            cadenceRpm = sensorSnapshot.cadenceRpm,
            powerW = sensorSnapshot.powerW,
            estPowerW = estimatedPowerW,
        )
    }

    private fun live(nowMs: Long): LiveRide {
        val fix = metrics.latestFix?.takeIf { metrics.isFixFresh(nowMs) }
        return LiveRide(
            status = status,
            nowMs = nowMs,
            startedAtMs = startedAtMs,
            elapsedMs = elapsedMs(nowMs),
            movingMs = metrics.movingMs,
            distanceM = metrics.distanceM,
            speedMps = metrics.currentSpeedMps(nowMs),
            avgSpeedMps = metrics.averageSpeedMps(),
            maxSpeedMps = metrics.maxSpeedMps,
            elevation = elevation.snapshot(),
            lap = laps.status(progress(nowMs)),
            hasGpsFix = fix != null,
            gpsAccuracyM = fix?.accuracyM,
            batteryMode = battery.mode,
            phoneBatteryPercent = phoneBatteryPercent,
            pausedSinceMs = pausedSinceMs,
            reminder = reminders.active,
            isIdlePromptVisible = status == RideStatus.AUTO_PAUSED &&
                !isIdlePromptDismissed &&
                autoPause.isIdlePromptDue(nowMs),
            sensors = sensors.live(sensorSnapshot),
            isGpsEnabled = context.isGpsEnabled,
            profileId = context.profileId,
            route = route.progress,
            segment = segments.state,
            routeId = context.routeId,
            latDeg = fix?.latDeg,
            lonDeg = fix?.lonDeg,
        )
    }

    private fun progress(nowMs: Long) =
        RideProgress(nowMs, metrics.distanceM, metrics.movingMs, elevation.snapshot().ascentM)

    private fun elapsedMs(nowMs: Long): Long = startedAtMs?.let { nowMs - it } ?: 0L

    private fun isFresh(timeMs: Long?, nowMs: Long): Boolean =
        timeMs != null && nowMs - timeMs <= RecordingConfig.SAMPLE_MAX_AGE_MS

    private fun drainEvents(): List<RideEvent> = pendingEvents.toList().also { pendingEvents.clear() }

    private companion object {
        /** User actions that are ride status commands. */
        val STATUS_COMMANDS: Map<RideAction, RideCommand> = mapOf(
            RideAction.START to RideCommand.START,
            RideAction.PAUSE to RideCommand.PAUSE,
            RideAction.RESUME to RideCommand.RESUME,
            RideAction.FINISH to RideCommand.FINISH,
            RideAction.SAVE to RideCommand.SAVE,
            RideAction.DISCARD to RideCommand.DISCARD,
        )
    }
}
