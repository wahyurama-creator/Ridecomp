package com.ridecomp.core.engine.route

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 07-TEST-PLAN "Tanjakan" & T3.13: reference geometries built point by point (10 m spacing). */
class ClimbsAndTurnsTest {
    private val metersPerDegree = 111_195.0

    /** Builds a route from legs of (length m, heading deg, grade %) starting at elevation 100 m. */
    private fun route(vararg legs: Triple<Double, Double, Double>, withElevation: Boolean = true): List<RoutePoint> {
        var lat = 0.0
        var lon = 0.0
        var elevation = 100.0
        val points = arrayListOf(GpxPoint(lat, lon, elevation.takeIf { withElevation }))
        legs.forEach { (length, heading, grade) ->
            repeat((length / STEP_M).toInt()) {
                lat += STEP_M * cos(Math.toRadians(heading)) / metersPerDegree
                lon += STEP_M * sin(Math.toRadians(heading)) / metersPerDegree
                elevation += STEP_M * grade / 100
                points += GpxPoint(lat, lon, elevation.takeIf { withElevation })
            }
        }
        return RouteBuilder.build(points, "r", null, 0).points
    }

    private fun near(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")

    @Test
    fun `real climbs are found, a short bump is not, a small dip is merged`() {
        val climbs = ClimbDetector.detect(
            route(
                Triple(3_000.0, 0.0, 0.0),
                Triple(2_000.0, 0.0, 6.0),
                Triple(1_000.0, 0.0, 0.0),
                Triple(300.0, 0.0, 2.0),
                Triple(1_000.0, 0.0, 0.0),
                Triple(800.0, 0.0, 5.0),
                Triple(100.0, 0.0, -5.0),
                Triple(800.0, 0.0, 5.0),
                Triple(1_000.0, 0.0, 0.0),
            ),
        )

        assertEquals(2, climbs.size, "climbs: $climbs")
        val first = climbs[0]
        near(3_000.0, first.startDistanceM, 100.0)
        near(2_000.0, first.lengthM, 150.0)
        near(120.0, first.gainM, 10.0)
        near(6.0, first.avgGradePercent, 0.6)
        assertTrue(first.gradeProfilePercent.isNotEmpty())
        // 800 m up, a 5 m dip, 800 m up: one climb of about 1.7 km.
        near(1_700.0, climbs[1].lengthM, 200.0)
        assertEquals(listOf(0, 1), climbs.map { it.index })
    }

    @Test
    fun `routes without elevation or gentle slopes have no climbs`() {
        assertTrue(ClimbDetector.detect(route(Triple(5_000.0, 0.0, 6.0), withElevation = false)).isEmpty())
        assertTrue(ClimbDetector.detect(route(Triple(3_000.0, 0.0, 2.0))).isEmpty(), "2 % is below 3 %")
        assertTrue(ClimbDetector.detect(route(Triple(400.0, 0.0, 8.0))).isEmpty(), "400 m is shorter than 500 m")
    }

    @Test
    fun `a few points without elevation do not hide or invent climbs`() {
        val full = route(Triple(3_000.0, 0.0, 0.0), Triple(2_000.0, 0.0, 6.0), Triple(1_000.0, 0.0, 0.0))
        // Every 7th point loses its elevation, including the first one.
        val gappy = full.mapIndexed { i, p -> if (i % 7 == 0) p.copy(elevationM = null) else p }

        val climbs = ClimbDetector.detect(gappy)

        assertEquals(1, climbs.size, "climbs: $climbs")
        near(120.0, climbs.single().gainM, 10.0)
        near(ClimbDetector.detect(full).single().startDistanceM, climbs.single().startDistanceM, 40.0)
    }

    @Test
    fun `a 90 degree left turn, a sharp right and a hairpin`() {
        val left = TurnDetector.detect(route(Triple(500.0, 0.0, 0.0), Triple(500.0, 270.0, 0.0)))
        assertEquals(1, left.size, "turns: $left")
        assertEquals(TurnDirection.LEFT, left.single().direction)
        assertEquals(TurnSeverity.NORMAL, left.single().severity)
        near(500.0, left.single().distanceM, 20.0)

        val sharp = TurnDetector.detect(route(Triple(500.0, 0.0, 0.0), Triple(500.0, 120.0, 0.0)))
        assertEquals(TurnSeverity.SHARP, sharp.single().severity)
        assertEquals(TurnDirection.RIGHT, sharp.single().direction)

        // Turnaround on a narrow road (5 m): read as a U-turn.
        val hairpin = TurnDetector.detect(
            route(Triple(500.0, 0.0, 0.0), Triple(5.0, 90.0, 0.0), Triple(500.0, 180.0, 0.0)),
        )
        assertEquals(TurnDirection.UTURN, hairpin.single().direction)
    }

    @Test
    fun `gentle bends, gps noise and the route ends give no alerts`() {
        // 40 degrees over 400 m: a sweeping bend, never 45 degrees within ±30 m.
        val bend =
            route(Triple(500.0, 0.0, 0.0), *Array(40) { Triple(10.0, it.toDouble(), 0.0) }, Triple(500.0, 40.0, 0.0))
        assertTrue(TurnDetector.detect(bend).isEmpty(), "bend: ${TurnDetector.detect(bend)}")

        // Zig-zag of ±3 m on a straight road (GPS noise): removed by the 5 m simplification.
        val noisy = (0..200).map { i ->
            GpxPoint(
                i * 10 / metersPerDegree,
                (if (i % 2 == 0) 3.0 else -3.0) / metersPerDegree,
                null,
            )
        }
        assertTrue(TurnDetector.detect(RouteBuilder.build(noisy, "n", null, 0).points).isEmpty())

        // A turn 20 m after the start is ignored (first 50 m).
        assertTrue(TurnDetector.detect(route(Triple(20.0, 0.0, 0.0), Triple(500.0, 90.0, 0.0))).isEmpty())
    }

    private companion object {
        const val STEP_M = 10.0
    }
}
