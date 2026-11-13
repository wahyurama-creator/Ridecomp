package com.ridecomp.core.engine.track

import com.ridecomp.core.model.RideSample

/** Limits for reading a whole ride track into memory (best efforts, segments, training load). */
public object RideTrackConfig {
    /** Upper bound of samples read for one ride (24 h at 1 Hz): keeps memory bounded (MEM-01). */
    public const val MAX_SAMPLES: Int = 86_400

    /** Samples read per database page. */
    public const val PAGE_SIZE: Int = 2_000
}

/**
 * The parts of a ride's track that post-ride analysis needs, in primitive arrays (about 50 bytes per second
 * instead of a [RideSample] object). Missing positions are NaN, missing heart rate/power are [NO_VALUE].
 * Filled page by page, bounded by [RideTrackConfig.MAX_SAMPLES].
 */
public class RideTrack(capacity: Int = INITIAL_CAPACITY) {
    private var times = LongArray(capacity)
    private var distances = DoubleArray(capacity)
    private var lats = DoubleArray(capacity)
    private var lons = DoubleArray(capacity)
    private var altitudes = DoubleArray(capacity)
    private var heartRates = IntArray(capacity)
    private var powers = IntArray(capacity)

    public var size: Int = 0
        private set

    /** True when at least one sample carries power-meter data (estimated power never counts). */
    public var hasPower: Boolean = false
        private set

    public val isFull: Boolean get() = size >= RideTrackConfig.MAX_SAMPLES

    public fun add(sample: RideSample) {
        if (isFull) return
        if (size == times.size) grow()
        times[size] = sample.timeMs
        distances[size] = sample.distanceM
        lats[size] = sample.latDeg ?: Double.NaN
        lons[size] = sample.lonDeg ?: Double.NaN
        altitudes[size] = sample.altitudeM ?: Double.NaN
        heartRates[size] = sample.heartRateBpm ?: NO_VALUE
        val power = sample.powerW
        powers[size] = power ?: NO_VALUE
        if (power != null) hasPower = true
        size++
    }

    public fun timeMs(index: Int): Long = times[index]

    public fun distanceM(index: Int): Double = distances[index]

    public fun latDeg(index: Int): Double = lats[index]

    public fun lonDeg(index: Int): Double = lons[index]

    public fun altitudeM(index: Int): Double = altitudes[index]

    public fun hasPosition(index: Int): Boolean = !lats[index].isNaN() && !lons[index].isNaN()

    public fun heartRateBpm(index: Int): Int? = heartRates[index].takeIf { it != NO_VALUE }

    /** Power-meter watts; a missing sample counts as 0 W (coasting or dropout). */
    public fun powerW(index: Int): Int = powers[index].takeIf { it != NO_VALUE } ?: 0

    private fun grow() {
        val newSize = minOf(times.size * GROWTH_FACTOR, RideTrackConfig.MAX_SAMPLES)
        times = times.copyOf(newSize)
        distances = distances.copyOf(newSize)
        lats = lats.copyOf(newSize)
        lons = lons.copyOf(newSize)
        altitudes = altitudes.copyOf(newSize)
        heartRates = heartRates.copyOf(newSize)
        powers = powers.copyOf(newSize)
    }

    public companion object {
        public const val NO_VALUE: Int = Int.MIN_VALUE
        private const val INITIAL_CAPACITY = 1_024
        private const val GROWTH_FACTOR = 2
    }
}
