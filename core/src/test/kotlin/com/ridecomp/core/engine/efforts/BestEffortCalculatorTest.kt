package com.ridecomp.core.engine.efforts

import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Best efforts with synthetic 1 Hz tracks (T4.6, 07-TEST-PLAN). */
class BestEffortCalculatorTest {
    /** Builds a 1 Hz track from per-second speeds (m/s) and optional power; null speed = paused second. */
    private fun track(speeds: List<Double?>, power: (Int) -> Int? = { null }, estPower: Int? = null): RideTrack {
        val track = RideTrack()
        var distance = 0.0
        speeds.forEachIndexed { second, speed ->
            if (speed == null) return@forEachIndexed
            distance += speed
            track.add(
                RideSample(
                    timeMs = second * MS,
                    distanceM = distance,
                    powerW = power(second),
                    estPowerW = estPower,
                ),
            )
        }
        return track
    }

    private fun List<BestEffort>.of(key: BestEffortKey): BestEffort? = firstOrNull { it.key == key }

    @Test
    fun `steady ride gives the distance efforts it reaches`() {
        val efforts = BestEffortCalculator.calculate(track(List(1_201) { 10.0 }), RideTotals(distanceM = 12_000.0))

        assertEquals(100.0, efforts.of(BestEffortKey.FASTEST_1_KM)?.value ?: 0.0, TOLERANCE)
        assertEquals(500.0, efforts.of(BestEffortKey.FASTEST_5_KM)?.value ?: 0.0, TOLERANCE)
        assertEquals(1_000.0, efforts.of(BestEffortKey.FASTEST_10_KM)?.value ?: 0.0, TOLERANCE)
        assertNull(efforts.of(BestEffortKey.FASTEST_20_KM))
    }

    @Test
    fun `the fastest kilometre is found inside a longer ride`() {
        val speeds = List(600) { 10.0 } + List(50) { 20.0 } + List(600) { 10.0 }

        val best = BestEffortCalculator.calculate(track(speeds), RideTotals()).of(BestEffortKey.FASTEST_1_KM)

        assertEquals(50.0, best?.value ?: 0.0, TOLERANCE)
        // The 20 m/s burst covers seconds 600..649: the window ends on its last sample.
        assertEquals(649 * MS, best?.endMs)
    }

    @Test
    fun `a pause in the middle does not count as time`() {
        // 600 s riding, 300 s paused (no samples), 600 s riding: 10 km at 10 m/s is still 1000 s.
        val speeds = List(600) { 10.0 } + List<Double?>(300) { null } + List(600) { 10.0 }

        val efforts = BestEffortCalculator.calculate(track(speeds), RideTotals())

        assertEquals(1_000.0, efforts.of(BestEffortKey.FASTEST_10_KM)?.value ?: 0.0, PAUSE_TOLERANCE)
    }

    @Test
    fun `power efforts use moving time and only power-meter data`() {
        // 200 W with 400 W between seconds 100 and 159 (60 s): 5 min best = (60 × 400 + 240 × 200) / 300.
        val steady = track(List(1_300) { 10.0 }, power = { if (it in 100..159) 400 else 200 })

        val efforts = BestEffortCalculator.calculate(steady, RideTotals())

        assertEquals(400.0, efforts.of(BestEffortKey.POWER_5_S)?.value ?: 0.0, TOLERANCE)
        assertEquals(400.0, efforts.of(BestEffortKey.POWER_1_MIN)?.value ?: 0.0, TOLERANCE)
        assertEquals(240.0, efforts.of(BestEffortKey.POWER_5_MIN)?.value ?: 0.0, TOLERANCE)
        assertTrue(efforts.of(BestEffortKey.POWER_20_MIN) != null)
        assertNull(efforts.of(BestEffortKey.POWER_60_MIN))

        val estimatedOnly = BestEffortCalculator.calculate(track(List(400) { 10.0 }, estPower = 300), RideTotals())
        assertFalse(estimatedOnly.any { it.key.type == BestEffortType.POWER })
    }

