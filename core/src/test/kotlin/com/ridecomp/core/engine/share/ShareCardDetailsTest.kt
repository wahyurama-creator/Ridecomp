package com.ridecomp.core.engine.share

import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.RideSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Splits and elevation strip of a share card (T4.5b). */
class ShareCardDetailsTest {
    private fun track(speeds: List<Double?>, altitude: (Int) -> Double? = { null }): RideTrack = RideTrack().apply {
        var distance = 0.0
        speeds.forEachIndexed { second, speed ->
            if (speed == null) return@forEachIndexed
            distance += speed
            add(RideSample(timeMs = second * 1_000L, distanceM = distance, altitudeM = altitude(second)))
        }
    }

    @Test
    fun `splits are moving time per kilometre and a pause does not count`() {
        // 1.5 km at 10 m/s, then 5 min paused, then 1.5 km at 5 m/s.
        val speeds = List(150) { 10.0 } + List<Double?>(300) { null } + List(310) { 5.0 }

        val splits = ShareCardDetails.splits(track(speeds))

        assertEquals(3, splits.size)
        assertEquals(100.0, splits[0], 1.0)
        // 500 m at 10 m/s + the resume step + 500 m at 5 m/s: the 5-minute pause is not in it.
        assertEquals(151.0, splits[1], 2.0)
        assertEquals(200.0, splits[2], 1.0)
    }

    @Test
    fun `the elevation strip has a fixed number of points and is empty without altitude`() {
        val hilly = track(List(500) { 8.0 }) { it.toDouble() }

        assertEquals(ShareCardConfig.PROFILE_POINTS, ShareCardDetails.elevation(hilly).size)
        assertTrue(ShareCardDetails.elevation(track(List(500) { 8.0 })).isEmpty())
        assertTrue(ShareCardDetails.splits(RideTrack()).isEmpty())
    }
}
