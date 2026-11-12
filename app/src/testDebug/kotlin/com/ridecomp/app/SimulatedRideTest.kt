package com.ridecomp.app

import com.ridecomp.core.engine.geo.GeoMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulatedRideTest {
    @Test
    fun `the simulated ride moves, then stops like at a traffic light`() {
        assertTrue(SimulatedRide.speedMps(10) > 4.0)
        assertEquals(0.0, SimulatedRide.speedMps(SimulatedRide.RIDE_S + 5))
        assertTrue(SimulatedRide.speedMps(SimulatedRide.RIDE_S + SimulatedRide.STOP_S + 1) > 0.0)
    }

    @Test
    fun `fixes stay on the loop around the centre`() {
        listOf(0.0, 500.0, 1_234.0, 3_000.0).forEach { distance ->
            val fix = SimulatedRide.fixAt(distance, speedMps = 7.0, timeMs = 0)
            val radius = GeoMath.distanceM(
                SimulatedRide.CENTER_LAT_DEG,
                SimulatedRide.CENTER_LON_DEG,
                fix.latDeg,
                fix.lonDeg,
            )
            assertEquals(SimulatedRide.RADIUS_M, radius, absoluteTolerance = 5.0)
        }
    }
}
