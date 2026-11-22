package com.ridecomp.core.engine.geo

import com.ridecomp.core.model.LocationFix
import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceTrackerRouteGapTest {
    private val meterInLatDeg = 1.0 / 111_195.0

    private fun fix(timeS: Long, northM: Double) =
        LocationFix(timeS * 1_000, -7.0 + northM * meterInLatDeg, 110.0, null, 5.0, 5.0)

    private fun trackerWithRoute(routeM: Double?) = DistanceTracker(alongRoute = { _, _, _, _ -> routeM })

    @Test
    fun `after a gap the route distance is used when it is plausible`() {
        val tracker = trackerWithRoute(300.0)
        tracker.add(fix(0, 0.0), isCounting = true)
        // 60 s without a fix, 200 m straight across, 300 m along the route.
        assertEquals(300.0, tracker.add(fix(60, 200.0), isCounting = true))
    }

    @Test
    fun `without a gap, or with an implausible route distance, the straight line is used`() {
        val noGap = trackerWithRoute(300.0)
        noGap.add(fix(0, 0.0), isCounting = true)
        assertEquals(5.0, noGap.add(fix(1, 5.0), isCounting = true), absoluteTolerance = 0.01)

        val tooLong = trackerWithRoute(1_000.0)
        tooLong.add(fix(0, 0.0), isCounting = true)
        assertEquals(200.0, tooLong.add(fix(60, 200.0), isCounting = true), absoluteTolerance = 0.5)

        val offRoute = trackerWithRoute(null)
        offRoute.add(fix(0, 0.0), isCounting = true)
        assertEquals(200.0, offRoute.add(fix(60, 200.0), isCounting = true), absoluteTolerance = 0.5)
    }
}
