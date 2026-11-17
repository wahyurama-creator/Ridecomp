package com.ridecomp.feature.ride.model

import androidx.annotation.StringRes
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.route.TurnDirection
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Unit symbols that come from resources (not part of [UnitFormatter]). */
data class MetricUnitLabels(
    val bpm: String,
    val rpm: String,
    val watt: String,
    val percent: String,
    val wattsPerKg: String = "",
    val kcal: String = "",
)

/** Direction glyphs of turn fields and banners (12-FEATURE-SPECS §7.2); symbols, not translated text. */
object TurnArrows {
    const val LEFT: String = "←"
    const val RIGHT: String = "→"
    const val UTURN: String = "↶"

    fun of(direction: TurnDirection): String = when (direction) {
        TurnDirection.LEFT -> LEFT
        TurnDirection.RIGHT -> RIGHT
        TurnDirection.UTURN -> UTURN
    }
}

/** Everything a metric needs to produce its text. */
class MetricContext(val live: LiveRide, val format: UnitFormatter, val zone: ZoneId)

/** How a metric's unit is chosen. */
enum class MetricUnit { NONE, SPEED, DISTANCE, SHORT_DISTANCE, ELEVATION, PERCENT, BPM, RPM, WATT, WATTS_PER_KG, KCAL }

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val ONE_DECIMAL = "%.1f"

private fun MetricContext.decimal(value: Double?): String =
    value?.let { String.format(Locale.getDefault(), ONE_DECIMAL, it) } ?: UnitFormatter.NO_VALUE

/**
 * Every metric a data field can show (02-DESIGN §5): label, unit, the sensor it depends on (for the
 * "sensor lost" warning) and how to read it from the live ride. Adding a metric is one entry.
 */
