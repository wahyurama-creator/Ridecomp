package com.ridecomp.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RollingMedianTest {
    @Test
    fun `a single spike does not move the median`() {
        val median = RollingMedian(5)
        listOf(10.0, 11.0, 500.0, 10.0, 12.0).forEach(median::add)
        assertEquals(11.0, median.medianOrNull())
    }

    @Test
    fun `old samples leave the window and even counts average the middle two`() {
        val median = RollingMedian(3)
        assertNull(median.medianOrNull())
        listOf(1.0, 2.0).forEach(median::add)
        assertEquals(1.5, median.medianOrNull())
        listOf(9.0, 10.0, 11.0).forEach(median::add)
        assertEquals(10.0, median.medianOrNull())
        median.clear()
        assertNull(median.medianOrNull())
    }
}
