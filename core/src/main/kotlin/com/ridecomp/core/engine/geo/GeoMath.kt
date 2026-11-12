package com.ridecomp.core.engine.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Geodesy helpers on a spherical Earth (05-DATA-MODEL §3.1). */
public object GeoMath {
    /** Mean Earth radius used by the haversine formula. */
    public const val EARTH_RADIUS_M: Double = 6_371_000.0
    public const val MAX_LATITUDE_DEG: Double = 90.0
    public const val MAX_LONGITUDE_DEG: Double = 180.0
    private const val HALF: Double = 0.5

    /** Great-circle distance in meters between two WGS84 points (haversine). */
    public fun distanceM(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Double {
        val lat1 = Math.toRadians(lat1Deg)
        val lat2 = Math.toRadians(lat2Deg)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(lon2Deg - lon1Deg)
        val sinLat = sin(dLat * HALF)
        val sinLon = sin(dLon * HALF)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** True when the coordinate is finite and inside the valid WGS84 range (SEC-IN-02). */
    public fun isValidCoordinate(latDeg: Double, lonDeg: Double): Boolean = latDeg.isFinite() &&
        lonDeg.isFinite() &&
        latDeg in -MAX_LATITUDE_DEG..MAX_LATITUDE_DEG &&
        lonDeg in -MAX_LONGITUDE_DEG..MAX_LONGITUDE_DEG
}
