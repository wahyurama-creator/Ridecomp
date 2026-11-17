package com.ridecomp.core.engine.map

import com.ridecomp.core.engine.geo.GeoMath

/** Way back to where the ride started (T7.2, D-92). */
public data class BackToStart(
    /** Back along the line already ridden: from the nearest point of it, then the line to its start. */
    val alongTrailM: Double,
    /** As the crow flies. */
    val straightM: Double,
    /** Direction of the start, degrees clockwise from north. */
    val bearingDeg: Double,
    val start: TrailPoint,
) {
    val isArrived: Boolean get() = straightM <= BackToStartConfig.ARRIVED_M

    public companion object {
        /**
         * Null without a position or a ridden line. The nearest trail point is searched over the whole
         * line (bounded by [TrailConfig.MAX_POINTS]): CPU work, call off the main thread.
         */
        public fun of(trail: List<TrailPoint>, latDeg: Double?, lonDeg: Double?): BackToStart? {
            val start = trail.firstOrNull()
            if (start == null || latDeg == null || lonDeg == null) return null
            var nearestIndex = 0
            var nearestM = Double.MAX_VALUE
            trail.forEachIndexed { index, point ->
                val distance = GeoMath.distanceM(latDeg, lonDeg, point.latDeg, point.lonDeg)
                if (distance < nearestM) {
                    nearestM = distance
                    nearestIndex = index
                }
            }
            var backM = nearestM
            for (index in nearestIndex downTo 1) {
                val from = trail[index]
                val to = trail[index - 1]
                backM += GeoMath.distanceM(from.latDeg, from.lonDeg, to.latDeg, to.lonDeg)
            }
            return BackToStart(
                alongTrailM = backM,
                straightM = GeoMath.distanceM(latDeg, lonDeg, start.latDeg, start.lonDeg),
                bearingDeg = GeoMath.bearingDeg(latDeg, lonDeg, start.latDeg, start.lonDeg),
                start = start,
            )
        }
    }
}

public object BackToStartConfig {
    /** Within this of the start the rider is back (about GPS accuracy). */
    public const val ARRIVED_M: Double = 30.0
}
