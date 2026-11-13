package com.ridecomp.feature.ride.model

import androidx.annotation.StringRes
import com.ridecomp.feature.ride.R

/** Groups of the metric picker (13-UI-SCREENS #24): Kecepatan · Cadence · Power · Heart rate · … */
enum class MetricCategory(@param:StringRes val labelRes: Int, val metrics: List<RideMetric>) {
    SPEED(
        R.string.metric_category_speed,
        listOf(RideMetric.SPEED, RideMetric.AVG_SPEED, RideMetric.MAX_SPEED, RideMetric.LAP_AVG_SPEED),
    ),
    DISTANCE_TIME(
        R.string.metric_category_distance_time,
        listOf(
            RideMetric.DISTANCE,
            RideMetric.MOVING_TIME,
            RideMetric.ELAPSED_TIME,
            RideMetric.CLOCK,
            RideMetric.CALORIES,
        ),
    ),
    CADENCE(R.string.metric_category_cadence, listOf(RideMetric.CADENCE, RideMetric.AVG_CADENCE)),
    POWER(
        R.string.metric_category_power,
        listOf(
            RideMetric.POWER,
            RideMetric.POWER_3S,
            RideMetric.POWER_10S,
            RideMetric.AVG_POWER,
            RideMetric.MAX_POWER,
            RideMetric.NORMALIZED_POWER,
            RideMetric.POWER_ZONE,
            RideMetric.WATTS_PER_KG,
            RideMetric.ESTIMATED_POWER,
            RideMetric.LAP_AVG_POWER,
        ),
    ),
    HEART_RATE(
        R.string.metric_category_heart_rate,
        listOf(
            RideMetric.HEART_RATE,
            RideMetric.AVG_HEART_RATE,
            RideMetric.MAX_HEART_RATE,
            RideMetric.HEART_RATE_ZONE,
            RideMetric.LAP_AVG_HR,
        ),
    ),
    ELEVATION(
        R.string.metric_category_elevation,
        listOf(RideMetric.ALTITUDE, RideMetric.ASCENT, RideMetric.DESCENT, RideMetric.GRADE),
    ),
    LAP(R.string.metric_category_lap, listOf(RideMetric.LAP_NUMBER, RideMetric.LAP_TIME, RideMetric.LAP_DISTANCE)),
    NAVIGATION(
        R.string.metric_category_navigation,
        listOf(RideMetric.DISTANCE_REMAINING, RideMetric.ROUTE_ETA, RideMetric.REMAINING_ASCENT, RideMetric.NEXT_TURN),
    ),
    ;

    companion object {
        fun of(metric: RideMetric?): MetricCategory = entries.firstOrNull { metric in it.metrics } ?: SPEED
    }
}
