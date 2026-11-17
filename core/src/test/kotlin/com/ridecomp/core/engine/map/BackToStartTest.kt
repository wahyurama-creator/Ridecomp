package com.ridecomp.core.engine.map

import com.ridecomp.core.engine.geo.GeoMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackToStartTest {
    /** An L-shaped ride: 1 km north, then 1 km east. */
    private val trail = buildList {
        repeat(11) { add(TrailPoint(-7.0 + it * STEP_DEG, 110.0)) }
        repeat(10) { add(TrailPoint(-7.0 + 10 * STEP_DEG, 110.0 + (it + 1) * STEP_DEG)) }
    }

    @Test
    fun `back along the ridden line is longer than the straight line`() {
        val end = trail.last()

        val back = BackToStart.of(trail, end.latDeg, end.lonDeg)!!

        val legM = GeoMath.distanceM(-7.0, 110.0, -7.0 + 10 * STEP_DEG, 110.0)
        assertEquals(legM * 2, back.alongTrailM, legM * TOLERANCE)
        assertEquals(legM * Math.sqrt(2.0), back.straightM, legM * TOLERANCE)
        // Start is to the south-west.
        assertTrue(back.bearingDeg in 220.0..230.0)
        assertFalse(back.isArrived)
    }

    @Test
    fun `off the line, the gap to the nearest point is added`() {
        val corner = trail[10]
        val aside = BackToStart.of(trail, corner.latDeg, corner.lonDeg + STEP_DEG / 2)!!
        val onIt = BackToStart.of(trail, corner.latDeg, corner.lonDeg)!!

        assertTrue(aside.alongTrailM > onIt.alongTrailM)
    }

    @Test
    fun `near the start the rider has arrived, without a line or position there is nothing`() {
        assertTrue(BackToStart.of(trail, -7.0, 110.0001)!!.isArrived)
        assertNull(BackToStart.of(emptyList(), -7.0, 110.0))
        assertNull(BackToStart.of(trail, null, 110.0))
    }

    @Test
    fun `bearing points the right way`() {
        assertEquals(0.0, GeoMath.bearingDeg(-7.0, 110.0, -6.9, 110.0), 0.01)
        assertEquals(90.0, GeoMath.bearingDeg(-7.0, 110.0, -7.0, 110.1), 0.1)
        assertEquals(180.0, GeoMath.bearingDeg(-7.0, 110.0, -7.1, 110.0), 0.01)
    }

    private companion object {
        /** About 110 m. */
        const val STEP_DEG = 0.001
        const val TOLERANCE = 0.01
    }
}
