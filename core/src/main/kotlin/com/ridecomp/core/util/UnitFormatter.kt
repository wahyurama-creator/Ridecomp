package com.ridecomp.core.util

import com.ridecomp.core.config.Units
import com.ridecomp.core.model.UnitSystem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converts SI values to display text (units are SI everywhere else; conversion only at display,
 * CLAUDE.md). Missing values become [NO_VALUE] — never "0" (02-DESIGN §3.4). Shared by the ride screen,
 * history and the foreground notification.
 */
public class UnitFormatter(private val unitSystem: UnitSystem, private val locale: Locale = Locale.getDefault()) {
    public val speedUnit: String get() = if (isMetric) KMH else MPH
    public val distanceUnit: String get() = if (isMetric) KM else MI
    public val elevationUnit: String get() = if (isMetric) M else FT

    private val isMetric: Boolean get() = unitSystem == UnitSystem.METRIC

    /** Speed with one decimal ("32.4"). */
    public fun speed(speedMps: Double?): String =
        speedMps?.takeIf { it.isFinite() }?.let { decimal(it * if (isMetric) Units.KMH_PER_MPS else Units.MPH_PER_MPS) }
            ?: NO_VALUE

    /** Distance in km/mi with two decimals ("12.34"). */
    public fun distance(distanceM: Double?): String = distanceM?.takeIf { it.isFinite() }?.let {
        String.format(locale, TWO_DECIMALS, it / if (isMetric) Units.METERS_PER_KM else Units.METERS_PER_MILE)
    } ?: NO_VALUE

    /** Elevation in whole m/ft. */
    public fun elevation(altitudeM: Double?): String = altitudeM?.takeIf { it.isFinite() }?.let {
        (if (isMetric) it else it / Units.METERS_PER_FOOT).roundToInt().toString()
    } ?: NO_VALUE

    /** Short distances (turn alerts) in whole m/ft, with [elevationUnit]. */
    public fun shortDistance(distanceM: Double?): String = elevation(distanceM)

    /** Grade in whole percent (05-DATA-MODEL §3.5: shown with 0 decimals). */
    public fun grade(gradePercent: Double?): String =
        gradePercent?.takeIf { it.isFinite() }?.roundToInt()?.toString() ?: NO_VALUE

    /** A time difference with its sign ("−0:13" faster, "+0:08" slower); the sign carries the meaning. */
    public fun signedDuration(deltaMs: Long): String =
        (if (deltaMs < 0) MINUS_SIGN else PLUS_SIGN) + duration(kotlin.math.abs(deltaMs))

    /** Integer metrics (HR, cadence, power, calories). */
    public fun integer(value: Int?): String = value?.toString() ?: NO_VALUE

    /** Duration as H:MM:SS, or M:SS below one hour. */
    public fun duration(durationMs: Long?): String {
        val totalSeconds = (durationMs ?: return NO_VALUE).coerceAtLeast(0) / Units.MS_PER_SECOND
        val hours = totalSeconds / Units.SECONDS_PER_HOUR
        val minutes = totalSeconds % Units.SECONDS_PER_HOUR / Units.SECONDS_PER_MINUTE
        val seconds = totalSeconds % Units.SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(Locale.ROOT, HOURS_FORMAT, hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, MINUTES_FORMAT, minutes, seconds)
        }
    }

    private fun decimal(value: Double): String {
        // Avoid "-0.0" for tiny negative noise.
        val shown = if (abs(value) < EPSILON) 0.0 else value
        return String.format(locale, ONE_DECIMAL, shown)
    }

    public companion object {
        /** Placeholder for a missing value. */
        public const val NO_VALUE: String = "--"
        public const val KMH: String = "km/h"
        public const val MPH: String = "mph"
        public const val KM: String = "km"
        public const val MI: String = "mi"
        public const val M: String = "m"
        public const val FT: String = "ft"
        public const val PERCENT_SIGN: String = "%"
        public const val MINUS_SIGN: String = "\u2212"
        public const val PLUS_SIGN: String = "+"
        private const val ONE_DECIMAL = "%.1f"
        private const val TWO_DECIMALS = "%.2f"
        private const val HOURS_FORMAT = "%d:%02d:%02d"
        private const val MINUTES_FORMAT = "%d:%02d"
        private const val EPSILON = 0.05
    }
}
