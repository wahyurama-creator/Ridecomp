package com.ridecomp.core.engine.geo

import com.ridecomp.core.engine.metrics.ExponentialSmoother
import com.ridecomp.core.model.LocationFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ~1 m of latitude in degrees. */
private const val METER_IN_LAT_DEG = 1.0 / 111_195.0

private fun fix(
    tSec: Long,
    northM: Double,
    accuracyM: Double? = 5.0,
    speedMps: Double? = null,
    lonDeg: Double = 110.0,
) = LocationFix(
    timeMs = tSec * 1_000,
    latDeg = -7.0 + northM * METER_IN_LAT_DEG,
    lonDeg = lonDeg,
    altitudeM = null,
    accuracyM = accuracyM,
    speedMps = speedMps,
)

class GeoMathTest {
    @Test
    fun `one degree of longitude on the equator is about 111 km`() {
        assertEquals(111_195.0, GeoMath.distanceM(0.0, 0.0, 0.0, 1.0), absoluteTolerance = 1.0)
    }

    @Test
    fun `distance between identical points is zero`() {
        assertEquals(0.0, GeoMath.distanceM(-7.79, 110.36, -7.79, 110.36))
    }

    @Test
    fun `antipodal points are half the circumference apart`() {
        assertEquals(Math.PI * GeoMath.EARTH_RADIUS_M, GeoMath.distanceM(0.0, 0.0, 0.0, 180.0), 1.0)
    }

    @Test
    fun `coordinate validation rejects out of range and non finite values`() {
        assertTrue(GeoMath.isValidCoordinate(-7.8, 110.4))
        assertFalse(GeoMath.isValidCoordinate(91.0, 0.0))
        assertFalse(GeoMath.isValidCoordinate(0.0, -181.0))
        assertFalse(GeoMath.isValidCoordinate(Double.NaN, 0.0))
        assertFalse(GeoMath.isValidCoordinate(0.0, Double.POSITIVE_INFINITY))
    }
}

class GpsFilterTest {
    @Test
    fun `good fix is accepted`() {
        assertNull(GpsFilter().check(fix(0, 0.0)))
    }

    @Test
    fun `fix with poor accuracy is rejected`() {
        assertEquals(GpsRejection.POOR_ACCURACY, GpsFilter().check(fix(0, 0.0, accuracyM = 31.0)))
    }

    @Test
    fun `fix without accuracy is accepted`() {
        assertNull(GpsFilter().check(fix(0, 0.0, accuracyM = null)))
    }

    @Test
    fun `invalid coordinate is rejected`() {
        val bad = fix(0, 0.0).copy(latDeg = Double.NaN)

        assertEquals(GpsRejection.INVALID_COORDINATE, GpsFilter().check(bad))
    }

    @Test
    fun `fix that does not advance in time is rejected`() {
        val filter = GpsFilter()
        filter.check(fix(10, 0.0))

        assertEquals(GpsRejection.NOT_NEWER, filter.check(fix(10, 5.0)))
        assertEquals(GpsRejection.NOT_NEWER, filter.check(fix(9, 5.0)))
    }

    @Test
    fun `jump faster than 120 kmh is a spike`() {
        val filter = GpsFilter()
        filter.check(fix(0, 0.0))

        assertEquals(GpsRejection.SPEED_SPIKE, filter.check(fix(1, 40.0)))
    }

    @Test
    fun `spike does not replace the reference fix`() {
        val filter = GpsFilter()
        filter.check(fix(0, 0.0))
        filter.check(fix(1, 500.0))

        assertNull(filter.check(fix(2, 16.0)))
    }

    @Test
    fun `reset forgets the previous fix`() {
        val filter = GpsFilter()
        filter.check(fix(10, 0.0))
        filter.reset()

        assertNull(filter.check(fix(5, 1_000.0)))
    }
}

class DistanceTrackerTest {
    @Test
    fun `moving fixes accumulate distance`() {
        val tracker = DistanceTracker()

        (0L..10L).forEach { tracker.add(fix(it, it * 8.0), isCounting = true) }

        assertEquals(80.0, tracker.totalM, absoluteTolerance = 0.5)
    }

    @Test
    fun `jitter while standing still adds no distance`() {
        val tracker = DistanceTracker()
        val jitterM = listOf(0.0, 1.2, -0.8, 1.5, -1.1, 0.4, -1.3, 0.9)

        jitterM.forEachIndexed { second, offset -> tracker.add(fix(second.toLong(), offset), isCounting = true) }

        assertEquals(0.0, tracker.totalM)
    }

    @Test
    fun `slow riding without device speed is still counted once past the jitter floor`() {
        val tracker = DistanceTracker()

        (0L..12L).forEach { tracker.add(fix(it, it * 1.0), isCounting = true) }

        assertEquals(12.0, tracker.totalM, absoluteTolerance = 6.5)
        assertTrue(tracker.totalM > 0.0)
    }

    @Test
    fun `reported device speed decides whether the rider moves`() {
        val tracker = DistanceTracker()
        tracker.add(fix(0, 0.0, speedMps = 0.1), isCounting = true)

        tracker.add(fix(1, 3.0, speedMps = 0.1), isCounting = true)

        assertEquals(0.0, tracker.totalM)
    }

    @Test
    fun `distance is not counted while not recording and not replayed after resume`() {
        val tracker = DistanceTracker()
        tracker.add(fix(0, 0.0), isCounting = true)
        tracker.add(fix(10, 100.0), isCounting = false)

        tracker.add(fix(11, 108.0), isCounting = true)

        assertEquals(8.0, tracker.totalM, absoluteTolerance = 0.5)
    }

    @Test
    fun `restore continues from a checkpoint`() {
        val tracker = DistanceTracker()
        tracker.restore(1_000.0)
        tracker.add(fix(0, 0.0), isCounting = true)

        tracker.add(fix(1, 8.0), isCounting = true)

        assertEquals(1_008.0, tracker.totalM, absoluteTolerance = 0.5)
    }
}

class ExponentialSmootherTest {
    @Test
    fun `first sample initialises and later samples blend`() {
        val smoother = ExponentialSmoother(0.3)

        assertEquals(10.0, smoother.add(10.0))
        assertEquals(13.0, smoother.add(20.0), absoluteTolerance = 1e-9)
    }

    @Test
    fun `reset clears the value`() {
        val smoother = ExponentialSmoother(0.3)
        smoother.add(5.0)

        smoother.reset()

        assertNull(smoother.value)
    }

    @Test
    fun `alpha must be within range`() {
        assertFailsWith<IllegalArgumentException> { ExponentialSmoother(0.0) }
        assertFailsWith<IllegalArgumentException> { ExponentialSmoother(1.5) }
    }
}
