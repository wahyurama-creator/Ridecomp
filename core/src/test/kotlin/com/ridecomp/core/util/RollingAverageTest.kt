package com.ridecomp.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RollingAverageTest {
    @Test
    fun `empty window has no average`() {
        val average = RollingAverage(3)

        assertNull(average.averageOrNull())
        assertNull(average.fullAverageOrNull())
    }

    @Test
    fun `partial window averages available samples but has no full average`() {
        val average = RollingAverage(3)

        average.add(100.0)
        average.add(200.0)

        assertEquals(150.0, average.averageOrNull())
        assertNull(average.fullAverageOrNull())
    }

    @Test
    fun `full window drops the oldest sample`() {
        val average = RollingAverage(3)

        listOf(100.0, 200.0, 300.0, 400.0).forEach(average::add)

        assertTrue(average.isFull)
        assertEquals(300.0, average.averageOrNull())
        assertEquals(300.0, average.fullAverageOrNull())
    }

    @Test
    fun `long stream stays accurate`() {
        val average = RollingAverage(30)

        repeat(36_000) { average.add(250.0) }

        assertEquals(250.0, requireNotNull(average.averageOrNull()), absoluteTolerance = 1e-9)
    }

    @Test
    fun `clear resets the window`() {
        val average = RollingAverage(2)
        listOf(1.0, 2.0).forEach(average::add)

        average.clear()

        assertEquals(0, average.count)
        assertNull(average.averageOrNull())
    }

    @Test
    fun `window size must be positive`() {
        assertFailsWith<IllegalArgumentException> { RollingAverage(0) }
    }
}
