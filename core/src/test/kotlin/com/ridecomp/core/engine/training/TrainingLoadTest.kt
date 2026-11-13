package com.ridecomp.core.engine.training

import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.Sex
import java.time.LocalDate
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reference values for TSS, TRIMP and CTL/ATL (T4.6, 12-FEATURE-SPECS §10). */
class TrainingLoadTest {
    private fun hrTrack(bpm: Int?, minutes: Int): RideTrack = RideTrack().apply {
        repeat(minutes * SECONDS_PER_MINUTE) { add(RideSample(timeMs = it * MS, heartRateBpm = bpm)) }
    }

    @Test
    fun `power meter and FTP give a TSS-like load`() {
        val load = TrainingLoadCalculator.calculate(
            hrTrack(bpm = 150, minutes = 60),
            RideTotals(movingS = 3_600, normalizedPowerW = 200),
            LoadInputs(ftpW = 250, hrMaxBpm = 190, hrRestBpm = 60, sex = Sex.MALE),
        )

        assertEquals(LoadMethod.POWER, load?.method)
        assertEquals(0.8, load?.intensityFactor ?: 0.0, TOLERANCE)
        assertEquals(64.0, load?.load ?: 0.0, TOLERANCE)
    }

    @Test
    fun `without FTP heart rate gives Banister TRIMP per minute`() {
        val load = TrainingLoadCalculator.calculate(
            hrTrack(bpm = 150, minutes = 60),
            RideTotals(movingS = 3_600, normalizedPowerW = 200),
            LoadInputs(ftpW = null, hrMaxBpm = 190, hrRestBpm = 60, sex = Sex.MALE),
        )

        val reserve = 90.0 / 130.0
        assertEquals(LoadMethod.HEART_RATE, load?.method)
        assertEquals(60 * reserve * 0.64 * exp(1.92 * reserve), load?.load ?: 0.0, TOLERANCE)
        assertFalse(load?.isEstimatedInputs ?: true)
    }

    @Test
    fun `unknown sex uses the average coefficients and is marked as an estimate`() {
        val load = TrainingLoadCalculator.calculate(
            hrTrack(bpm = 150, minutes = 10),
            RideTotals(),
            LoadInputs(ftpW = null, hrMaxBpm = 190, hrRestBpm = 60, sex = null),
        )

        val reserve = 90.0 / 130.0
        assertEquals(10 * reserve * 0.75 * exp(1.795 * reserve), load?.load ?: 0.0, TOLERANCE)
        assertTrue(load?.isEstimatedInputs ?: false)
    }

    @Test
    fun `no heart rate and no power means no load`() {
        assertNull(
            TrainingLoadCalculator.calculate(
                hrTrack(bpm = null, minutes = 5),
                RideTotals(),
                LoadInputs(250, 190, 60, null),
            ),
        )
        assertNull(
            TrainingLoadCalculator.calculate(
                hrTrack(bpm = 150, minutes = 5),
                RideTotals(),
                LoadInputs(null, null, 60, null),
            ),
        )
    }

    @Test
    fun `fitness, fatigue and form follow the daily formulas`() {
        val day0 = LocalDate.of(2026, 11, 1)

        val days = TrainingTrend.compute(day0, day0.plusDays(1), dayBefore = null, loadByDate = mapOf(day0 to 100.0))

        assertEquals(100.0 / 42, days[0].ctl, TOLERANCE)
        assertEquals(100.0 / 7, days[0].atl, TOLERANCE)
        assertEquals(0.0, days[0].tsb, TOLERANCE)
        assertEquals(100.0 / 42 - 100.0 / 7, days[1].tsb, TOLERANCE)
        assertEquals(days[0].ctl - days[0].ctl / 42, days[1].ctl, TOLERANCE)
    }

    @Test
    fun `heart rate zone time adds up the seconds`() {
        val zones = ZoneTime.heartRate(hrTrack(bpm = 150, minutes = 2), hrMaxBpm = 200)

        assertEquals(listOf(0L, 0L, 120L, 0L, 0L), zones)
        assertTrue(ZoneTime.heartRate(hrTrack(bpm = null, minutes = 1), 200).isEmpty())
    }

    private companion object {
        const val MS = 1_000L
        const val SECONDS_PER_MINUTE = 60
        const val TOLERANCE = 1e-6
    }
}
