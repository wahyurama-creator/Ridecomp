package com.ridecomp.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TopLevelDestinationTest {
    @Test
    fun `bottom bar keeps exactly five tabs`() {
        assertEquals(5, TopLevelDestination.entries.size)
    }

    @Test
    fun `routes are unique`() {
        val routes = TopLevelDestination.entries.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `fromRoute resolves known routes and ignores unknown ones`() {
        TopLevelDestination.entries.forEach { assertEquals(it, TopLevelDestination.fromRoute(it.route)) }
        assertNull(TopLevelDestination.fromRoute("unknown"))
        assertNull(TopLevelDestination.fromRoute(null))
    }

    @Test
    fun `app starts on home`() {
        assertEquals(TopLevelDestination.HOME, TopLevelDestination.START)
    }
}
