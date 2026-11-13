package com.ridecomp.core.engine.safety

import com.ridecomp.core.config.EstimatedPowerConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.util.RingBuffer
import com.ridecomp.core.util.RollingAverage

/**
 * Thresholds of the incident detector (12-FEATURE-SPECS §5.2). All of them must be calibrated with field
 * tests (L17: falls on soft ground, potholes, speed bumps, phone dropped while stopped, hard braking).
 */
public object IncidentConfig {
    /** Speed before the impact: average of the last 5 s at or above 8 km/h. */
    public val MIN_SPEED_BEFORE_MPS: Double = Units.kmhToMps(8.0)
    public const val SPEED_WINDOW_S: Int = 5

    /** Impact: acceleration magnitude at or above 3.5 g. */
    public const val IMPACT_G: Double = 3.5
    public val IMPACT_MPS2: Double = IMPACT_G * EstimatedPowerConfig.GRAVITY_MPS2

    /** Confirmation must happen within 15 s after the impact. */
    public const val CONFIRM_WINDOW_MS: Long = 15_000L

    /** "Still": speed below 2 km/h … */
    public val STILL_SPEED_MPS: Double = Units.kmhToMps(2.0)

    /** … and low variance of |a| over this window (phone lying still, not pedalling). */
    public const val STILL_WINDOW_MS: Long = 5_000L
    public const val STILL_MAX_VARIANCE_MPS2_SQ: Double = 1.0

    /** Enough samples in the still window to judge variance (≈ 50 Hz sensor, 5 s). */
    public const val STILL_MIN_SAMPLES: Int = 50

    /** Ring buffer of accelerometer samples (≈ 6 s at 50 Hz, MEM-05). */
    public const val SAMPLE_BUFFER_SIZE: Int = 300
}

/** One accelerometer reading: magnitude of the acceleration vector, gravity included. */
public data class AccelSample(val timeMs: Long, val magnitudeMps2: Double)

/** A suspected incident: the start of the countdown (12-FEATURE-SPECS §5.3). */
public data class IncidentDetection(val impactAtMs: Long, val peakG: Double, val speedBeforeMps: Double)

/**
 * Fall detection (12-FEATURE-SPECS §5.2): a hard impact while riding (≥ 8 km/h), followed within 15 s by
 * standing still (speed < 2 km/h and low acceleration variance). Run only while recording; confine to one
 * coroutine (CONC-01).
 */
public class IncidentDetector {
    private val samples = RingBuffer<AccelSample>(IncidentConfig.SAMPLE_BUFFER_SIZE)
    private val speeds = RollingAverage(IncidentConfig.SPEED_WINDOW_S)
    private var candidate: IncidentDetection? = null
    private var currentSpeedMps: Double? = null

    /** Accelerometer sample (≈ 50 Hz). */
    public fun onAcceleration(sample: AccelSample) {
        samples.add(sample)
        val speedBefore = speeds.averageOrNull() ?: return
        val isImpact = sample.magnitudeMps2 >= IncidentConfig.IMPACT_MPS2
        val isRiding = speedBefore >= IncidentConfig.MIN_SPEED_BEFORE_MPS
        if (!isImpact || !isRiding) return
        val peakG = sample.magnitudeMps2 / EstimatedPowerConfig.GRAVITY_MPS2
        val existing = candidate
        candidate = if (existing == null) {
            IncidentDetection(sample.timeMs, peakG, speedBefore)
        } else {
            existing.copy(peakG = maxOf(existing.peakG, peakG))
        }
    }

    /**
     * Called once per second with the current speed (GPS or wheel; null when unknown). Returns the
     * incident when the stillness after an impact is confirmed, once.
     */
    public fun onSecond(nowMs: Long, speedMps: Double?): IncidentDetection? {
        currentSpeedMps = speedMps
        val pending = candidate
        // The speed "before" the impact must not include the slow seconds after it.
        if (pending == null) speedMps?.let(speeds::add)
        if (pending == null) return null
        val sinceImpactMs = nowMs - pending.impactAtMs
        return when {
            sinceImpactMs > IncidentConfig.CONFIRM_WINDOW_MS -> null.also { reset() }
            isStill(nowMs, pending.impactAtMs) -> pending.also { reset() }
            else -> null
        }
    }

    /** Clears all state, e.g. on pause or when the countdown starts. */
    public fun reset() {
        candidate = null
        speeds.clear()
    }

    private fun isStill(nowMs: Long, impactAtMs: Long): Boolean {
        val isSlow = currentSpeedMps?.let { it < IncidentConfig.STILL_SPEED_MPS } == true
        // Only samples after the impact: the spike itself must not count as movement.
        val window = samples.filter { it.timeMs > impactAtMs && nowMs - it.timeMs in 0..IncidentConfig.STILL_WINDOW_MS }
        val variance = window.magnitudeVariance()
        return isSlow &&
            window.size >= IncidentConfig.STILL_MIN_SAMPLES &&
            variance != null &&
            variance <= IncidentConfig.STILL_MAX_VARIANCE_MPS2_SQ
    }

    private fun List<AccelSample>.magnitudeVariance(): Double? {
        if (isEmpty()) return null
        val mean = sumOf { it.magnitudeMps2 } / size
        return sumOf { (it.magnitudeMps2 - mean).let { d -> d * d } } / size
    }
}
