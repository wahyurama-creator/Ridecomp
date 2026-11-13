package com.ridecomp.feature.ride.model

import androidx.compose.runtime.Immutable
import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.route.PolylineSimplifier
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.util.downsample
import com.ridecomp.ui.component.ChartValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The followed route as drawn on the ride screen: simplified line and elevation profile (T3.8, T3.9). */
@Immutable
data class RouteView(
    val line: ImmutableList<RoutePoint> = persistentListOf(),
    /** Elevation vs distance in display units; empty when the route has no elevation. */
    val elevation: ImmutableList<ChartValue> = persistentListOf(),
    val distanceDivisor: Double = Units.METERS_PER_KM,
) {
    val hasElevation: Boolean get() = elevation.isNotEmpty()

    companion object {
        /** CPU work (simplification, conversion): call off the main thread. */
        fun of(points: List<RoutePoint>, units: UnitSystem): RouteView {
            val isMetric = units == UnitSystem.METRIC
            val distanceDivisor = if (isMetric) Units.METERS_PER_KM else Units.METERS_PER_MILE
            val elevationDivisor = if (isMetric) 1.0 else Units.METERS_PER_FOOT
            val elevation = if (points.any { it.elevationM != null }) {
                points.downsample(ActivityConfig.CHART_MAX_POINTS)
                    .map { ChartValue(it.cumDistanceM / distanceDivisor, it.elevationM?.div(elevationDivisor)) }
            } else {
                emptyList()
            }
            return RouteView(
                line = PolylineSimplifier.simplify(points).toImmutableList(),
                elevation = elevation.toImmutableList(),
                distanceDivisor = distanceDivisor,
            )
        }
    }
}
