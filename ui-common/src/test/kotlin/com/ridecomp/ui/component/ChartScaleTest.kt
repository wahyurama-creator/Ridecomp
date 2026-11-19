package com.ridecomp.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Round axis numbers of RideChart (T11.1). */
class ChartScaleTest {
    @Test
    fun `steps are 1, 2 or 5 times a power of ten`() {
        assertEquals(10.0, ChartScale.niceStep(36.0, 4))
        assertEquals(2.0, ChartScale.niceStep(7.0, 4))
        assertEquals(0.5, ChartScale.niceStep(1.6, 4))
        assertEquals(50.0, ChartScale.niceStep(180.0, 4))
        assertEquals(1.0, ChartScale.niceStep(0.0, 4))
    }

    @Test
    fun `speed starts at zero and ends on a round number above the maximum`() {
        val axis = ChartScale.yAxis(dataMin = 3.2, dataMax = 47.6, scale = ChartYScale.FROM_ZERO)

        assertEquals(0.0, axis.min)
        assertEquals(50.0, axis.max)
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0), axis.ticks)
        assertEquals(0, axis.decimals)
    }

    @Test
    fun `elevation follows the data range so the shape shows`() {
        val axis = ChartScale.yAxis(dataMin = 7.4, dataMax = 14.9, scale = ChartYScale.DATA_RANGE)

        assertEquals(6.0, axis.min)
        assertEquals(16.0, axis.max)
        assertTrue(axis.ticks.all { it % axis.step == 0.0 })
        assertTrue(axis.min <= 7.4 && axis.max >= 14.9)
    }

    @Test
    fun `the elevation line keeps room above and below`() {
        // 120–132 m would fit 120..135 exactly, with the low point on the bottom edge.
        val axis = ChartScale.yAxis(dataMin = 120.0, dataMax = 132.0, scale = ChartYScale.DATA_RANGE)
        assertTrue(axis.min < 120.0 && axis.max > 132.0)
        assertEquals(115.0, axis.min)
        assertEquals(135.0, axis.max)

        // Data near sea level does not get a negative axis.
        assertEquals(0.0, ChartScale.yAxis(dataMin = 0.5, dataMax = 30.0, scale = ChartYScale.DATA_RANGE).min)
    }

    @Test
    fun `a flat line still gets a band, small ranges keep decimals`() {
        val flat = ChartScale.yAxis(dataMin = 140.0, dataMax = 140.0, scale = ChartYScale.DATA_RANGE)
        assertTrue(flat.max > flat.min)
        assertTrue(flat.min <= 140.0 && flat.max >= 140.0)

        val small = ChartScale.yAxis(dataMin = 0.2, dataMax = 1.7, scale = ChartYScale.DATA_RANGE)
        assertEquals(1, small.decimals)
    }

    @Test
    fun `distance labels are round kilometres after the start`() {
        assertEquals(listOf(2.0, 4.0, 6.0, 8.0, 10.0), ChartScale.xTicks(0.0, 10.99))
        assertEquals(listOf(50.0, 100.0, 150.0), ChartScale.xTicks(0.0, 150.0))
        assertEquals(listOf(10.0, 20.0, 30.0, 40.0), ChartScale.xTicks(0.0, 42.0))
        assertEquals(listOf(0.5, 1.0, 1.5), ChartScale.xTicks(0.0, 1.5))
        assertEquals(emptyList(), ChartScale.xTicks(0.0, 0.0))
    }

    @Test
    fun `decimals follow the step`() {
        assertEquals(0, ChartScale.decimalsFor(5.0))
        assertEquals(1, ChartScale.decimalsFor(0.5))
        assertEquals(1, ChartScale.decimalsFor(0.2))
        assertEquals(2, ChartScale.decimalsFor(0.05))
    }
}
