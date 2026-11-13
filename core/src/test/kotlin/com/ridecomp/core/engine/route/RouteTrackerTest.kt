package com.ridecomp.core.engine.route

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteTrackerTest {
    /** ~111 m per 0.001° of latitude; [elevations] optional per point. */
    private fun northLine(count: Int, elevations: (Int) -> Double? = { null }): List<RoutePoint> =
        RouteBuilder.build((0 until count).map { GpxPoint(it * STEP, 0.0, elevations(it)) }, "r", null, 0).points

    private fun near(expected: Double, actual: Double, tolerance: Double = 2.0) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")

    @Test
    fun `projection gives distance along, remaining distance and eta`() {
        val tracker = RouteTracker(northLine(21))

        val progress = tracker.update(
            latDeg = 5.5 * STEP,
            lonDeg = 0.0001,
            accuracyM = 5.0,
            timeMs = 0,
            avgSpeedMps = 5.0,
        )

        near(611.0, progress.distanceAlongM)
        near(2_226.0 - 611.0, progress.distanceRemainingM, 3.0)
        near(11.0, progress.distanceFromRouteM, 1.0)
        assertFalse(progress.isOffRoute)
        near((progress.distanceRemainingM / 5.0) * 1_000, assertNotNull(progress.etaMs).toDouble(), 1.0)
        assertNull(tracker.update(5.5 * STEP, 0.0, 5.0, 1_000, avgSpeedMps = 0.5).etaMs)
    }

    @Test
    fun `off route only after 10 s beyond 50 m, back after 5 s within 30 m`() {
        val tracker = RouteTracker(northLine(21))
        val east100m = 0.0009
        var time = 0L
        fun at(lon: Double, accuracy: Double = 5.0) = tracker.update(3 * STEP, lon, accuracy, time, 5.0).also {
            time +=
                1_000
        }

        val far = (0 until 11).map { at(east100m) }
        assertTrue(far.take(10).none { it.event != null }, "no alert during the first 10 s")
        assertEquals(RouteEvent.OFF_ROUTE, far.last().event)
        assertTrue(far.last().isOffRoute)

        val back = (0 until 6).map { at(0.0001) }
        assertTrue(back.take(5).none { it.event != null })
        assertEquals(RouteEvent.BACK_ON_ROUTE, back.last().event)
        assertFalse(back.last().isOffRoute)
    }

    @Test
    fun `poor gps accuracy widens the off route limit`() {
        val tracker = RouteTracker(northLine(21))

        val events = (0 until 20).mapNotNull { tracker.update(3 * STEP, 0.0009, 80.0, it * 1_000L, 5.0).event }

        assertTrue(events.isEmpty(), "100 m away with ±80 m accuracy is within 1.5 × accuracy")
    }

    @Test
    fun `out and back on the same road keeps the current direction`() {
        val out = (0 until 21).map { GpxPoint(it * STEP, 0.0, null) }
        val route = RouteBuilder.build(out + out.reversed().drop(1), "r", null, 0).points
        val tracker = RouteTracker(route)

        // Riding out: the point at 0.005° is both ~556 m (out) and ~3 896 m (back) along the route.
        (0..5).forEach { tracker.update(it * STEP, 0.0, 5.0, it * 1_000L, 5.0) }
        near(556.0, tracker.update(5 * STEP, 0.0, 5.0, 6_000, 5.0).distanceAlongM)

        // After the turn-around the same place matches the return leg.
        (20 downTo 5).forEachIndexed { i, step -> tracker.update(step * STEP, 0.0, 5.0, 10_000L + i * 1_000, 5.0) }
        near(3_896.0, tracker.update(5 * STEP, 0.0, 5.0, 40_000, 5.0).distanceAlongM, 5.0)
    }

    @Test
    fun `rejoining far ahead after being off route is found by a full search`() {
        val tracker = RouteTracker(northLine(41))
        var time = 0L
        tracker.update(STEP, 0.0, 5.0, time, 5.0)
        repeat(11) { tracker.update(2 * STEP, 0.005, 5.0, ++time * 1_000, 5.0) }

        val rejoined = (0 until 6).map { tracker.update(35 * STEP, 0.0, 5.0, (++time) * 1_000, 5.0) }

        assertEquals(RouteEvent.BACK_ON_ROUTE, rejoined.last().event)
        near(3_896.0, rejoined.last().distanceAlongM, 5.0)
    }

    @Test
    fun `remaining ascent counts only climbing still ahead`() {
        // Up 10 m per point to point 10, then flat.
        val tracker = RouteTracker(northLine(21) { if (it <= 10) 100.0 + it * 10 else 200.0 })

        near(100.0, assertNotNull(tracker.update(0.0, 0.0, 5.0, 0, 5.0).remainingAscentM), 0.1)
        near(50.0, assertNotNull(tracker.update(5 * STEP, 0.0, 5.0, 1_000, 5.0).remainingAscentM), 0.1)
        near(0.0, assertNotNull(tracker.update(15 * STEP, 0.0, 5.0, 2_000, 5.0).remainingAscentM), 0.1)
        assertNull(RouteTracker(northLine(3)).update(0.0, 0.0, 5.0, 0, 5.0).remainingAscentM)
    }

    private companion object {
        const val STEP = 0.001
    }
}
