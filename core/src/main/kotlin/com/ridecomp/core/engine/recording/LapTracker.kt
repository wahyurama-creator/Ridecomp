package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.metrics.AverageMax
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.LapTrigger
import com.ridecomp.core.model.RideSample
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Cumulative ride values at one moment; laps are differences between two of these. */
public data class RideProgress(val timeMs: Long, val distanceM: Double, val movingMs: Long, val ascentM: Double)

/** The running lap, for the lap data page (02-DESIGN §3.4 page 4). */
public data class LapStatus(
    /** 1-based number shown to the rider. */
    val number: Int,
    val elapsedMs: Long,
    val distanceM: Double,
    val movingMs: Long,
    val avgSpeedMps: Double,
    val avgHeartRateBpm: Int? = null,
    val avgPowerW: Int? = null,
    /** Duration and distance of the lap before this one (13-UI-SCREENS #25), null for the first lap. */
    val previousLapMs: Long? = null,
    val previousLapDistanceM: Double? = null,
    /** Auto-lap distance, null for manual laps only. */
    val autoLapM: Double? = null,
) {
    /**
     * Time this lap will take at the current pace, for distance auto-laps only (13-UI-SCREENS #25);
     * null until the lap has covered [LapConfig.MIN_PROJECTION_DISTANCE_M].
     */
    val projectedLapMs: Long?
        get() {
            val lapM = autoLapM ?: return null
            if (distanceM < LapConfig.MIN_PROJECTION_DISTANCE_M) return null
            return (elapsedMs * lapM / distanceM).roundToLong()
        }

    /** Projection minus the previous lap: negative = faster. Only when the previous lap was a full auto-lap. */
    val projectedDeltaMs: Long?
        get() {
            val projected = projectedLapMs ?: return null
            val previous = previousLapMs ?: return null
            val isComparable = previousLapDistanceM?.let {
                autoLapM != null && it >= autoLapM * LapConfig.FULL_LAP_RATIO
            }
            return if (isComparable == true) projected - previous else null
        }
}

/** Lap projection limits (13-UI-SCREENS #25). */
public object LapConfig {
    /** Too early, the pace is noise. */
    public const val MIN_PROJECTION_DISTANCE_M: Double = 200.0

    /** A previous lap this close to the auto-lap distance counts as a full lap. */
    public const val FULL_LAP_RATIO: Double = 0.99
}

/**
 * Manual laps and auto laps every [autoLapM] meters (01-PRD F-12). Auto laps are cut exactly at each
 * multiple of [autoLapM]; the overshoot belongs to the next lap. Feed [onTick] once per second while
 * recording.
 */
public class LapTracker(private val autoLapM: Double? = null) {
    private var index = 0
    private var start: RideProgress? = null
    private var maxSpeedMps = 0.0
    private var heartRate = AverageMax()
    private var cadence = AverageMax()
    private var powerSum = 0.0
    private var powerSeconds = 0L
    private var maxPowerW: Int? = null
    private var lastLap: Lap? = null

    /** Adds one moving second of sensor data to the running lap (power counts coasting zeros). */
    public fun onSensorSecond(heartRateBpm: Int?, cadenceRpm: Int?, powerW: Int?, hasPowerMeter: Boolean) {
        heartRate.add(heartRateBpm)
        cadence.add(cadenceRpm)
        if (hasPowerMeter) {
            powerSum += powerW ?: 0
            powerSeconds++
            powerW?.let { maxPowerW = maxOf(maxPowerW ?: 0, it) }
        }
    }

    /** A stored second replayed after a crash/kill (a stored power value means a power meter). */
    public fun replaySecond(sample: RideSample) {
        onSensorSecond(sample.heartRateBpm, sample.cadenceRpm, sample.powerW, hasPowerMeter = sample.powerW != null)
    }

    /** Starts the first lap (or resumes after recovery with the given lap [startIndex]). */
    public fun start(at: RideProgress, startIndex: Int = 0) {
        index = startIndex
        start = at
        maxSpeedMps = 0.0
    }

    /** Updates the running lap; returns a closed auto lap when its distance was reached. */
    public fun onTick(now: RideProgress, speedMps: Double?): Lap? {
        val begin = start
        if (begin != null && speedMps != null && speedMps > maxSpeedMps) maxSpeedMps = speedMps
        val reachedLapM = autoLapM?.takeIf { begin != null && now.distanceM - begin.distanceM >= it }
        // Cut exactly at the lap distance; the remainder starts the next lap.
        return if (begin != null && reachedLapM != null) {
            close(now.copy(distanceM = begin.distanceM + reachedLapM), LapTrigger.DISTANCE)
        } else {
            null
        }
    }

    /** Closes the running lap on the LAP button. */
    public fun manualLap(now: RideProgress): Lap? = start?.let { close(now, LapTrigger.MANUAL) }

    /** Closes the final lap when the ride finishes; an empty final lap is dropped. */
    public fun finish(now: RideProgress): Lap? {
        val begin = start ?: return null
        val isEmpty = now.distanceM <= begin.distanceM && now.movingMs <= begin.movingMs
        return if (isEmpty) null.also { start = null } else close(now, LapTrigger.MANUAL).also { start = null }
    }

    /** Progress of the running lap, or null before the ride starts. */
    public fun status(now: RideProgress): LapStatus? = start?.let { begin ->
        val distance = now.distanceM - begin.distanceM
        val moving = now.movingMs - begin.movingMs
        LapStatus(
            number = index + 1,
            elapsedMs = now.timeMs - begin.timeMs,
            distanceM = distance,
            movingMs = moving,
            avgSpeedMps = averageSpeedMps(distance, moving),
            avgHeartRateBpm = heartRate.average(),
            avgPowerW = if (powerSeconds > 0) (powerSum / powerSeconds).roundToInt() else null,
            previousLapMs = lastLap?.let { it.endTimeMs - it.startTimeMs },
            previousLapDistanceM = lastLap?.distanceM,
            autoLapM = autoLapM,
        )
    }

    private fun close(end: RideProgress, trigger: LapTrigger): Lap {
        val begin = requireNotNull(start) { "lap not started" }
        val distance = end.distanceM - begin.distanceM
        val movingMs = end.movingMs - begin.movingMs
        val lap = Lap(
            index = index,
            startTimeMs = begin.timeMs,
            endTimeMs = end.timeMs,
            distanceM = distance,
            movingS = movingMs / Units.MS_PER_SECOND,
            avgSpeedMps = averageSpeedMps(distance, movingMs),
            maxSpeedMps = maxSpeedMps,
            ascentM = end.ascentM - begin.ascentM,
            trigger = trigger,
            avgHeartRateBpm = heartRate.average(),
            maxHeartRateBpm = heartRate.max,
            avgCadenceRpm = cadence.average(),
            avgPowerW = if (powerSeconds > 0) (powerSum / powerSeconds).roundToInt() else null,
            maxPowerW = maxPowerW,
        )
        index++
        start = end
        lastLap = lap
        resetLapAggregates()
        return lap
    }

    private fun resetLapAggregates() {
        maxSpeedMps = 0.0
        heartRate = AverageMax()
        cadence = AverageMax()
        powerSum = 0.0
        powerSeconds = 0
        maxPowerW = null
    }

    private fun averageSpeedMps(distanceM: Double, movingMs: Long): Double =
        if (movingMs > 0) distanceM / (movingMs.toDouble() / Units.MS_PER_SECOND) else 0.0
}
