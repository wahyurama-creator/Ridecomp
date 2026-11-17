package com.ridecomp.core.engine.geo

import kotlin.math.asin
import kotlin.math.atan2
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
    private const val FULL_TURN_DEG: Double = 360.0

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

    /** Initial bearing from point 1 to point 2, degrees clockwise from north in 0..360. */
    public fun bearingDeg(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Double {
        val lat1 = Math.toRadians(lat1Deg)
        val lat2 = Math.toRadians(lat2Deg)
        val dLon = Math.toRadians(lon2Deg - lon1Deg)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + FULL_TURN_DEG) % FULL_TURN_DEG
    }

    /** True when the coordinate is finite and inside the valid WGS84 range (SEC-IN-02). */
    public fun isValidCoordinate(latDeg: Double, lonDeg: Double): Boolean = latDeg.isFinite() &&
        lonDeg.isFinite() &&
        latDeg in -MAX_LATITUDE_DEG..MAX_LATITUDE_DEG &&
        lonDeg in -MAX_LONGITUDE_DEG..MAX_LONGITUDE_DEG
}
