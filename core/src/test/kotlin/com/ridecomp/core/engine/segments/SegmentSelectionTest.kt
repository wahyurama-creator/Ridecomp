package com.ridecomp.core.engine.segments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Figures of the part chosen in the segment editor (T11.15). */
class SegmentSelectionTest {
    /** 10 m per second for 300 s; climbs 30 m over the first kilometre, then flat. */
    private val profile = TrackProfile(
        DoubleArray(301) { it * 10.0 },
        LongArray(301) { it * 1_000L },
        DoubleArray(301) { if (it <= 100) 100.0 + it * 0.3 else 130.0 },
    )

    @Test
    fun `length, climb, grade, time and speed of the chosen part`() {
        val stats = profile.stats(0.0, 1_000.0)

        assertEquals(1_000.0, stats.lengthM, DELTA)
        // Counted in steps above the hysteresis, like a saved segment: the last small rise is not counted.
        assertEquals(30.0, stats.gainM ?: 0.0, HYSTERESIS_M)
        assertEquals(3.0, stats.avgGradePercent ?: 0.0, DELTA)
        assertEquals(100.0, stats.elapsedS, DELTA)
        assertEquals(10.0, stats.avgSpeedMps ?: 0.0, DELTA)
        assertEquals(100.0, stats.altitudeRangeM?.start ?: 0.0, DELTA)
        assertEquals(130.0, stats.altitudeRangeM?.endInclusive ?: 0.0, DELTA)
    }

    @Test
    fun `times are interpolated between samples, a flat part has no climb`() {
        val stats = profile.stats(1_505.0, 2_005.0)

        assertEquals(50.0, stats.elapsedS, DELTA)
        assertEquals(0.0, stats.gainM ?: -1.0, DELTA)
        assertEquals(0.0, stats.avgGradePercent ?: -1.0, DELTA)
    }

    @Test
    fun `without altitude there is no climb or grade, an empty part has no figures`() {
        val flat =
            TrackProfile(DoubleArray(3) { it * 10.0 }, LongArray(3) { it * 1_000L }, DoubleArray(3) { Double.NaN })

        assertNull(flat.stats(0.0, 20.0).gainM)
        assertNull(flat.stats(0.0, 20.0).avgGradePercent)
        assertEquals(0.0, profile.stats(500.0, 500.0).elapsedS, DELTA)
        assertNull(profile.stats(500.0, 500.0).avgSpeedMps)
    }

    private companion object {
        const val DELTA = 1e-6
        const val HYSTERESIS_M = 1.0
    }
}
