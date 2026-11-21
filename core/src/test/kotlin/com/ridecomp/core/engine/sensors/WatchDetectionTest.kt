package com.ridecomp.core.engine.sensors

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchDetectionTest {
    @Test
    fun `watches seen in the device tests are recognised`() {
        // Given the names from smartwatch-compat.md (T12.1) and the T12.2 report
        val names = listOf("HUAWEI WATCH HR-E57", "Aolon GTR3(ID-A05B)")

        // Then both are watches
        names.forEach { assertTrue(isLikelyWatch(it), it) }
    }

    @Test
    fun `brand watch lines and bands are recognised regardless of case`() {
        listOf(
            "Forerunner 265",
            "fenix 7",
            "Polar Pacer Pro 1A2B3C4D",
            "COROS PACE 3 ABC",
            "Suunto Race S",
            "Amazfit Balance",
            "Xiaomi Smart Band 8",
        ).forEach { assertTrue(isLikelyWatch(it), it) }
    }

    @Test
    fun `chest straps and bike sensors from the same brands are not watches`() {
        listOf(
            "HRM-Dual:123456",
            "Polar H10 1A2B3C4D",
            "Polar Verity Sense 1A2B",
            "TICKR 1234",
            "COROS HRM",
            "Amazfit Helio Strap",
            "KICKR",
            "Wahoo CADENCE",
        ).forEach { assertFalse(isLikelyWatch(it), it) }
    }

    @Test
    fun `a missing or blank name is not a watch`() {
        assertFalse(isLikelyWatch(null))
        assertFalse(isLikelyWatch("   "))
    }
}
