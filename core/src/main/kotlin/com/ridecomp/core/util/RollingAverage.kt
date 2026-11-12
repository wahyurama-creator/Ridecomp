package com.ridecomp.core.util

/**
 * Rolling mean over the last [windowSize] numeric samples in O(1) per sample, without boxing.
 * Used for power 3s/10s/30s, smoothing and similar windows (05-DATA-MODEL §3.6, MEM-05).
 *
 * Not thread-safe: confine each instance to one coroutine (CONC-01).
 */
public class RollingAverage(public val windowSize: Int) {
    init {
        require(windowSize > 0) { "windowSize must be > 0, was $windowSize" }
    }

    private val values = DoubleArray(windowSize)
    private var head = 0
    private var sum = 0.0

    /** Number of samples currently in the window (≤ [windowSize]). */
    public var count: Int = 0
        private set

    public val isFull: Boolean get() = count == windowSize

    /** Adds a sample. */
    public fun add(value: Double) {
        if (isFull) {
            sum -= values[head]
            values[head] = value
            head = (head + 1) % windowSize
        } else {
            values[(head + count) % windowSize] = value
            count++
        }
        sum += value
    }

    /** Mean of the samples in the window, or null when empty. */
    public fun averageOrNull(): Double? = if (count == 0) null else sum / count

    /** Mean only once the window is full (e.g. NP needs a full 30 s window), otherwise null. */
    public fun fullAverageOrNull(): Double? = if (isFull) sum / count else null

    /** Empties the window. */
    public fun clear() {
        values.fill(0.0)
        head = 0
        count = 0
        sum = 0.0
    }
}
