package com.ridecomp.feature.ride.model

import androidx.compose.runtime.Immutable
import com.ridecomp.core.engine.geo.GeoMath
import com.ridecomp.core.engine.map.MapTiles
import com.ridecomp.core.engine.map.TrailPoint
import com.ridecomp.core.engine.route.RoutePoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * What the map page draws (D-90): online tiles or offline regions when there is a map, and on a ride
 * without a route the line ridden so far ([trail], with distances so the breadcrumb can draw it).
 */
@Immutable
data class RideMapView(
    val isOnline: Boolean = false,
    val regionPaths: ImmutableList<String> = persistentListOf(),
    val trail: ImmutableList<RoutePoint> = persistentListOf(),
) {
    val hasMap: Boolean get() = isOnline || regionPaths.isNotEmpty()

    companion object {
        fun of(tiles: MapTiles, trail: List<TrailPoint>): RideMapView =
            RideMapView(tiles.isOnline, tiles.regionPaths.toImmutableList(), trail.withDistances())

        private fun List<TrailPoint>.withDistances(): ImmutableList<RoutePoint> {
            var cumulativeM = 0.0
            return mapIndexed { index, point ->
                if (index > 0) {
                    val previous = this[index - 1]
                    cumulativeM += GeoMath.distanceM(previous.latDeg, previous.lonDeg, point.latDeg, point.lonDeg)
                }
                RoutePoint(point.latDeg, point.lonDeg, elevationM = null, cumDistanceM = cumulativeM)
            }.toImmutableList()
        }
    }
}
