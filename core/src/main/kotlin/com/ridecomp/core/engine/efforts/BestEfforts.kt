package com.ridecomp.core.engine.efforts

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.RideTotals

/** Best effort limits (12-FEATURE-SPECS §8). */
public object BestEffortConfig {
    /**
     * Samples are written once a second while the timer runs (03-ARCHITECTURE §4.3), so a longer gap is a
     * pause: it counts as one sample interval, never as moving time.
     */
    public const val MAX_CONTINUOUS_GAP_S: Double = 5.0

    /** One sample interval (1 Hz recording). */
    public const val SAMPLE_INTERVAL_S: Double = 1.0

    /** "Highest average speed" only counts rides at least this long. */
    public const val MIN_AVG_SPEED_DISTANCE_M: Double = 20_000.0
}

/** Group of a record (05-DATA-MODEL `best_efforts.type`). Stable strings: stored on users' phones. */
public enum class BestEffortType(public val storageValue: String) {
    DISTANCE("distance"),
    SPEED("speed"),
    POWER("power"),
    OTHER("other"),
}

/**
 * Every record RideComp keeps (12-FEATURE-SPECS §8). [storageKey] is stored: never rename. [target] is the
 * distance in meters or the duration in seconds of the window; 0 for whole-ride records.
 */
public enum class BestEffortKey(
    public val type: BestEffortType,
    public val storageKey: String,
    public val target: Double,
    /** False when a lower value wins (time over a distance). */
    public val isHigherBetter: Boolean,
) {
    FASTEST_1_KM(BestEffortType.DISTANCE, "distance_1000", 1_000.0, false),
    FASTEST_5_KM(BestEffortType.DISTANCE, "distance_5000", 5_000.0, false),
    FASTEST_10_KM(BestEffortType.DISTANCE, "distance_10000", 10_000.0, false),
    FASTEST_20_KM(BestEffortType.DISTANCE, "distance_20000", 20_000.0, false),
    FASTEST_40_KM(BestEffortType.DISTANCE, "distance_40000", 40_000.0, false),
    FASTEST_100_KM(BestEffortType.DISTANCE, "distance_100000", 100_000.0, false),
    SPEED_30_S(BestEffortType.SPEED, "speed_30", 30.0, true),
    SPEED_1_MIN(BestEffortType.SPEED, "speed_60", 60.0, true),
    SPEED_3_MIN(BestEffortType.SPEED, "speed_180", 180.0, true),
    SPEED_5_MIN(BestEffortType.SPEED, "speed_300", 300.0, true),
    SPEED_20_MIN(BestEffortType.SPEED, "speed_1200", 1_200.0, true),
    POWER_5_S(BestEffortType.POWER, "power_5", 5.0, true),
    POWER_30_S(BestEffortType.POWER, "power_30", 30.0, true),
    POWER_1_MIN(BestEffortType.POWER, "power_60", 60.0, true),
    POWER_3_MIN(BestEffortType.POWER, "power_180", 180.0, true),
    POWER_5_MIN(BestEffortType.POWER, "power_300", 300.0, true),
    POWER_20_MIN(BestEffortType.POWER, "power_1200", 1_200.0, true),
    POWER_60_MIN(BestEffortType.POWER, "power_3600", 3_600.0, true),
    LONGEST_RIDE(BestEffortType.OTHER, "longest_ride", 0.0, true),
    MOST_ASCENT(BestEffortType.OTHER, "most_ascent", 0.0, true),
    FASTEST_AVG_SPEED(BestEffortType.OTHER, "fastest_avg_speed", 0.0, true),
    ;

    /** True when [value] beats [best] (a tie is not a new record). */
    public fun isBetter(value: Double, best: Double?): Boolean = when {
        best == null -> true
        isHigherBetter -> value > best
        else -> value < best
    }

    public companion object {
        public fun fromStorage(key: String): BestEffortKey? = entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * One effort of a ride. [value] is seconds for distances, m/s for speeds over a duration and the average
 * speed, watts for power, meters for the longest ride and the most ascent. [startMs]/[endMs] mark the window
 * (null for whole rides).
 */
public data class BestEffort(val key: BestEffortKey, val value: Double, val startMs: Long?, val endMs: Long?)

/**
 * Best efforts of one ride (12-FEATURE-SPECS §8): sliding windows (two pointers) over cumulative distance and
 * **moving** time, so pauses never count. Runs after the ride is saved, on the default dispatcher.
 */
public object BestEffortCalculator {
    public fun calculate(track: RideTrack, totals: RideTotals): List<BestEffort> {
        val moving = movingSeconds(track)
        val keys = BestEffortKey.entries
        val distances = keys.filter { it.type == BestEffortType.DISTANCE }
            .mapNotNull { fastestDistance(track, moving, it) }
        val distanceSum = DoubleArray(track.size) { track.distanceM(it) - track.distanceM(0) }
        val speeds = keys.filter { it.type == BestEffortType.SPEED }
            .mapNotNull { bestAverage(track, moving, distanceSum, it) }
        val powers = if (track.hasPower) {
            val energy = energy(track, moving)
            keys.filter { it.type == BestEffortType.POWER }.mapNotNull { bestAverage(track, moving, energy, it) }
        } else {
            emptyList()
        }
        return distances + speeds + powers + wholeRide(totals)
    }

    /** Moving seconds at each sample: gaps longer than a pause threshold count as one interval. */
    private fun movingSeconds(track: RideTrack): DoubleArray {
        val moving = DoubleArray(track.size)
        for (i in 1 until track.size) {
            val gapS = (track.timeMs(i) - track.timeMs(i - 1)).toDouble() / Units.MS_PER_SECOND
            val step = if (gapS <= BestEffortConfig.MAX_CONTINUOUS_GAP_S) gapS else BestEffortConfig.SAMPLE_INTERVAL_S
            moving[i] = moving[i - 1] + step
        }
        return moving
    }

    /** Shortest moving time over [key]'s distance; the window start is interpolated between samples. */
    private fun fastestDistance(track: RideTrack, moving: DoubleArray, key: BestEffortKey): BestEffort? {
        val target = key.target
        var start = 0
        var best: BestEffort? = null
        for (end in 1 until track.size) {
            val endDistance = track.distanceM(end)
            if (endDistance - track.distanceM(0) < target) continue
            while (start + 1 < end && endDistance - track.distanceM(start + 1) >= target) start++
            val startDistance = endDistance - target
            val span = track.distanceM(start + 1) - track.distanceM(start)
            val fraction = if (span > 0) ((startDistance - track.distanceM(start)) / span).coerceIn(0.0, 1.0) else 0.0
            val startMoving = moving[start] + fraction * (moving[start + 1] - moving[start])
            val seconds = moving[end] - startMoving
            if (seconds > 0 && key.isBetter(seconds, best?.value)) {
                best = BestEffort(key, seconds, track.timeMs(start), track.timeMs(end))
            }
        }
        return best
    }

    /** Energy in joules up to each sample; missing power-meter samples count as 0 W. */
    private fun energy(track: RideTrack, moving: DoubleArray): DoubleArray {
        val energy = DoubleArray(track.size)
        for (i in 1 until track.size) {
            energy[i] = energy[i - 1] + track.powerW(i) * (moving[i] - moving[i - 1])
        }
        return energy
    }

    /**
     * Highest average of a quantity over [key]'s moving duration: [sum] holds the quantity accumulated up to
     * each sample (joules for watts, meters for m/s). Nothing when the ride is shorter or the best is 0.
     */
    private fun bestAverage(track: RideTrack, moving: DoubleArray, sum: DoubleArray, key: BestEffortKey): BestEffort? {
        var start = 0
        var best: BestEffort? = null
        for (end in 1 until track.size) {
            if (moving[end] < key.target) continue
            while (moving[end] - moving[start + 1] >= key.target) start++
            val seconds = moving[end] - moving[start]
            val average = (sum[end] - sum[start]) / seconds
            if (average > 0 && key.isBetter(average, best?.value)) {
                best = BestEffort(key, average, track.timeMs(start), track.timeMs(end))
            }
        }
        return best
    }

    /** Longest ride, most ascent and highest average speed (rides of at least 20 km). */
    private fun wholeRide(totals: RideTotals): List<BestEffort> = listOfNotNull(
        totals.distanceM.takeIf { it > 0 }?.let { BestEffort(BestEffortKey.LONGEST_RIDE, it, null, null) },
        totals.ascentM.takeIf { it > 0 }?.let { BestEffort(BestEffortKey.MOST_ASCENT, it, null, null) },
        totals.avgSpeedMps
            .takeIf { it > 0 && totals.distanceM >= BestEffortConfig.MIN_AVG_SPEED_DISTANCE_M }
            ?.let { BestEffort(BestEffortKey.FASTEST_AVG_SPEED, it, null, null) },
    )
}
