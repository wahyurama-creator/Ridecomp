package com.ridecomp.core.engine.route

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ridden part of the route on the tile map (T3.5). */
class RiddenUntilTest {
    private val line = listOf(
        RoutePoint(latDeg = 0.0, lonDeg = 0.0, elevationM = null, cumDistanceM = 0.0),
        RoutePoint(latDeg = 0.0, lonDeg = 1.0, elevationM = null, cumDistanceM = 1000.0),
        RoutePoint(latDeg = 1.0, lonDeg = 1.0, elevationM = null, cumDistanceM = 2000.0),
    )

    @Test
    fun `ends between vertices at the distance ridden`() {
        val ridden = line.riddenUntil(distanceAlongM = 250.0)

        assertEquals(2, ridden.size)
        assertEquals(0.25, ridden.last().lonDeg, 1e-9)
        assertEquals(250.0, ridden.last().cumDistanceM)
    }

    @Test
    fun `before the start nothing is ridden and past the end the whole line is`() {
        assertTrue(line.riddenUntil(distanceAlongM = -5.0).isEmpty())
        assertEquals(line, line.riddenUntil(distanceAlongM = 2500.0))
        assertEquals(3, line.riddenUntil(distanceAlongM = 1500.0).size)
    }
}
