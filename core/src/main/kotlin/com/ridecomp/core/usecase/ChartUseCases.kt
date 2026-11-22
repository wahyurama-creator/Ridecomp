package com.ridecomp.core.usecase

import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.repository.ActivityRepository
import kotlin.math.ceil

/** One chart point: x = distance (m), y values may be null (no data). */
public data class ChartPoint(
    val distanceM: Double,
    val speedMps: Double?,
    val altitudeM: Double?,
    val heartRateBpm: Int?,
    val cadenceRpm: Int?,
    val powerW: Int?,
    /** Position, for the route sketch of the detail screen; null without a GPS fix. */
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
)

/**
 * Loads a ride's samples for charts, reduced to at most [ActivityConfig.CHART_MAX_POINTS] (T11.1): each
 * point averages a run of consecutive samples, so short spikes are smoothed instead of skipped and a long
 * ride never draws thousands of points. A value with no data in the whole run stays null (a gap). Reads in
 * bounded chunks, never the whole track at once (DATA-04, 02-DESIGN §6).
 */
public class LoadRideChartUseCase(private val repository: ActivityRepository) {
    public suspend operator fun invoke(
        activityId: Long,
        maxPoints: Int = ActivityConfig.CHART_MAX_POINTS,
    ): List<ChartPoint> {
        val count = repository.getSampleCount(activityId)
        if (count == 0) return emptyList()
        val bucket = ChartBucket(ceil(count.toDouble() / maxPoints).toInt().coerceAtLeast(1))
        val result = ArrayList<ChartPoint>(minOf(count, maxPoints))
        var after = Long.MIN_VALUE
        while (true) {
            val chunk = repository.getSamples(activityId, after, ActivityConfig.TRACK_CHUNK_SIZE)
            if (chunk.isEmpty()) break
            chunk.forEach { sample -> bucket.add(sample)?.let(result::add) }
            after = chunk.last().timeMs
        }
        bucket.flush()?.let(result::add)
        return result
    }
}

/**
 * Where the GPS signal was lost (forest, tunnel; D-149): among the points that have a fix, in order, the
 * indices `i` whose line to point `i + 1` crosses chart points without one. That part of the drawn route
 * is a straight guess, not the way ridden.
 */
public fun List<ChartPoint>.routeGaps(): List<Int> {
    val gaps = mutableListOf<Int>()
    var lastFixIndex = -1
    var isMissing = false
    forEach { point ->
        if (point.latDeg != null && point.lonDeg != null) {
            if (isMissing && lastFixIndex >= 0) gaps += lastFixIndex
            lastFixIndex++
            isMissing = false
        } else if (lastFixIndex >= 0) {
            isMissing = true
        }
    }
    return gaps
}

/** Averages [size] samples into one chart point; nulls are skipped, an all-null value stays null. */
private class ChartBucket(private val size: Int) {
    private var samples = 0
    private var distanceM = 0.0
    private val speed = Average()
    private val altitude = Average()
    private val heartRate = Average()
    private val cadence = Average()
    private val power = Average()
    private var latDeg: Double? = null
    private var lonDeg: Double? = null

    /** Adds a sample; returns the finished point when the bucket is full. */
    fun add(sample: RideSample): ChartPoint? {
        samples++
        distanceM = sample.distanceM
        speed.add(sample.speedMps)
        altitude.add(sample.altitudeM)
        heartRate.add(sample.heartRateBpm?.toDouble())
        cadence.add(sample.cadenceRpm?.toDouble())
        power.add(sample.powerW?.toDouble())
        if (latDeg == null && sample.latDeg != null && sample.lonDeg != null) {
            latDeg = sample.latDeg
            lonDeg = sample.lonDeg
        }
        return if (samples == size) flush() else null
    }

    /** The point of the samples added since the last one, or null when there are none. */
    fun flush(): ChartPoint? {
        if (samples == 0) return null
        val point = ChartPoint(
            distanceM = distanceM,
            speedMps = speed.take(),
            altitudeM = altitude.take(),
            heartRateBpm = heartRate.take()?.let(Math::round)?.toInt(),
            cadenceRpm = cadence.take()?.let(Math::round)?.toInt(),
            powerW = power.take()?.let(Math::round)?.toInt(),
            latDeg = latDeg,
            lonDeg = lonDeg,
        )
        samples = 0
        latDeg = null
        lonDeg = null
        return point
    }
}

/** Running mean that resets when taken. */
private class Average {
    private var sum = 0.0
    private var count = 0

    fun add(value: Double?) {
        if (value == null || !value.isFinite()) return
        sum += value
        count++
    }

    fun take(): Double? = (if (count == 0) null else sum / count).also {
        sum = 0.0
        count = 0
    }
}
