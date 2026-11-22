package com.ridecomp.core.usecase

import com.ridecomp.core.config.HeartRateConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.training.LoadInputs
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.PressureSource
import kotlin.math.ceil

/** Sections of the ride detail, in the order they are shown (14-PHASE11-PLAN T11.2). */
public enum class AnalysisSectionKind { SPEED, ELEVATION, HEART_RATE, POWER, CADENCE }

/** A number in a section's list. */
public enum class AnalysisStatKind { AVG, MAX, MIN, ASCENT, DESCENT, NORMALIZED, MOVING_TIME, ELAPSED_TIME }

/** Times are seconds, not values of the section's unit: they are never converted. */
public val AnalysisStatKind.isDuration: Boolean
    get() = this == AnalysisStatKind.MOVING_TIME || this == AnalysisStatKind.ELAPSED_TIME

/** One number of a section, in SI units (m/s, m, bpm, W, rpm, s); converted only for display. */
public data class AnalysisStat(val kind: AnalysisStatKind, val value: Double)

/** A section with its numbers; its chart is the matching value of each [ChartPoint]. */
public data class AnalysisSection(val kind: AnalysisSectionKind, val stats: List<AnalysisStat>)

/** Where the HR max of the zones comes from: the profile, or 220 − age when the profile has none. */
public enum class HrMaxSource { PROFILE, AGE }

/** One zone: its range in bpm or W ([high] null for the top zone) and the ride's seconds in it. */
public data class ZoneTimeSlice(val zone: Int, val low: Int, val high: Int?, val seconds: Long)

/** Time in zones Z1..Z5 of a ride (T11.5), from every sample, not the averaged chart points. */
public data class HeartRateZones(val hrMaxBpm: Int, val source: HrMaxSource, val zones: List<ZoneTimeSlice>) {
    public val totalSeconds: Long get() = zones.sumOf { it.seconds }
}

/**
 * Everything the detail page draws: the chart points (≤ 300), the sections that have data and, when the
 * ride has heart rate and an HR max is known, the time in each zone (null otherwise).
 */
public data class ActivityAnalysis(
    val points: List<ChartPoint>,
    val sections: List<AnalysisSection>,
    val heartRateZones: HeartRateZones? = null,
    /** Time in power zones (T11.6): from the power meter, else the estimate; null without either. */
    val powerZones: PowerZones? = null,
)

/**
 * Loads the analysis of a finished ride once per screen (T11.2): the downsampled chart points and, per
 * section, its numbers. A section without at least two values is left out, so the page hides it. Averages
 * and maxima come from the ride totals when it has them (the chart is averaged), else from the chart.
 * CPU work: the caller runs it on the default dispatcher.
 */
public class LoadActivityAnalysisUseCase(
    private val loadChart: LoadRideChartUseCase,
    private val activities: ActivityRepository,
    private val profiles: ProfileRepository,
    private val estimateFtp: EstimateFtpUseCase,
    private val clock: Clock,
) {
    public suspend operator fun invoke(activityId: Long, totals: RideTotals?): ActivityAnalysis {
        val points = loadChart(activityId)
        val sections = AnalysisSectionKind.entries.mapNotNull { kind ->
            val values = points.mapNotNull { kind.valueOf(it) }
            if (values.size < MIN_SECTION_VALUES) null else AnalysisSection(kind, kind.stats(values, totals))
        }
        if (points.isEmpty()) return ActivityAnalysis(points, sections)
        // Zones from every sample (1 Hz), not the averaged chart points: one more pass over the ride.
        val profile = profiles.get()
        val hrMax = LoadInputs.of(profile, clock.nowMs()).hrMaxBpm
        val ftp = estimateFtp()
        val isMeter = sections.any { it.kind == AnalysisSectionKind.POWER }
        val counter = ZoneCounter(hrMax, ftp.ftpW, isMeter).countAll(activities, activityId)
        val heartRateZones = hrMax?.takeIf { counter.hasHeartRate }?.let {
            val source = if (profile.hrMaxBpm != null) HrMaxSource.PROFILE else HrMaxSource.AGE
            HeartRateZones(it, source, HeartRateZoneBounds.of(it).withSeconds(counter.heartRate))
        }
        val powerZones = if (counter.hasPower) {
            PowerZones(ftp, !isMeter, PowerZoneBounds.of(ftp.ftpW).withSeconds(counter.power))
        } else {
            null
        }
        return ActivityAnalysis(points, sections, heartRateZones, powerZones)
    }
}

private fun List<ZoneTimeSlice>.withSeconds(seconds: LongArray): List<ZoneTimeSlice> =
    mapIndexed { index, slice -> slice.copy(seconds = seconds[index]) }

