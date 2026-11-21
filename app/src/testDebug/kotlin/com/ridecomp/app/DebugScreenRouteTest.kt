package com.ridecomp.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebugScreenRouteTest {
    @Test
    fun `route names with one argument are accepted`() {
        listOf("settings", "pre_ride", "help/WATCHES", "activity/12").forEach {
            assertEquals(it, DebugScreenRoute.parse(it))
        }
    }

    @Test
    fun `anything else is ignored`() {
        listOf(null, "", "Settings", "help/A/B", "activity/1?x=2", "../files", "a".repeat(33), "settings ")
            .forEach { assertNull(DebugScreenRoute.parse(it)) }
    }
}
