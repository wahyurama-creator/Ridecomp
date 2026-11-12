package com.ridecomp.core.engine.metrics

/**
 * Exponential moving average: `value = alpha * x + (1 - alpha) * value` (05-DATA-MODEL §3.2, α ≈ 0.3).
 * The first sample initialises the value.
 */
public class ExponentialSmoother(private val alpha: Double) {
    init {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1], was $alpha" }
    }

    /** Current smoothed value, or null before the first sample. */
    public var value: Double? = null
        private set

    /** Adds a sample and returns the new smoothed value. */
    public fun add(sample: Double): Double {
        val next = value?.let { alpha * sample + (1 - alpha) * it } ?: sample
        value = next
        return next
    }

    public fun reset() {
        value = null
    }
}
