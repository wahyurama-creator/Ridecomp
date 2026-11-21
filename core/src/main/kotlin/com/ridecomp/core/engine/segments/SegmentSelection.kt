package com.ridecomp.core.engine.segments

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.route.RouteBuilder

/** What the segment editor shows for the chosen part of a ride (T11.15). */
public data class SegmentSelectionStats(
    val lengthM: Double,
    /** Climb with the same hysteresis as a saved segment; null without altitude. */
    val gainM: Double?,
    /** Net climb over the length, like a saved segment; null without altitude. */
    val avgGradePercent: Double?,
    /** Time this ride took over the part (elapsed, like segment efforts). */
    val elapsedS: Double,
    /** Lowest and highest altitude along the part; null without altitude. */
    val altitudeRangeM: ClosedFloatingPointRange<Double>? = null,
) {
    val avgSpeedMps: Double? get() = if (elapsedS > 0) lengthM / elapsedS else null
}

/**
 * A ride's full-resolution distance, time and altitude, kept by the segment editor so the chosen part's
 * figures are exact at any length (the drawn outline is downsampled). Read-only after it is built.
 */
public class TrackProfile(
    private val distancesM: DoubleArray,
    private val timesMs: LongArray,
    /** NaN where a sample has no altitude. */
    private val altitudesM: DoubleArray,
) {
    init {
        require(distancesM.size == timesMs.size && distancesM.size == altitudesM.size) { "Mismatched profile" }
    }

    public val totalM: Double get() = distancesM.lastOrNull() ?: 0.0

    /** Figures between [startM] and [endM] along the ride; times are interpolated at both ends. */
    public fun stats(startM: Double, endM: Double): SegmentSelectionStats {
        val lengthM = (endM - startM).coerceAtLeast(0.0)
        if (distancesM.size < 2 || lengthM <= 0.0) return SegmentSelectionStats(lengthM, null, null, 0.0)
        val first = indexAtOrAfter(startM)
        val last = indexAtOrBefore(endM)
        val elevations = (first..last).map { altitudesM[it] }.filterNot(Double::isNaN)
        val hasAltitude = elevations.size >= 2
        val netM = if (hasAltitude) elevations.last() - elevations.first() else 0.0
        val elapsedS = (timeAt(endM) - timeAt(startM)) / Units.MS_PER_SECOND
        return SegmentSelectionStats(
            lengthM = lengthM,
            gainM = if (hasAltitude) RouteBuilder.ascent(elevations) else null,
            avgGradePercent = if (hasAltitude) netM / lengthM * Units.PERCENT else null,
            elapsedS = elapsedS.coerceAtLeast(0.0),
            altitudeRangeM = if (hasAltitude) elevations.min()..elevations.max() else null,
        )
    }

    private fun timeAt(distanceM: Double): Double {
        val after = indexAtOrAfter(distanceM)
        if (after == 0) return timesMs.first().toDouble()
        val before = after - 1
        val span = distancesM[after] - distancesM[before]
        val fraction = if (span > 0) ((distanceM - distancesM[before]) / span).coerceIn(0.0, 1.0) else 0.0
        return timesMs[before] + fraction * (timesMs[after] - timesMs[before])
    }

    /** First sample at or past [distanceM] (the last one when the ride is shorter). */
    private fun indexAtOrAfter(distanceM: Double): Int {
        var low = 0
        var high = distancesM.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (distancesM[middle] < distanceM) low = middle + 1 else high = middle
        }
        return low
    }

    private fun indexAtOrBefore(distanceM: Double): Int {
        val index = indexAtOrAfter(distanceM)
        return if (distancesM[index] > distanceM && index > 0) index - 1 else index
    }
}
