package com.ridecomp.core.usecase

import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.engine.export.ByteSink
import com.ridecomp.core.engine.export.FitActivityWriter
import com.ridecomp.core.engine.export.FitSport
import com.ridecomp.core.engine.export.GpxWriter
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.repository.ActivityProfileRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.source.Clock
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import com.ridecomp.core.util.runCatchingCancellable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Files in the app's private export folder (SEC-STO-01); shared later through a FileProvider. */
public interface ExportFileStore {
    /** Writes [bytes] as [fileName] and returns the absolute path. */
    public suspend fun write(fileName: String, bytes: ByteArray): String

    /** Streams into [fileName] and returns the absolute path. */
    public suspend fun stream(fileName: String, block: suspend (ByteSink) -> Unit): String

    public suspend fun delete(fileName: String)
}

/** Hands an exported file to other apps or to a user-chosen location (SEC-PLT-04, SEC-STO-01). */
public interface ExportedFileHandler {
    /** Opens the Android share sheet (Strava, Drive, WhatsApp…) through a FileProvider. */
    public suspend fun share(file: ExportedFile)

    /** Copies the file to a location picked with the Storage Access Framework ("Save to Downloads"). */
    public suspend fun copyTo(file: ExportedFile, destinationUri: String): Boolean
}

public enum class ExportFormat(public val extension: String, public val mimeType: String) {
    FIT("fit", "application/octet-stream"),
    GPX("gpx", "application/gpx+xml"),
}

/** An exported file ready to be shared or saved. */
public data class ExportedFile(val path: String, val fileName: String, val format: ExportFormat)

public enum class ExportError { NOT_FOUND, WRITE_FAILED }

/** File names in one place: `RideComp_YYYYMMDD_HHMM.fit` from the local start time (04-INTEGRATIONS §B.4). */
public object ExportFileNames {
    private const val PREFIX = "RideComp_"
    private const val EMERGENCY_PREFIX = "RideComp_emergency_"
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    public fun of(activity: Activity, format: ExportFormat, zone: ZoneId): String =
        PREFIX + formatter.format(Instant.ofEpochMilli(activity.startedAtMs).atZone(zone)) + "." + format.extension

    /** Temporary FIT written at ≤ 5 % battery, deleted once the ride is exported normally. */
    public fun emergency(activity: Activity): String =
        EMERGENCY_PREFIX + activity.uuid + "." + ExportFormat.FIT.extension
}

/** Reads all samples of a ride in bounded chunks (DATA-04) and hands them over one by one. */
internal suspend fun ActivityRepository.forEachSample(activityId: Long, action: (RideSample) -> Unit) {
    var after = Long.MIN_VALUE
    while (true) {
        val chunk = getSamples(activityId, after, ActivityConfig.TRACK_CHUNK_SIZE)
        if (chunk.isEmpty()) break
        chunk.forEach(action)
        after = chunk.last().timeMs
    }
}

/**
 * Builds the FIT file of a ride (T1.14, 04-INTEGRATIONS §B) and stores it in the export folder.
 * [isEmergency] writes the temporary battery-emergency copy (12-FEATURE-SPECS §3) without marking the
 * ride as exported.
 */
