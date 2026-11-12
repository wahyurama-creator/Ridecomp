package com.ridecomp.feature.ride.model

import androidx.annotation.StringRes
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Unit symbols that come from resources (not part of [UnitFormatter]). */
data class MetricUnitLabels(val bpm: String, val rpm: String, val watt: String, val percent: String)

/** Everything a metric needs to produce its text. */
class MetricContext(val live: LiveRide, val format: UnitFormatter, val zone: ZoneId)

/** How a metric's unit is chosen. */
enum class MetricUnit { NONE, SPEED, DISTANCE, ELEVATION, PERCENT, BPM, RPM, WATT }

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Every metric a data field can show (02-DESIGN §5): label, unit and how to read it from the live ride —
 * one place for the ride screen, the summary and the data page editor (T2.8). Adding a metric is one line.
 */
enum class RideMetric(
    @param:StringRes val labelRes: Int,
    private val unitKind: MetricUnit,
    private val read: MetricContext.() -> String,
) {
    SPEED(R.string.metric_speed, MetricUnit.SPEED, { format.speed(live.speedMps) }),
    AVG_SPEED(R.string.metric_avg_speed, MetricUnit.SPEED, {
        format.speed(live.avgSpeedMps.takeIf { live.movingMs > 0 })
    }),
    MAX_SPEED(R.string.metric_max_speed, MetricUnit.SPEED, {
        format.speed(live.maxSpeedMps.takeIf { live.movingMs > 0 })
    }),
    DISTANCE(R.string.metric_distance, MetricUnit.DISTANCE, { format.distance(live.distanceM) }),
    MOVING_TIME(R.string.metric_moving_time, MetricUnit.NONE, { format.duration(live.movingMs) }),
    ELAPSED_TIME(R.string.metric_elapsed_time, MetricUnit.NONE, { format.duration(live.elapsedMs) }),
    CLOCK(R.string.metric_clock, MetricUnit.NONE, {
        CLOCK_FORMAT.format(Instant.ofEpochMilli(live.nowMs).atZone(zone))
    }),
    ALTITUDE(R.string.metric_altitude, MetricUnit.ELEVATION, { format.elevation(live.elevation.altitudeM) }),
    ASCENT(R.string.metric_ascent, MetricUnit.ELEVATION, { format.elevation(live.elevation.ascentM) }),
    DESCENT(R.string.metric_descent, MetricUnit.ELEVATION, { format.elevation(live.elevation.descentM) }),
    GRADE(R.string.metric_grade, MetricUnit.PERCENT, { format.grade(live.elevation.gradePercent) }),

    // TODO(T2.5): sensor metrics are filled when BLE sensors arrive.
    HEART_RATE(R.string.metric_heart_rate, MetricUnit.BPM, { UnitFormatter.NO_VALUE }),
    CADENCE(R.string.metric_cadence, MetricUnit.RPM, { UnitFormatter.NO_VALUE }),
    POWER_3S(R.string.metric_power_3s, MetricUnit.WATT, { UnitFormatter.NO_VALUE }),
    LAP_NUMBER(R.string.metric_lap_number, MetricUnit.NONE, { format.integer(live.lap?.number) }),
    LAP_TIME(R.string.metric_lap_time, MetricUnit.NONE, { format.duration(live.lap?.elapsedMs) }),
    LAP_DISTANCE(R.string.metric_lap_distance, MetricUnit.DISTANCE, { format.distance(live.lap?.distanceM) }),
    LAP_AVG_SPEED(
        R.string.metric_lap_avg_speed,
        MetricUnit.SPEED,
        { format.speed(live.lap?.takeIf { it.movingMs > 0 }?.avgSpeedMps) },
    ),
    LAP_AVG_POWER(R.string.metric_lap_avg_power, MetricUnit.WATT, { UnitFormatter.NO_VALUE }),
    LAP_AVG_HR(R.string.metric_lap_avg_hr, MetricUnit.BPM, { UnitFormatter.NO_VALUE }),
    ;

    /** Display text of this metric for [live]. */
    fun value(live: LiveRide, format: UnitFormatter, zone: ZoneId = ZoneId.systemDefault()): String =
        MetricContext(live, format, zone).read()

    /** Unit label shown next to the field label, or null. */
    fun unit(format: UnitFormatter, labels: MetricUnitLabels): String? = when (unitKind) {
        MetricUnit.NONE -> null
        MetricUnit.SPEED -> format.speedUnit
        MetricUnit.DISTANCE -> format.distanceUnit
        MetricUnit.ELEVATION -> format.elevationUnit
        MetricUnit.PERCENT -> labels.percent
        MetricUnit.BPM -> labels.bpm
        MetricUnit.RPM -> labels.rpm
        MetricUnit.WATT -> labels.watt
    }
}

/** One swipeable data page: its fields and whether the first field spans the full width. */
data class DataPage(val metrics: List<RideMetric>, val isFirstFullWidth: Boolean = false)

/** Default pages from 05-DATA-MODEL §2 (map page arrives in Phase 3, editor in T2.8). */
object DefaultDataPages {
    val MAIN = DataPage(
        listOf(
            RideMetric.SPEED,
            RideMetric.DISTANCE,
            RideMetric.MOVING_TIME,
            RideMetric.HEART_RATE,
            RideMetric.CADENCE,
            RideMetric.POWER_3S,
        ),
        isFirstFullWidth = true,
    )
    val CLIMB = DataPage(listOf(RideMetric.GRADE, RideMetric.ALTITUDE, RideMetric.ASCENT, RideMetric.DESCENT))
    val LAP = DataPage(
        listOf(
            RideMetric.LAP_TIME,
            RideMetric.LAP_DISTANCE,
            RideMetric.LAP_AVG_SPEED,
            RideMetric.LAP_AVG_POWER,
            RideMetric.LAP_AVG_HR,
            RideMetric.CLOCK,
        ),
    )
    val ALL: List<DataPage> = listOf(MAIN, CLIMB, LAP)
}