/** The bpm range of each zone for [hrMax], as `Zones.heartRate` assigns them (upper bound starts the next). */
public object HeartRateZoneBounds {
    public fun of(hrMax: Int): List<ZoneTimeSlice> {
        val starts =
            listOf(0) + HeartRateConfig.ZONE_UPPER_PERCENT_HR_MAX.map { ceil(hrMax * it / Units.PERCENT).toInt() }
        return starts.mapIndexed { index, start ->
            ZoneTimeSlice(zone = index + 1, low = start, high = starts.getOrNull(index + 1)?.minus(1), seconds = 0)
        }
    }
}

/** The value of this section at [point], in SI units; null where there is no data. */
public fun AnalysisSectionKind.valueOf(point: ChartPoint): Double? = when (this) {
    AnalysisSectionKind.SPEED -> point.speedMps
    AnalysisSectionKind.ELEVATION -> point.altitudeM
    AnalysisSectionKind.HEART_RATE -> point.heartRateBpm?.toDouble()
    AnalysisSectionKind.POWER -> point.powerW?.toDouble()
    AnalysisSectionKind.CADENCE -> point.cadenceRpm?.toDouble()
}

private fun AnalysisSectionKind.stats(values: List<Double>, totals: RideTotals?): List<AnalysisStat> = when (this) {
    AnalysisSectionKind.SPEED -> speedStats(values, totals)
    AnalysisSectionKind.ELEVATION -> elevationStats(values, totals)
    AnalysisSectionKind.HEART_RATE -> listOfNotNull(
        stat(AnalysisStatKind.AVG, totals?.avgHeartRateBpm?.toDouble() ?: values.average()),
        stat(AnalysisStatKind.MAX, totals?.maxHeartRateBpm?.toDouble() ?: values.max()),
    )
    AnalysisSectionKind.POWER -> listOfNotNull(
        stat(AnalysisStatKind.AVG, totals?.avgPowerW?.toDouble() ?: values.average()),
        stat(AnalysisStatKind.NORMALIZED, totals?.normalizedPowerW?.toDouble()),
        stat(AnalysisStatKind.MAX, totals?.maxPowerW?.toDouble() ?: values.max()),
    )
    // Cadence averages only while pedaling (zeros left out), as the ride totals do.
    AnalysisSectionKind.CADENCE -> listOfNotNull(
        stat(AnalysisStatKind.AVG, totals?.avgCadenceRpm?.toDouble() ?: values.filter { it > 0 }.averageOrNull()),
        stat(AnalysisStatKind.MAX, values.max()),
    )
}

/**
 * Speed analysis (T11.3): average while moving (stops left out), as the ride totals do; the maximum is the
 * one filtered while recording (EMA, no GPS spikes, D-118), so it matches the ride screen; moving and total
 * time from the totals.
 */
private fun speedStats(values: List<Double>, totals: RideTotals?): List<AnalysisStat> = listOfNotNull(
    stat(AnalysisStatKind.AVG, totals?.avgSpeedMps?.takeIf { it > 0 } ?: values.filter { it > 0 }.averageOrNull()),
    stat(AnalysisStatKind.MAX, totals?.maxSpeedMps?.takeIf { it > 0 } ?: values.max()),
    stat(AnalysisStatKind.MOVING_TIME, totals?.movingS?.takeIf { it > 0 }?.toDouble()),
    stat(AnalysisStatKind.ELAPSED_TIME, totals?.elapsedS?.takeIf { it > 0 }?.toDouble()),
)

/** Elevation analysis (T11.4): gain and highest point first, then descent and lowest point. */
private fun elevationStats(values: List<Double>, totals: RideTotals?): List<AnalysisStat> = listOfNotNull(
    stat(AnalysisStatKind.ASCENT, totals?.ascentM),
    stat(AnalysisStatKind.MAX, values.max()),
    stat(AnalysisStatKind.DESCENT, totals?.descentM),
    stat(AnalysisStatKind.MIN, values.min()),
)

private fun stat(kind: AnalysisStatKind, value: Double?): AnalysisStat? = value?.let { AnalysisStat(kind, it) }

private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()

/** A line needs two points. */
private const val MIN_SECTION_VALUES = 2

/**
 * Whether this phone has a barometer. Without one the ride's climbing comes from GPS altitude and is shown
 * as an estimate (D-148): it over-counts even after filtering.
 */
public class HasBarometerUseCase(private val pressure: PressureSource) {
    public operator fun invoke(): Boolean = pressure.hasBarometer
}