    @Test
    fun `speed efforts find the fastest stretch over moving time`() {
        // 8 m/s with a 14 m/s sprint between seconds 300 and 329 (30 s).
        val speeds = List(300) { 8.0 } + List(30) { 14.0 } + List(900) { 8.0 }

        val efforts = BestEffortCalculator.calculate(track(speeds), RideTotals())

        val sprint = efforts.of(BestEffortKey.SPEED_30_S)
        assertEquals(14.0, sprint?.value ?: 0.0, TOLERANCE)
        assertEquals(299 * MS, sprint?.startMs)
        assertEquals(329 * MS, sprint?.endMs)
        // 1 min: the sprint plus 30 s at 8 m/s.
        assertEquals(11.0, efforts.of(BestEffortKey.SPEED_1_MIN)?.value ?: 0.0, TOLERANCE)
        assertEquals(8.0 + 6.0 * 30 / 1_200, efforts.of(BestEffortKey.SPEED_20_MIN)?.value ?: 0.0, TOLERANCE)
    }

    @Test
    fun `speed efforts skip pauses and need the full duration`() {
        // 20 s riding, a 10 min stop (no samples), 20 s riding: no 1 min window, and the stop is not 30 s.
        val speeds = List(20) { 10.0 } + List<Double?>(600) { null } + List(20) { 10.0 }

        val efforts = BestEffortCalculator.calculate(track(speeds), RideTotals())

        assertEquals(10.0, efforts.of(BestEffortKey.SPEED_30_S)?.value ?: 0.0, PAUSE_TOLERANCE)
        assertNull(efforts.of(BestEffortKey.SPEED_1_MIN))
        assertNull(BestEffortCalculator.calculate(track(List(100) { 0.0 }), RideTotals()).of(BestEffortKey.SPEED_30_S))
    }

    @Test
    fun `power has 30 s and 3 min efforts`() {
        val ride = track(List(400) { 10.0 }, power = { if (it in 100..129) 500 else 200 })

        val efforts = BestEffortCalculator.calculate(ride, RideTotals())

        assertEquals(500.0, efforts.of(BestEffortKey.POWER_30_S)?.value ?: 0.0, TOLERANCE)
        assertEquals((30 * 500 + 150 * 200) / 180.0, efforts.of(BestEffortKey.POWER_3_MIN)?.value ?: 0.0, TOLERANCE)
    }

    @Test
    fun `whole-ride records need data, average speed needs 20 km`() {
        val short = BestEffortCalculator.calculate(
            RideTrack(),
            RideTotals(distanceM = 15_000.0, ascentM = 120.0, avgSpeedMps = 8.0),
        )
        assertEquals(15_000.0, short.of(BestEffortKey.LONGEST_RIDE)?.value)
        assertEquals(120.0, short.of(BestEffortKey.MOST_ASCENT)?.value)
        assertNull(short.of(BestEffortKey.FASTEST_AVG_SPEED))

        val long = BestEffortCalculator.calculate(RideTrack(), RideTotals(distanceM = 25_000.0, avgSpeedMps = 8.0))
        assertEquals(8.0, long.of(BestEffortKey.FASTEST_AVG_SPEED)?.value)
        assertNull(long.of(BestEffortKey.MOST_ASCENT))
    }

    @Test
    fun `a lower time and a higher power are better, ties are not new records`() {
        assertTrue(BestEffortKey.FASTEST_1_KM.isBetter(99.0, 100.0))
        assertFalse(BestEffortKey.FASTEST_1_KM.isBetter(100.0, 100.0))
        assertTrue(BestEffortKey.POWER_5_S.isBetter(401.0, 400.0))
        assertTrue(BestEffortKey.LONGEST_RIDE.isBetter(1.0, null))
        assertEquals(BestEffortKey.POWER_20_MIN, BestEffortKey.fromStorage("power_1200"))
        assertTrue(BestEffortKey.SPEED_1_MIN.isBetter(10.1, 10.0))
        assertEquals(BestEffortKey.SPEED_30_S, BestEffortKey.fromStorage("speed_30"))
    }

    private companion object {
        const val MS = 1_000L
        const val TOLERANCE = 1e-6

        /** The resume step counts one sample interval. */
        const val PAUSE_TOLERANCE = 1.0
    }
}
