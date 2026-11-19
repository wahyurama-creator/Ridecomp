package com.ridecomp.ui.component

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** How the vertical axis of a [RideChart] is fitted to its data (T11.1). */
enum class ChartYScale {
    /** From zero up to the data: speed, heart rate, power. */
    FROM_ZERO,

    /** Around the data only, so the shape shows: elevation. */
    DATA_RANGE,
}

/** A vertical axis with round tick values: [min] and [max] are multiples of [step]. */
data class ChartAxis(val min: Double, val max: Double, val step: Double) {
    val range: Double get() = max - min

    /** The tick values from [min] to [max] inclusive. */
    val ticks: List<Double> get() = (0..((range / step) + EPSILON).toInt()).map {
        ChartScale.round(min + it * step, step)
    }

    /** Decimals needed to print the ticks (0 for 5, 10, 20; 1 for 0.5 …). */
    val decimals: Int get() = ChartScale.decimalsFor(step)
}

/**
 * Round numbers for chart axes (T11.1, 14-PHASE11-PLAN): steps of 1, 2 or 5 × 10ⁿ, as on printed charts, so
 * the labels read 15 · 30 · 45 rather than 23,7 · 31,2. Pure math, unit tested.
 */
object ChartScale {
    /** About this many horizontal lines on the vertical axis. */
    const val Y_TICKS: Int = 4

    /** About this many distance labels along the bottom. */
    const val X_TICKS: Int = 5

    /** The 1-2-5 step closest to splitting [range] into [count] parts. */
    fun niceStep(range: Double, count: Int): Double {
        val raw = abs(range) / count.coerceAtLeast(1)
        if (raw <= 0.0 || !raw.isFinite()) return 1.0
        val magnitude = TEN.pow(floor(log10(raw)))
        val fraction = raw / magnitude + EPSILON
        // Nearest of 1, 2, 5, 10 (thresholds halfway on a log-ish scale, as on printed charts).
        val nice = when {
            fraction < ONE_OR_TWO -> NICE_ONE
            fraction < TWO_OR_FIVE -> NICE_TWO
            fraction < FIVE_OR_TEN -> NICE_FIVE
            else -> TEN
        }
        return nice * magnitude
    }

    /** The vertical axis for data between [dataMin] and [dataMax]. */
    fun yAxis(dataMin: Double, dataMax: Double, scale: ChartYScale, count: Int = Y_TICKS): ChartAxis {
        val low = if (scale == ChartYScale.FROM_ZERO) minOf(0.0, dataMin) else dataMin
        val high = max(dataMax, low)
        // A flat line still gets a visible band around it.
        val span = (high - low).takeIf { it > 0 } ?: max(abs(high), 1.0)
        val step = niceStep(span, count)
        val min = if (scale == ChartYScale.FROM_ZERO && dataMin >= 0) 0.0 else floor(low / step) * step
        var top = ceil(high / step) * step
        if (top <= min) top = min + step
        return ChartAxis(min, top, step)
    }

    /**
     * Distance labels between [start] and [end] (exclusive of the start, like "2 km · 4 km · 6 km"): round
     * multiples of a 1-2-5 step. Empty for a ride too short to need any.
     */
    fun xTicks(start: Double, end: Double, count: Int = X_TICKS): List<Double> {
        val span = end - start
        if (span <= 0.0 || !span.isFinite()) return emptyList()
        val step = niceStep(span, count)
        val firstIndex = floor(start / step + EPSILON).toLong() + 1
        return generateSequence(firstIndex) { it + 1 }
            .map { round(it * step, step) }
            .takeWhile { it <= end + step * EPSILON }
            .toList()
    }

    /** [value] without floating point noise (0.6000000000000001 → 0.6), to the decimals of [step]. */
    fun round(value: Double, step: Double): Double {
        val factor = TEN.pow(decimalsFor(step))
        return Math.round(value * factor) / factor
    }

    /** Decimals needed to show multiples of [step] exactly. */
    fun decimalsFor(step: Double): Int {
        if (step <= 0.0 || !step.isFinite() || step >= 1.0) return 0
        return ceil(-log10(step) - EPSILON).toInt().coerceIn(0, MAX_DECIMALS)
    }

    private const val TEN = 10.0
    private const val MAX_DECIMALS = 3
    private const val NICE_ONE = 1.0
    private const val NICE_TWO = 2.0
    private const val NICE_FIVE = 5.0
    private const val ONE_OR_TWO = 1.5
    private const val TWO_OR_FIVE = 3.0
    private const val FIVE_OR_TEN = 7.0
}

/** Tolerance for floating point steps (0.1 + 0.2 …). */
private const val EPSILON = 1e-9