public class ExportFitUseCase(
    private val repository: ActivityRepository,
    private val store: ExportFileStore,
    private val clock: Clock,
    private val zone: ZoneId,
    private val profiles: ActivityProfileRepository,
) {
    public suspend operator fun invoke(
        activityId: Long,
        isEmergency: Boolean = false,
    ): Outcome<ExportedFile, ExportError> {
        val activity = repository.get(activityId) ?: return ExportError.NOT_FOUND.asFailure()
        val fileName = if (isEmergency) {
            ExportFileNames.emergency(
                activity,
            )
        } else {
            ExportFileNames.of(activity, ExportFormat.FIT, zone)
        }
        val result = runCatchingCancellable {
            val bytes = buildFit(activity)
            store.write(fileName, bytes).also { path ->
                if (!isEmergency) {
                    repository.markFitExported(activityId, path, clock.nowMs())
                    store.delete(ExportFileNames.emergency(activity))
                }
            }
        }
        return result.fold(
            onSuccess = { ExportedFile(it, fileName, ExportFormat.FIT).asSuccess() },
            onFailure = { ExportError.WRITE_FAILED.asFailure() },
        )
    }

    private suspend fun buildFit(activity: Activity): ByteArray {
        val profile = activity.profileId?.let { profiles.get(it) }
        val sport = profile?.let { FitSport(it.fitSport, it.fitSubSport) } ?: FitSport()
        val writer = FitActivityWriter(activity.startedAtMs, activity.uuid.hashCode().toLong() and UINT32_MASK, sport)
        val timerEvents = repository.getEvents(activity.id).mapNotNull { event ->
            timerStateOf(event.type)?.let { isStart -> event.timeMs to isStart }
        }.sortedBy { it.first }
        var nextEvent = 0
        var lastTimeMs = activity.startedAtMs
        var isTimerRunning = false
        repository.forEachSample(activity.id) { sample ->
            while (nextEvent < timerEvents.size && timerEvents[nextEvent].first <= sample.timeMs) {
                val (time, isStart) = timerEvents[nextEvent++]
                writer.addTimerEvent(time, isStart)
                isTimerRunning = isStart
            }
            writer.addRecord(sample)
            lastTimeMs = sample.timeMs
        }
        timerEvents.drop(nextEvent).forEach { (time, isStart) ->
            writer.addTimerEvent(time, isStart)
            isTimerRunning = isStart
            lastTimeMs = maxOf(lastTimeMs, time)
        }
        val endTimeMs = activity.endedAtMs ?: lastTimeMs
        // Every activity ends with the timer stopped (unfinished/emergency rides have no STOP event yet).
        if (isTimerRunning) writer.addTimerEvent(endTimeMs, isStart = false)
        repository.getLaps(activity.id).forEach(writer::addLap)
        val utcOffsetS = zone.rules.getOffset(Instant.ofEpochMilli(endTimeMs)).totalSeconds.toLong()
        return writer.finish(activity.totals, endTimeMs, utcOffsetS)
    }

    private fun timerStateOf(type: RideEventType): Boolean? = when (type) {
        RideEventType.START, RideEventType.RESUME, RideEventType.AUTO_RESUME -> true
        RideEventType.PAUSE, RideEventType.AUTO_PAUSE, RideEventType.STOP -> false
        RideEventType.GAP, RideEventType.LAP, RideEventType.SENSOR_LOST, RideEventType.SENSOR_BACK,
        RideEventType.REMINDER_DRINK, RideEventType.REMINDER_EAT, RideEventType.REMINDER_ACK,
        RideEventType.INCIDENT_DETECTED, RideEventType.INCIDENT_CANCELLED, RideEventType.SOS_SENT,
        RideEventType.SMS_FAILED, RideEventType.OFF_ROUTE, RideEventType.BACK_ON_ROUTE,
        RideEventType.BATTERY_MODE_SAVER, RideEventType.BATTERY_MODE_CRITICAL, RideEventType.BATTERY_MODE_EMERGENCY,
        -> null
    }

    private companion object {
        const val UINT32_MASK = 0xFFFF_FFFFL
    }
}

/** Streams the GPX file of a ride (04-INTEGRATIONS §C.2). */
public class ExportGpxUseCase(
    private val repository: ActivityRepository,
    private val store: ExportFileStore,
    private val zone: ZoneId,
) {
    public suspend operator fun invoke(activityId: Long): Outcome<ExportedFile, ExportError> {
        val activity = repository.get(activityId) ?: return ExportError.NOT_FOUND.asFailure()
        val fileName = ExportFileNames.of(activity, ExportFormat.GPX, zone)
        val result = runCatchingCancellable {
            // Bounded: one DB chunk at a time is read and streamed to the file (MEM-05).
            store.stream(fileName) { sink ->
                val writer = GpxWriter(sink)
                writer.begin(activity.name, activity.startedAtMs)
                repository.forEachSample(activity.id, writer::addPoint)
                writer.end()
            }
        }
        return result.fold(
            onSuccess = { ExportedFile(it, fileName, ExportFormat.GPX).asSuccess() },
            onFailure = { ExportError.WRITE_FAILED.asFailure() },
        )
    }
}