enum class RideMetric(
    @param:StringRes val labelRes: Int,
    private val unitKind: MetricUnit,
    /** Sensor this metric needs; its field turns to warning when that sensor disconnects. */
    val sensor: SensorKind?,
    private val read: MetricContext.() -> String,
) {
    SPEED(R.string.metric_speed, MetricUnit.SPEED, null, { format.speed(live.speedMps) }),
    AVG_SPEED(R.string.metric_avg_speed, MetricUnit.SPEED, null, {
        format.speed(
            live.avgSpeedMps.takeIf {
                live.movingMs >
                    0
            },
        )
    }),
    MAX_SPEED(R.string.metric_max_speed, MetricUnit.SPEED, null, {
        format.speed(
            live.maxSpeedMps.takeIf {
                live.movingMs >
                    0
            },
        )
    }),
    DISTANCE(R.string.metric_distance, MetricUnit.DISTANCE, null, { format.distance(live.distanceM) }),
    MOVING_TIME(R.string.metric_moving_time, MetricUnit.NONE, null, { format.duration(live.movingMs) }),
    ELAPSED_TIME(R.string.metric_elapsed_time, MetricUnit.NONE, null, { format.duration(live.elapsedMs) }),
    CLOCK(R.string.metric_clock, MetricUnit.NONE, null, {
        CLOCK_FORMAT.format(Instant.ofEpochMilli(live.nowMs).atZone(zone))
    }),
    ALTITUDE(R.string.metric_altitude, MetricUnit.ELEVATION, null, { format.elevation(live.elevation.altitudeM) }),
    ASCENT(R.string.metric_ascent, MetricUnit.ELEVATION, null, { format.elevation(live.elevation.ascentM) }),
    DESCENT(R.string.metric_descent, MetricUnit.ELEVATION, null, { format.elevation(live.elevation.descentM) }),
    GRADE(R.string.metric_grade, MetricUnit.PERCENT, null, { format.grade(live.elevation.gradePercent) }),
    HEART_RATE(R.string.metric_heart_rate, MetricUnit.BPM, SensorKind.HEART_RATE, {
        format.integer(live.sensors.heartRateBpm)
    }),
    AVG_HEART_RATE(R.string.metric_avg_heart_rate, MetricUnit.BPM, SensorKind.HEART_RATE, {
        format.integer(live.sensors.avgHeartRateBpm)
    }),
    MAX_HEART_RATE(R.string.metric_max_heart_rate, MetricUnit.BPM, SensorKind.HEART_RATE, {
        format.integer(live.sensors.maxHeartRateBpm)
    }),
    HEART_RATE_ZONE(R.string.metric_heart_rate_zone, MetricUnit.NONE, SensorKind.HEART_RATE, {
        format.integer(live.sensors.heartRateZone)
    }),
    CADENCE(R.string.metric_cadence, MetricUnit.RPM, null, { format.integer(live.sensors.cadenceRpm) }),
    AVG_CADENCE(R.string.metric_avg_cadence, MetricUnit.RPM, null, { format.integer(live.sensors.avgCadenceRpm) }),
    POWER(R.string.metric_power, MetricUnit.WATT, SensorKind.POWER, { format.integer(live.sensors.powerW) }),
    POWER_3S(R.string.metric_power_3s, MetricUnit.WATT, SensorKind.POWER, { format.integer(live.sensors.power3sW) }),
    POWER_10S(R.string.metric_power_10s, MetricUnit.WATT, SensorKind.POWER, { format.integer(live.sensors.power10sW) }),
    AVG_POWER(R.string.metric_avg_power, MetricUnit.WATT, SensorKind.POWER, { format.integer(live.sensors.avgPowerW) }),
    MAX_POWER(R.string.metric_max_power, MetricUnit.WATT, SensorKind.POWER, { format.integer(live.sensors.maxPowerW) }),
    NORMALIZED_POWER(R.string.metric_np, MetricUnit.WATT, SensorKind.POWER, {
        format.integer(live.sensors.normalizedPowerW)
    }),
    POWER_ZONE(R.string.metric_power_zone, MetricUnit.NONE, SensorKind.POWER, {
        format.integer(live.sensors.powerZone)
    }),
    WATTS_PER_KG(R.string.metric_watts_per_kg, MetricUnit.WATTS_PER_KG, SensorKind.POWER, {
        decimal(live.sensors.wattsPerKg)
    }),
    ESTIMATED_POWER(R.string.metric_estimated_power, MetricUnit.WATT, null, {
        format.integer(live.sensors.estimatedPowerW)
    }),
    CALORIES(R.string.metric_calories, MetricUnit.KCAL, null, { format.integer(live.sensors.calories) }),
    LAP_NUMBER(R.string.metric_lap_number, MetricUnit.NONE, null, { format.integer(live.lap?.number) }),
    LAP_TIME(R.string.metric_lap_time, MetricUnit.NONE, null, { format.duration(live.lap?.elapsedMs) }),
    LAP_DISTANCE(R.string.metric_lap_distance, MetricUnit.DISTANCE, null, { format.distance(live.lap?.distanceM) }),
    LAP_AVG_SPEED(
        R.string.metric_lap_avg_speed,
        MetricUnit.SPEED,
        null,
        { format.speed(live.lap?.takeIf { it.movingMs > 0 }?.avgSpeedMps) },
    ),
    LAP_AVG_POWER(R.string.metric_lap_avg_power, MetricUnit.WATT, SensorKind.POWER, {
        format.integer(live.lap?.avgPowerW)
    }),
    LAP_AVG_HR(R.string.metric_lap_avg_hr, MetricUnit.BPM, SensorKind.HEART_RATE, {
        format.integer(live.lap?.avgHeartRateBpm)
    }),
    DISTANCE_REMAINING(R.string.metric_distance_remaining, MetricUnit.DISTANCE, null, {
        format.distance(live.route?.distanceRemainingM)
    }),
    ROUTE_ETA(R.string.metric_route_eta, MetricUnit.NONE, null, { format.duration(live.route?.etaMs) }),
    REMAINING_ASCENT(R.string.metric_remaining_ascent, MetricUnit.ELEVATION, null, {
        format.elevation(live.route?.remainingAscentM)
    }),
    NEXT_TURN(R.string.metric_next_turn, MetricUnit.SHORT_DISTANCE, null, {
        live.route?.nextTurn?.let { "${TurnArrows.of(it.direction)} ${format.shortDistance(it.distanceM)}" }
            ?: UnitFormatter.NO_VALUE
    }),
    ;

    /**
     * Stable id stored in the data page JSON (05-DATA-MODEL §2). Derived from the entry name: renaming an
     * entry drops it from saved pages (they fall back gracefully, see `sanitized`), so rename with care.
     */
    val key: String get() = name.lowercase(Locale.ROOT)

    /** Display text of this metric for [live]. */
    fun value(live: LiveRide, format: UnitFormatter, zone: ZoneId = ZoneId.systemDefault()): String =
        MetricContext(live, format, zone).read()

    /** True when the sensor this metric needs was connected and is now lost (02-DESIGN §3.4). */
    fun isSensorLost(live: LiveRide): Boolean =
        sensor?.let { live.sensors.snapshot.states[it]?.isConnected == false } == true

    /** Unit label shown next to the field label, or null. */
    fun unit(format: UnitFormatter, labels: MetricUnitLabels): String? = when (unitKind) {
        MetricUnit.NONE -> null
        MetricUnit.SPEED -> format.speedUnit
        MetricUnit.DISTANCE -> format.distanceUnit
        MetricUnit.ELEVATION, MetricUnit.SHORT_DISTANCE -> format.elevationUnit
        MetricUnit.PERCENT -> labels.percent
        MetricUnit.BPM -> labels.bpm
        MetricUnit.RPM -> labels.rpm
        MetricUnit.WATT -> labels.watt
        MetricUnit.WATTS_PER_KG -> labels.wattsPerKg
        MetricUnit.KCAL -> labels.kcal
    }
}

