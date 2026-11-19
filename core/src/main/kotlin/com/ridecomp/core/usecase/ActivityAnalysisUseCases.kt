package com.ridecomp.core.usecase

import com.ridecomp.core.model.RideTotals

/** Sections of the ride detail, in the order they are shown (14-PHASE11-PLAN T11.2). */
public enum class AnalysisSectionKind { SPEED, ELEVATION, HEART_RATE, POWER, CADENCE }

/** A number in a section's list. */
public enum class AnalysisStatKind { AVG, MAX, MIN, ASCENT, DESCENT, NORMALIZED }

/** One number of a section, in SI units (m/s, m, bpm, W, rpm); converted only for display. */
public data class AnalysisStat(val kind: AnalysisStatKind, val value: Double)

/** A section with its numbers; its chart is the matching value of each [ChartPoint]. */
public data class AnalysisSection(val kind: AnalysisSectionKind, val stats: List<AnalysisStat>)

/** Everything the detail page draws: the chart points (≤ 300) and the sections that have data. */
public data class ActivityAnalysis(val points: List<ChartPoint>, val sections: List<AnalysisSection>)

/**
 * Loads the analysis of a finished ride once per screen (T11.2): the downsampled chart points and, per
 * section, its numbers. A section without at least two values is left out, so the page hides it. Averages
 * and maxima come from the ride totals when it has them (the chart is averaged), else from the chart.
 * CPU work: the caller runs it on the default dispatcher.
 */
public class LoadActivityAnalysisUseCase(private val loadChart: LoadRideChartUseCase) {
    public suspend operator fun invoke(activityId: Long, totals: RideTotals?): ActivityAnalysis {
        val points = loadChart(activityId)
        val sections = AnalysisSectionKind.entries.mapNotNull { kind ->
            val values = points.mapNotNull { kind.valueOf(it) }
            if (values.size < MIN_SECTION_VALUES) null else AnalysisSection(kind, kind.stats(values, totals))
        }
        return ActivityAnalysis(points, sections)
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

/** Average while moving (stops left out), as the ride totals do. */
private fun speedStats(values: List<Double>, totals: RideTotals?): List<AnalysisStat> = listOfNotNull(
    stat(AnalysisStatKind.AVG, totals?.avgSpeedMps?.takeIf { it > 0 } ?: values.filter { it > 0 }.averageOrNull()),
    stat(AnalysisStatKind.MAX, totals?.maxSpeedMps?.takeIf { it > 0 } ?: values.max()),
)

private fun elevationStats(values: List<Double>, totals: RideTotals?): List<AnalysisStat> = listOfNotNull(
    stat(AnalysisStatKind.ASCENT, totals?.ascentM),
    stat(AnalysisStatKind.DESCENT, totals?.descentM),
    stat(AnalysisStatKind.MIN, values.min()),
    stat(AnalysisStatKind.MAX, values.max()),
)

private fun stat(kind: AnalysisStatKind, value: Double?): AnalysisStat? = value?.let { AnalysisStat(kind, it) }

private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()

/** A line needs two points. */
private const val MIN_SECTION_VALUES = 2
