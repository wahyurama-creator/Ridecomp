package com.ridecomp.core.engine.segments

import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.util.Outcome
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Segment matching (T4.6): passing part of it, the other direction, GPS noise, riding it twice. */
class SegmentMatcherTest {
    /** A position [x] meters east and [y] meters north of the test origin. */
    private data class Xy(val x: Double, val y: Double)

    private fun lat(y: Double) = LAT0 + y / M_PER_DEG
    private fun lon(x: Double) = LON0 + x / (M_PER_DEG * cos(Math.toRadians(LAT0)))

    /** The 1 km segment going east along y = 0. */
    private val segment: Segment = (
        SegmentBuilder.build(
            name = "Jalan lurus",
            points = (0..100).map { RoutePoint(lat(0.0), lon(it * 10.0), null, it * 10.0) },
            source = SegmentSource.ACTIVITY,
            nowMs = 0,
        ) as Outcome.Success
        ).value.copy(id = 1)

    /** Straight legs between waypoints, sampled every [STEP_M] meters once a second. */
    private fun ride(vararg waypoints: Xy, noise: (Int) -> Double = { 0.0 }): RideTrack {
        val track = RideTrack()
        var distance = 0.0
        var second = 0
        waypoints.toList().zipWithNext().forEach { (from, to) ->
            val length = hypot(to.x - from.x, to.y - from.y)
            val steps = (length / STEP_M).toInt()
            for (step in 0 until steps) {
                val f = step.toDouble() / steps
                val x = from.x + (to.x - from.x) * f
                val y = from.y + (to.y - from.y) * f + noise(second)
                track.add(RideSample(timeMs = second * MS, latDeg = lat(y), lonDeg = lon(x), distanceM = distance))
                distance += STEP_M
                second++
            }
        }
        return track
    }

    @Test
    fun `riding the segment matches once with its time`() {
        val matches = SegmentMatcher.match(segment, ride(Xy(-300.0, 0.0), Xy(1_300.0, 0.0)))

        assertEquals(1, matches.size)
        assertEquals(1_000.0 / STEP_M, matches.single().elapsedS, ONE_SAMPLE_S)
    }

    @Test
    fun `the other direction never matches`() {
        assertTrue(SegmentMatcher.match(segment, ride(Xy(1_300.0, 0.0), Xy(-300.0, 0.0))).isEmpty())
    }

    @Test
    fun `leaving halfway or taking a detour does not match`() {
        val halfway = ride(Xy(-300.0, 0.0), Xy(600.0, 0.0), Xy(600.0, 800.0))
        val detour =
            ride(Xy(-300.0, 0.0), Xy(300.0, 0.0), Xy(300.0, 150.0), Xy(700.0, 150.0), Xy(700.0, 0.0), Xy(1_300.0, 0.0))

        assertEquals(emptyList(), SegmentMatcher.match(segment, halfway), "halfway")
        assertEquals(emptyList(), SegmentMatcher.match(segment, detour), "detour")
    }

    @Test
    fun `GPS noise within the path radius still matches`() {
        val noisy = ride(Xy(-300.0, 0.0), Xy(1_300.0, 0.0), noise = { if (it % 2 == 0) NOISE_M else -NOISE_M })

        assertEquals(1, SegmentMatcher.match(segment, noisy).size)
    }

    @Test
    fun `riding it twice gives two efforts`() {
        val twice = ride(
            Xy(-300.0, 0.0),
            Xy(1_300.0, 0.0),
            Xy(1_300.0, 300.0),
            Xy(-300.0, 300.0),
            Xy(-300.0, 0.0),
            Xy(1_300.0, 0.0),
        )

        assertEquals(2, SegmentMatcher.match(segment, twice).size)
    }

    @Test
    fun `rides elsewhere are skipped by their bounding box`() {
        val far = ride(Xy(50_000.0, 50_000.0), Xy(51_000.0, 50_000.0))
        val near = ride(Xy(-300.0, 0.0), Xy(1_300.0, 0.0))

        assertFalse(SegmentMatcher.mayOverlap(segment, SegmentMatcher.boundsOf(far)))
        assertTrue(SegmentMatcher.mayOverlap(segment, SegmentMatcher.boundsOf(near)))
        assertFalse(SegmentMatcher.mayOverlap(segment, SegmentMatcher.boundsOf(RideTrack())))
    }

    @Test
    fun `segments must be 200 m to 50 km with positions`() {
        fun build(lengthM: Double, latDeg: Double = LAT0) = SegmentBuilder.build(
            "x",
            listOf(RoutePoint(latDeg, LON0, 100.0, 0.0), RoutePoint(latDeg, lon(lengthM), 110.0, lengthM)),
            SegmentSource.CLIMB,
            0,
        )

        assertEquals(Outcome.Failure(SegmentError.TOO_SHORT), build(150.0))
        assertEquals(Outcome.Failure(SegmentError.TOO_LONG), build(60_000.0))
        assertEquals(Outcome.Failure(SegmentError.NO_POSITION), build(1_000.0, latDeg = Double.NaN))
        val climb = (build(500.0) as Outcome.Success).value
        assertEquals(2.0, climb.avgGradePercent, TOLERANCE)
        assertEquals(SegmentSource.CLIMB, climb.source)
    }

    private companion object {
        const val LAT0 = -7.78
        const val LON0 = 110.37
        const val M_PER_DEG = 111_320.0
        const val MS = 1_000L
        const val STEP_M = 8.0
        const val NOISE_M = 12.0
        const val ONE_SAMPLE_S = 2.0
        const val TOLERANCE = 1e-9
    }
}
