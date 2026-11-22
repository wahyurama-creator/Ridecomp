package com.ridecomp.core.engine.metrics

import com.ridecomp.core.config.ElevationConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.util.RingBuffer
import com.ridecomp.core.util.RollingAverage
import com.ridecomp.core.util.RollingMedian
import kotlin.math.pow

/** Where an altitude value came from; decides the ascent hysteresis (05-DATA-MODEL §3.5). */
public enum class AltitudeSource { BAROMETER, GPS }

/** Elevation state exposed to the ride. */
public data class ElevationSnapshot(
    /** Smoothed altitude, null until the first value. */
    val altitudeM: Double?,
    val ascentM: Double,
    val descentM: Double,
    /** Grade over the last ~50 m in percent, null until enough distance was covered. */
    val gradePercent: Double?,
)

/** Converts barometric pressure to altitude with the standard atmosphere formula. */
public object BarometricAltitude {
    public fun altitudeM(pressureHpa: Double): Double = ElevationConfig.BAROMETRIC_SCALE_M *
        (1.0 - (pressureHpa / ElevationConfig.SEA_LEVEL_PRESSURE_HPA).pow(ElevationConfig.BAROMETRIC_EXPONENT))
}

/**
 * Smoothed altitude, total ascent/descent with hysteresis and grade (05-DATA-MODEL §3.5).
 *
 * - Source: barometer when present (offset calibrated once to the first GPS altitude), else GPS.
 * - Smoothing: barometer 10 s moving average; GPS 30 s median, then at most 0.5 m per sample (D-148).
 * - Ascent: an anchor moves only when altitude changed by ≥ threshold (2 m barometer / 10 m GPS),
 *   so noise is never counted.
 * - Grade: Δalt / Δdist over the last ~50 m (≥ 30 m covered), clamped to ±30 %.
 *
 * Feed one value per second; all buffers are bounded (MEM-05).
 */
public class ElevationTracker(private val source: AltitudeSource) {
    private val smoother = RollingAverage(ElevationConfig.SMOOTHING_WINDOW_S)
    private val gpsMedian = RollingMedian(ElevationConfig.GPS_SMOOTHING_WINDOW_S)
    private var gpsFilteredM: Double? = null
    private val gradeWindow = RingBuffer<Pair<Double, Double>>(ElevationConfig.GRADE_BUFFER_SIZE)
    private var anchorM: Double? = null
    private var barometerOffsetM: Double? = null
    private var ascentM = 0.0
    private var descentM = 0.0
    private var altitudeM: Double? = null
    private var gradePercent: Double? = null

    private val thresholdM: Double = when (source) {
        AltitudeSource.BAROMETER -> ElevationConfig.HYSTERESIS_BAROMETER_M
        AltitudeSource.GPS -> ElevationConfig.HYSTERESIS_GPS_M
    }

    /** Restores totals after a crash/kill recovery. */
    public fun restore(ascentM: Double, descentM: Double) {
        this.ascentM = ascentM
        this.descentM = descentM
    }

    /**
     * Adds one second of data. [rawAltitudeM] is GPS altitude or, for [AltitudeSource.BAROMETER], the
     * altitude computed from pressure; [gpsAltitudeM] calibrates the barometer offset once.
     * [isMoving] = recording and moving: totals and grade only change then.
     */
    public fun add(
        rawAltitudeM: Double?,
        gpsAltitudeM: Double?,
        distanceM: Double,
        isMoving: Boolean,
    ): ElevationSnapshot {
        val raw = rawAltitudeM?.takeIf { it.isFinite() }
        if (raw != null) {
            calibrateOffsetOnce(raw, gpsAltitudeM)
            val smoothed = smooth(calibrate(raw))
            altitudeM = smoothed
            if (smoothed != null) updateTotals(smoothed, isMoving)
            if (smoothed != null && isMoving) updateGrade(smoothed, distanceM)
        }
        return snapshot()
    }

    public fun snapshot(): ElevationSnapshot = ElevationSnapshot(altitudeM, ascentM, descentM, gradePercent)

    private fun smooth(valueM: Double): Double? = when (source) {
        AltitudeSource.BAROMETER -> {
            smoother.add(valueM)
            smoother.averageOrNull()
        }
        AltitudeSource.GPS -> {
            gpsMedian.add(valueM)
            gpsMedian.medianOrNull()?.let(::limitGpsStep)
        }
    }

    /** A real climb is slow: a jump larger than [ElevationConfig.GPS_MAX_ALTITUDE_STEP_M] per sample is GPS error. */
    private fun limitGpsStep(medianM: Double): Double {
        val previous = gpsFilteredM
        val step = ElevationConfig.GPS_MAX_ALTITUDE_STEP_M
        val next = if (previous == null) medianM else previous + (medianM - previous).coerceIn(-step, step)
        gpsFilteredM = next
        return next
    }

    private fun calibrate(rawM: Double): Double = when (source) {
        AltitudeSource.GPS -> rawM
        AltitudeSource.BAROMETER -> rawM + (barometerOffsetM ?: 0.0)
    }

    /**
     * The barometer gives good relative but poor absolute altitude: the first GPS altitude sets the
     * offset. Smoothing and the ascent anchor restart then, so the offset jump is never counted as a climb.
     */
    private fun calibrateOffsetOnce(rawM: Double, gpsAltitudeM: Double?) {
        val gps = gpsAltitudeM?.takeIf { it.isFinite() }
        if (source == AltitudeSource.BAROMETER && barometerOffsetM == null && gps != null) {
            barometerOffsetM = gps - rawM
            smoother.clear()
            gradeWindow.clear()
            anchorM = null
        }
    }

    private fun updateTotals(smoothedM: Double, isMoving: Boolean) {
        val anchor = anchorM
        when {
            anchor == null -> anchorM = smoothedM
            !isMoving -> Unit
            smoothedM - anchor >= thresholdM -> {
                ascentM += smoothedM - anchor
                anchorM = smoothedM
            }
            anchor - smoothedM >= thresholdM -> {
                descentM += anchor - smoothedM
                anchorM = smoothedM
            }
        }
    }

    private fun updateGrade(smoothedM: Double, distanceM: Double) {
        gradeWindow.add(distanceM to smoothedM)
        val reference = gradeWindow.lastOrNull { distanceM - it.first >= ElevationConfig.GRADE_WINDOW_M }
            ?: gradeWindow.oldestOrNull()?.takeIf { distanceM - it.first >= ElevationConfig.GRADE_MIN_DISTANCE_M }
        gradePercent = reference?.let {
            val grade = (smoothedM - it.second) / (distanceM - it.first) * Units.PERCENT
            grade.coerceIn(-ElevationConfig.GRADE_LIMIT_PERCENT, ElevationConfig.GRADE_LIMIT_PERCENT)
        } ?: gradePercent
    }
}
