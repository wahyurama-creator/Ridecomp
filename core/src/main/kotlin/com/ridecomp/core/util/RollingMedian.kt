package com.ridecomp.core.util

/**
 * Rolling median over the last [windowSize] samples (bounded, MEM-05). A single wild value does not move it,
 * unlike a mean: used to smooth GPS altitude, whose spikes reach hundreds of meters (D-148).
 * O(n log n) per sample for a small window fed at 1 Hz.
 *
 * Not thread-safe: confine each instance to one coroutine (CONC-01).
 */
public class RollingMedian(public val windowSize: Int) {
    init {
        require(windowSize > 0) { "windowSize must be > 0, was $windowSize" }
    }

    private val values = DoubleArray(windowSize)
    private val sorted = DoubleArray(windowSize)
    private var head = 0
    private var count = 0

    public fun add(value: Double) {
        values[head] = value
        head = (head + 1) % windowSize
        if (count < windowSize) count++
    }

    /** Median of the samples in the window (mean of the middle two for an even count), or null when empty. */
    public fun medianOrNull(): Double? {
        if (count == 0) return null
        values.copyInto(sorted, endIndex = count)
        sorted.sort(0, count)
        val middle = count / 2
        return if (count % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    public fun clear() {
        head = 0
        count = 0
    }
}
