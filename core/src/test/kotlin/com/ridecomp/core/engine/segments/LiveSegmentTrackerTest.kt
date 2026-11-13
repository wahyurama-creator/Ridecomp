package com.ridecomp.core.engine.segments

import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.util.Outcome
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Live segments (T4.3, 12-FEATURE-SPECS §9.3). */
class LiveSegmentTrackerTest {
    private fun lat(y: Double) = LAT0 + y / M_PER_DEG
    private fun lon(x: Double) = LON0 + x / (M_PER_DEG * cos(Math.toRadians(LAT0)))

    /** 1 km going east along y = 0. */
    private val segment: Segment = (
        SegmentBuilder.build(
            "Lurus",
            (0..100).map { RoutePoint(lat(0.0), lon(it * 10.0), null, it * 10.0) },
            SegmentSource.ACTIVITY,
            0,
        ) as Outcome.Success
        ).value.copy(id = 1)

    /** Feeds one fix a second along straight legs at [STEP_M] per second; returns every state. */
    private fun ride(tracker: LiveSegmentTracker, vararg waypoints: Pair<Double, Double>): List<LiveSegmentState> {
        val states = mutableListOf<LiveSegmentState>()
        var second = 0L
        waypoints.toList().zipWithNext().forEach { (from, to) ->
            val steps = (hypot(to.first - from.first, to.second - from.second) / STEP_M).toInt()
            for (step in 0 until steps) {
                val f = step.toDouble() / steps
                val x = from.first + (to.first - from.first) * f
                val y = from.second + (to.second - from.second) * f
                states += tracker.update(lat(y), lon(x), second * MS)
                second++
            }
        }
        return states
    }

    @Test
    fun `approaching, riding and finishing slower than the record`() {
        val states = ride(
            LiveSegmentTracker(listOf(LiveSegment(segment, prElapsedS = 100.0))),
            -200.0 to 0.0,
            1_300.0 to 0.0,
        )

        assertTrue(states.any { it.approaching == "Lurus" })
        assertEquals(1, states.count { it.alert == SegmentAlert.STARTED })
        val halfway = states.mapNotNull { it.active }.first { it.fraction >= 0.5 }
        // 8 m/s against a 100 s record over 1 km: about 12 s behind at half way.
        assertEquals(12.5, halfway.deltaVsPrS ?: 0.0, ONE_SAMPLE_S)
        val result = assertNotNull(states.firstNotNullOfOrNull { it.result })
        assertEquals(125.0, result.elapsedS, ONE_SAMPLE_S)
        assertFalse(result.isPr)
        assertEquals(25.0, result.deltaVsPrS ?: 0.0, ONE_SAMPLE_S)
        assertEquals(1, states.count { it.alert == SegmentAlert.FINISHED })
    }

    @Test
    fun `a first effort is a new record`() {
        val states = ride(
            LiveSegmentTracker(listOf(LiveSegment(segment, prElapsedS = null))),
            -200.0 to 0.0,
            1_300.0 to 0.0,
        )

        assertEquals(1, states.count { it.alert == SegmentAlert.NEW_RECORD })
        assertNull(states.firstNotNullOfOrNull { it.active?.deltaVsPrS })
    }

    @Test
    fun `the other direction never starts`() {
        val states = ride(LiveSegmentTracker(listOf(LiveSegment(segment, 100.0))), 1_300.0 to 0.0, -200.0 to 0.0)

        assertTrue(states.none { it.active != null })
    }

    @Test
    fun `leaving the segment abandons it without a result`() {
        val states = ride(
            LiveSegmentTracker(listOf(LiveSegment(segment, 100.0))),
            -200.0 to 0.0,
            400.0 to 0.0,
            400.0 to 600.0,
        )

        assertTrue(states.any { it.active != null })
        assertNull(states.last().active)
        assertTrue(states.none { it.result != null })
    }

    @Test
    fun `nothing happens far from every segment`() {
        val states = ride(
            LiveSegmentTracker(listOf(LiveSegment(segment, 100.0))),
            5_000.0 to 5_000.0,
            6_000.0 to 5_000.0,
        )

        assertTrue(states.all { it == LiveSegmentState() })
    }

    private companion object {
        const val LAT0 = -7.78
        const val LON0 = 110.37
        const val M_PER_DEG = 111_320.0
        const val MS = 1_000L
        const val STEP_M = 8.0
        const val ONE_SAMPLE_S = 2.0
    }
}
