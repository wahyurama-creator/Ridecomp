package com.ridecomp.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneMaskTest {
    @Test
    fun `indonesian numbers show the local start and the last four digits`() {
        assertEquals("0812 •••• 4521", PhoneMask.mask("+6281234564521"))
        assertEquals("0812 •••• 4521", PhoneMask.mask("081234564521"))
    }

    @Test
    fun `short and foreign numbers never show more than half`() {
        assertEquals("•••• 5678", PhoneMask.mask("12345678"))
        assertEquals("4420 •••• 0000", PhoneMask.mask("+44 20 7946 0000"))
    }
}
