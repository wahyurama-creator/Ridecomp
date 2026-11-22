package com.ridecomp.core.engine.geo

/**
 * Distance along the followed route between two positions, or null when either is off the route or the
 * second is behind the first (D-150). Used to measure a stretch ridden without GPS.
 */
public fun interface AlongRoute {
    public fun distanceM(fromLatDeg: Double, fromLonDeg: Double, toLatDeg: Double, toLonDeg: Double): Double?
}
