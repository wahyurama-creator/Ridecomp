package com.ridecomp.core.model

import com.ridecomp.core.config.AutoPauseConfig
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.config.ReminderConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelDefaultsTest {
    @Test
    fun `new profile uses spec defaults`() {
        val profile = RiderProfile()

        assertEquals(ProfileDefaults.WEIGHT_KG, profile.weightKg)
        assertEquals(ProfileDefaults.BIKE_WEIGHT_KG, profile.bikeWeightKg)
        assertEquals(ProfileDefaults.WHEEL_CIRCUMFERENCE_MM, profile.wheelCircumferenceMm)
        assertEquals(RidingPosition.HOODS, profile.ridingPosition)
        assertEquals(Surface.ROAD, profile.surface)
        assertNull(profile.hrMaxBpm)
        assertNull(profile.ftpW)
    }

    @Test
    fun `ride settings default to auto pause on, reminders on, auto dim, metric`() {
        val settings = RideSettings()

        assertTrue(settings.isAutoPauseEnabled)
        assertEquals(AutoPauseConfig.DELAY_DEFAULT_S, settings.autoPauseDelayS)
        assertEquals(ReminderConfig.DRINK_INTERVAL_DEFAULT_MIN, settings.drinkIntervalMin)
        assertEquals(ReminderConfig.EAT_INTERVAL_DEFAULT_MIN, settings.eatIntervalMin)
        assertEquals(ScreenMode.AUTO_DIM, settings.screenMode)
        assertEquals(UnitSystem.METRIC, settings.unitSystem)
        assertNull(settings.autoLapM)
    }

    @Test
    fun `missing values in a sample are null, never zero`() {
        val sample = RideSample(timeMs = 0)

        assertNull(sample.speedMps)
        assertNull(sample.heartRateBpm)
        assertNull(sample.powerW)
        assertEquals(0.0, sample.distanceM)
    }

    @Test
    fun `totals start empty`() {
        val totals = RideTotals()

        assertEquals(0.0, totals.distanceM)
        assertNull(totals.normalizedPowerW)
    }

    @Test
    fun `battery modes are ordered by severity`() {
        assertEquals(
            listOf(BatteryMode.NORMAL, BatteryMode.SAVER, BatteryMode.CRITICAL, BatteryMode.EMERGENCY),
            BatteryMode.entries.sortedBy { it.ordinal },
        )
    }
}
