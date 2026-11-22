package com.ridecomp.core.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteGapsTest {
    private fun fix(m: Double) = ChartPoint(m, null, null, null, null, null, latDeg = -7.0, lonDeg = 110.0)

    private fun lost(m: Double) = ChartPoint(m, null, null, null, null, null)

    @Test
    fun `a lost signal between two fixes is a gap after the earlier fix`() {
        val points = listOf(fix(0.0), fix(10.0), lost(20.0), lost(30.0), fix(40.0), fix(50.0), lost(60.0), fix(70.0))
        // Fix indices: 0, 1, [gap] 2, 3, [gap] 4.
        assertEquals(listOf(1, 3), points.routeGaps())
    }

    @Test
    fun `no fix yet at the start and none at the end are not gaps`() {
        val points = listOf(lost(0.0), lost(10.0), fix(20.0), fix(30.0), lost(40.0))
        assertEquals(emptyList(), points.routeGaps())
    }
}
