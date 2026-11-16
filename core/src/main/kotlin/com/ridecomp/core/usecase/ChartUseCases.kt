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
 * Loads a ride's samples for charts, downsampled to at most [ActivityConfig.CHART_MAX_POINTS] by keeping
 * every n-th point. Reads in bounded chunks, never the whole track at once (DATA-04, 02-DESIGN §6).
 */
public class LoadRideChartUseCase(private val repository: ActivityRepository) {
    public suspend operator fun invoke(
        activityId: Long,
        maxPoints: Int = ActivityConfig.CHART_MAX_POINTS,
    ): List<ChartPoint> {
        val count = repository.getSampleCount(activityId)
        if (count == 0) return emptyList()
        val stride = ceil(count.toDouble() / maxPoints).toInt().coerceAtLeast(1)
        val result = ArrayList<ChartPoint>(minOf(count, maxPoints))
        var index = 0
        var after = Long.MIN_VALUE
        while (true) {
            val chunk = repository.getSamples(activityId, after, ActivityConfig.TRACK_CHUNK_SIZE)
            if (chunk.isEmpty()) break
            chunk.forEach { sample ->
                if (index % stride == 0) result += sample.toChartPoint()
                index++
            }
            after = chunk.last().timeMs
        }
        return result
    }

    private fun RideSample.toChartPoint() = ChartPoint(
        distanceM,
        speedMps,
        altitudeM,
        heartRateBpm,
        cadenceRpm,
        powerW,
        latDeg,
        lonDeg,
    )
}