/** One swipeable data page: its fields and whether the first field spans the full width. */
data class DataPage(val metrics: List<RideMetric>, val isFirstFullWidth: Boolean = false)

/** Default pages from 05-DATA-MODEL §2 (map page arrives in Phase 3; pages are editable, T2.8). */
object DefaultDataPages {
    val MAIN = DataPage(
        listOf(
            RideMetric.SPEED,
            RideMetric.DISTANCE,
            RideMetric.MOVING_TIME,
            RideMetric.HEART_RATE,
            RideMetric.CADENCE,
            RideMetric.POWER_3S,
            RideMetric.AVG_SPEED,
        ),
        isFirstFullWidth = true,
    )
    val CLIMB = DataPage(listOf(RideMetric.GRADE, RideMetric.ALTITUDE, RideMetric.ASCENT, RideMetric.DESCENT))
    val LAP = DataPage(
        listOf(
            RideMetric.LAP_TIME,
            RideMetric.LAP_DISTANCE,
            RideMetric.LAP_AVG_SPEED,
            RideMetric.LAP_AVG_HR,
            RideMetric.LAP_AVG_POWER,
        ),
        isFirstFullWidth = true,
    )
    val ALL: List<DataPage> = listOf(MAIN, CLIMB, LAP)

    /** Starting point of a page added in the editor. */
    val NEW_PAGE: DataPageLayout = DataPageLayout(listOf(RideMetric.SPEED.key, RideMetric.DISTANCE.key))
}

/** Lookup between stored metric keys and [RideMetric]. */
object RideMetricKeys {
    private val byKey: Map<String, RideMetric> = RideMetric.entries.associateBy { it.key }

    /** Every key this app version can show; used to sanitize stored pages. */
    val ALL: Set<String> = byKey.keys

    fun metricOf(key: String): RideMetric? = byKey[key]
}

/** Stored layout → renderable pages (unknown keys were already removed by `sanitized`). */
fun DataPagesLayout.toDataPages(): List<DataPage> = pages.map { page ->
    DataPage(page.fields.mapNotNull(RideMetricKeys::metricOf), page.isFirstFullWidth)
}

/** Renderable pages → stored layout. */
fun List<DataPage>.toLayout(): DataPagesLayout =
    DataPagesLayout(map { page -> DataPageLayout(page.metrics.map { it.key }, page.isFirstFullWidth) })

/**
 * Fields for sensors the rider does not use show phone metrics instead of "--" (novice test, D-86): with no
 * power meter a power field shows the estimate, other sensor fields the first phone metric not already on
 * the page. A sensor that is paired keeps its field, so a lost signal still shows as lost.
 */
fun List<RideMetric>.withFallbacks(sensorsInUse: Set<SensorKind>): List<RideMetric> {
    val used = toMutableSet()
    return map { metric ->
        if (metric.isAvailable(sensorsInUse)) return@map metric
        val candidates = if (metric.sensor == SensorKind.POWER) POWER_FALLBACKS else PHONE_FALLBACKS
        val substitute = candidates.firstOrNull { it !in used } ?: metric
        used += substitute
        substitute
    }
}

private fun RideMetric.isAvailable(sensorsInUse: Set<SensorKind>): Boolean = when (this) {
    // Cadence comes from a speed/cadence sensor or a power meter.
    RideMetric.CADENCE, RideMetric.AVG_CADENCE ->
        SensorKind.SPEED_CADENCE in sensorsInUse || SensorKind.POWER in sensorsInUse
    else -> sensor == null || sensor in sensorsInUse
}

private val PHONE_FALLBACKS = listOf(
    RideMetric.ASCENT,
    RideMetric.MAX_SPEED,
    RideMetric.ELAPSED_TIME,
    RideMetric.CALORIES,
    RideMetric.CLOCK,
)
private val POWER_FALLBACKS = listOf(RideMetric.ESTIMATED_POWER) + PHONE_FALLBACKS
