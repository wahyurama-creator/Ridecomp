package com.ridecomp.core.engine.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElevationTrackerTest {
    @Test
    fun `gps noise of plus minus 3 m on flat ground adds no ascent`() {
        val tracker = ElevationTracker(AltitudeSource.GPS)
        val noise = listOf(0.0, 3.0, -3.0, 2.5, -2.0, 3.0, -3.0, 1.0, -2.5, 3.0)

        repeat(30) { second ->
            tracker.add(100.0 + noise[second % noise.size], null, distanceM = second * 8.0, isMoving = true)
        }

        assertEquals(0.0, tracker.snapshot().ascentM)
        assertEquals(0.0, tracker.snapshot().descentM)
    }

    @Test
    fun `gps spikes of hundreds of meters on a flat road add no ascent`() {
        // Seen on a phone without a barometer (D-148): single readings 300 m off, and short runs of them.
        val tracker = ElevationTracker(AltitudeSource.GPS)
        repeat(1_800) { second ->
            val spike = when {
                second % 137 == 0 -> 300.0
                second % 211 in 0..4 -> -250.0
                else -> 0.0
            }
            tracker.add(40.0 + spike, null, distanceM = second * 5.0, isMoving = true)
        }

        assertEquals(0.0, tracker.snapshot().ascentM)
    }

    @Test
    fun `steady climb is counted as ascent`() {
        val tracker = ElevationTracker(AltitudeSource.GPS)

        // 0.4 m per second for 120 s = 48 m climb.
        (0..120).forEach { tracker.add(100.0 + it * 0.4, null, distanceM = it * 8.0, isMoving = true) }

        val ascent = tracker.snapshot().ascentM
        assertTrue(ascent in 36.0..48.0, "ascent was $ascent")
    }

    @Test
    fun `descent is counted separately`() {
        val tracker = ElevationTracker(AltitudeSource.BAROMETER)

        (0..120).forEach { tracker.add(200.0 - it * 0.5, null, distanceM = it * 10.0, isMoving = true) }

        assertTrue(tracker.snapshot().descentM > 45.0)
        assertEquals(0.0, tracker.snapshot().ascentM)
    }

    @Test
    fun `altitude changes while paused are not counted`() {
        val tracker = ElevationTracker(AltitudeSource.GPS)

        (0..60).forEach { tracker.add(100.0 + it, null, distanceM = 0.0, isMoving = false) }

        assertEquals(0.0, tracker.snapshot().ascentM)
        assertNotNull(tracker.snapshot().altitudeM)
    }

    @Test
    fun `grade on a steady 5 percent road`() {
        val tracker = ElevationTracker(AltitudeSource.BAROMETER)

        // 6 m/s with 0.3 m/s climb = 5 %.
        (0..60).forEach { tracker.add(50.0 + it * 0.3, null, distanceM = it * 6.0, isMoving = true) }

        assertEquals(5.0, requireNotNull(tracker.snapshot().gradePercent), absoluteTolerance = 0.2)
    }

    @Test
    fun `grade needs at least 30 m of distance`() {
        val tracker = ElevationTracker(AltitudeSource.GPS)

        (0..4).forEach { tracker.add(100.0 + it, null, distanceM = it * 5.0, isMoving = true) }

        assertNull(tracker.snapshot().gradePercent)
    }

    @Test
    fun `grade is clamped to 30 percent`() {
        val tracker = ElevationTracker(AltitudeSource.BAROMETER)

        (0..40).forEach { tracker.add(it * 5.0, null, distanceM = it * 5.0, isMoving = true) }

        assertEquals(30.0, tracker.snapshot().gradePercent)
    }

    @Test
    fun `barometer offset from the first gps altitude never creates a fake climb`() {
        val tracker = ElevationTracker(AltitudeSource.BAROMETER)
        (0..20).forEach { tracker.add(10.0, null, distanceM = it * 5.0, isMoving = true) }

        // GPS says we are at 300 m: the offset is +290 m.
        (21..60).forEach { tracker.add(10.0, gpsAltitudeM = 300.0, distanceM = it * 5.0, isMoving = true) }

        assertEquals(0.0, tracker.snapshot().ascentM)
        assertEquals(300.0, requireNotNull(tracker.snapshot().altitudeM), absoluteTolerance = 0.01)
    }

    @Test
    fun `restore continues totals after recovery`() {
        val tracker = ElevationTracker(AltitudeSource.GPS)
        tracker.restore(ascentM = 120.0, descentM = 80.0)

        assertEquals(120.0, tracker.snapshot().ascentM)
        assertEquals(80.0, tracker.snapshot().descentM)
    }

    @Test
    fun `invalid altitude values are ignored`() {
        val tracker = ElevationTracker(AltitudeSource.GPS)

        tracker.add(Double.NaN, null, distanceM = 0.0, isMoving = true)

        assertNull(tracker.snapshot().altitudeM)
    }

    @Test
    fun `standard pressure is sea level and lower pressure is higher`() {
        assertEquals(0.0, BarometricAltitude.altitudeM(1_013.25), absoluteTolerance = 1e-6)
        // ISA: 898.76 hPa at 1000 m.
        assertEquals(1_000.0, BarometricAltitude.altitudeM(898.76), absoluteTolerance = 2.0)
    }
}
