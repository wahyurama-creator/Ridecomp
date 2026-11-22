package com.ridecomp.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteGapsTest {
    private val line = (0..5).map { MapPoint(-7.0 + it * 0.001, 110.0) }

    @Test
    fun `the line is cut at each gap and the gap becomes a straight guess`() {
        val parts = line.splitAtGaps(listOf(1, 3))
        assertEquals(listOf(line.subList(0, 2), line.subList(2, 4), line.subList(4, 6)), parts.withFix)
        assertEquals(listOf(listOf(line[1], line[2]), listOf(line[3], line[4])), parts.guessed)
    }

    @Test
    fun `without gaps or with gaps out of range the line stays whole`() {
        assertEquals(listOf(line), line.splitAtGaps(emptyList()).withFix)
        assertEquals(listOf(line), line.splitAtGaps(listOf(5, -1)).withFix)
    }
}
