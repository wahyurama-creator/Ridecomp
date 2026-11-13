package com.ridecomp.core.engine.route

import com.ridecomp.core.config.Units
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 12-FEATURE-SPECS §6.2, §7.2: when turn and climb alerts fire, once each, and what the rider sees. */
class ClimbTurnTrackerTest {
    private val climb = RouteClimb(
        index = 0,
        startDistanceM = 2_000.0,
        endDistanceM = 2_400.0,
        gainM = 24.0,
        avgGradePercent = 6.0,
        maxGradePercent = 8.0,
        gradeProfilePercent = listOf(4.0, 6.0, 8.0, 6.0),
    )
    private val turn = RouteTurn(1_000.0, -90.0, TurnDirection.LEFT, TurnSeverity.NORMAL)
    private val slowMps = Units.kmhToMps(20.0)

    @Test
    fun `a turn is announced far then near, each once`() {
        val tracker = ClimbTurnTracker(emptyList(), listOf(turn))

        val before = tracker.update(800.0, slowMps, isOffRoute = false)
        assertNull(before.alert)
        assertFalse(before.nextTurn?.isAnnounced == true)

        val far = tracker.update(860.0, slowMps, isOffRoute = false)
        assertEquals(RouteAlert.TURN_FAR, far.alert)
        assertEquals(140.0, far.nextTurn?.distanceM)
        assertTrue(far.nextTurn?.isAnnounced == true)
        assertNull(tracker.update(870.0, slowMps, isOffRoute = false).alert, "far alert repeats")

        assertEquals(RouteAlert.TURN_NEAR, tracker.update(975.0, slowMps, isOffRoute = false).alert)
        assertNull(tracker.update(980.0, slowMps, isOffRoute = false).alert, "near alert repeats")
        assertNull(tracker.update(1_010.0, slowMps, isOffRoute = false).nextTurn, "turn passed")
    }

    @Test
    fun `above 30 km per h the turn is announced earlier`() {
        val tracker = ClimbTurnTracker(emptyList(), listOf(turn))
        // 45 km/h: 1.5 x the distances, far alert at 225 m.
        val fast = tracker.update(780.0, Units.kmhToMps(45.0), isOffRoute = false)
        assertEquals(RouteAlert.TURN_FAR, fast.alert)
        assertTrue(fast.nextTurn?.isAnnounced == true)
    }

    @Test
    fun `off route silences turns until back on route`() {
        val tracker = ClimbTurnTracker(emptyList(), listOf(turn))
        val off = tracker.update(900.0, slowMps, isOffRoute = true)
        assertNull(off.alert)
        assertNull(off.nextTurn)

        assertEquals(RouteAlert.TURN_FAR, tracker.update(900.0, slowMps, isOffRoute = false).alert)
    }

    @Test
    fun `a climb is announced 500 m before, then tracked while riding it`() {
        val tracker = ClimbTurnTracker(listOf(climb), emptyList())

        val far = tracker.update(1_000.0, slowMps, isOffRoute = false)
        assertNull(far.alert)
        assertEquals(1_000.0, far.nextClimb?.distanceToStartM)
        assertFalse(far.nextClimb?.isAnnounced == true)

        val ahead = tracker.update(1_550.0, slowMps, isOffRoute = false)
        assertEquals(RouteAlert.CLIMB_AHEAD, ahead.alert)
        assertTrue(ahead.nextClimb?.isAnnounced == true)
        assertNull(tracker.update(1_600.0, slowMps, isOffRoute = false).alert, "climb alert repeats")

        val on = tracker.update(2_200.0, slowMps, isOffRoute = false).climb
        assertEquals(1, on?.number)
        assertEquals(1, on?.total)
        assertEquals(200.0, on?.remainingDistanceM)
        // The last 200 m are at 8 % and 6 %: 8 + 6 = 14 m to go, 7 % on average.
        assertEquals(14.0, on?.remainingGainM ?: 0.0, 1e-9)
        assertEquals(7.0, on?.remainingAvgGradePercent ?: 0.0, 1e-9)
        assertEquals(0.5, on?.progressFraction ?: 0.0, 1e-9)

        val after = tracker.update(2_500.0, slowMps, isOffRoute = false)
        assertNull(after.climb)
        assertNull(after.nextClimb)
    }

    @Test
    fun `long climbs merge profile sections so at most twelve bars are drawn`() {
        val long = climb.copy(gradeProfilePercent = List(30) { if (it < 15) 4.0 else 8.0 })

        val profile = long.displayProfile()

        assertEquals(10, profile.gradesPercent.size)
        assertEquals(300.0, profile.sectionM)
        assertEquals(4.0, profile.gradesPercent.first())
        assertEquals(8.0, profile.gradesPercent.last())
        assertEquals(climb.gradeProfilePercent, climb.displayProfile().gradesPercent)
    }
}
