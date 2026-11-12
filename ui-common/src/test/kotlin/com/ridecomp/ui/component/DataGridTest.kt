package com.ridecomp.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class DataGridTest {
    @Test
    fun `layouts follow the design table`() {
        assertEquals(emptyList(), gridRows(0, false))
        assertEquals(listOf(listOf(0)), gridRows(1, false))
        assertEquals(listOf(listOf(0), listOf(1)), gridRows(2, false))
        assertEquals(listOf(listOf(0), listOf(1, 2)), gridRows(3, false))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), gridRows(4, false))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5)), gridRows(6, false))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5), listOf(6, 7)), gridRows(8, false))
    }

    @Test
    fun `first field can span the full width`() {
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), gridRows(6, true))
    }
}
