package com.ridecomp.feature.ride.model

import androidx.compose.runtime.Immutable
import com.ridecomp.core.engine.geo.GeoMath
import com.ridecomp.core.engine.map.BackToStart
import com.ridecomp.core.engine.map.MapTiles
import com.ridecomp.core.engine.map.TrailPoint
import com.ridecomp.core.engine.route.RoutePoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * What the map page draws (D-90): online tiles or offline regions when there is a map, the line ridden
 * so far ([trail], with distances so the breadcrumb can draw it) and the way back to the start (T7.2).
 */
@Immutable
data class RideMapView(
    val isOnline: Boolean = false,
    /** Offline: the tiles seen online before, from the cache (T11.11). */
    val isCached: Boolean = false,
    val regionPaths: ImmutableList<String> = persistentListOf(),
    val trail: ImmutableList<RoutePoint> = persistentListOf(),
    /** "Back to start" is on (T7.2); [backToStart] is null until there is a position. */
    val isBackToStart: Boolean = false,
    val backToStart: BackToStart? = null,
) {
    val hasMap: Boolean get() = isOnline || isCached || regionPaths.isNotEmpty()

    companion object {
        fun of(tiles: MapTiles, trail: List<TrailPoint>): RideMapView =
            RideMapView(tiles.isOnline, tiles.hasCache, tiles.regionPaths.toImmutableList(), trail.withDistances())

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
