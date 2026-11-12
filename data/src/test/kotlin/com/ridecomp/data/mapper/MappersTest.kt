package com.ridecomp.data.mapper

import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.LapTrigger
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.Sex
import com.ridecomp.core.model.Surface
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.ActivityEntity
import com.ridecomp.data.db.entity.EventEntity
import com.ridecomp.data.db.entity.SettingEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MappersTest {
    private val entity = ActivityEntity(
        id = 7,
        uuid = "u-7",
        name = "Gowes Pagi",
        status = DbNames.ActivityStatus.FINISHED,
        startedAt = 1_000,
        endedAt = 9_000,
        lastCheckpointAt = 9_000,
    )

    @Test
    fun `activity status round trips through stable strings`() {
        ActivityStatus.entries.forEach { assertEquals(it, it.toDb().toActivityStatus()) }
        assertEquals("recording", ActivityStatus.RECORDING.toDb())
    }

    @Test
    fun `activity with unknown status is skipped`() {
        assertNull(entity.copy(status = "corrupted").toDomain())
    }

    @Test
    fun `totals are copied both ways`() {
        val totals = RideTotals(elapsedS = 100, movingS = 90, distanceM = 800.0, ascentM = 12.0, avgSpeedMps = 8.9)

        val domain = requireNotNull(entity.withTotals(totals).toDomain())

        assertEquals(totals, domain.totals)
        assertEquals("Gowes Pagi", domain.name)
        assertEquals(9_000L, domain.endedAtMs)
    }

    @Test
    fun `sample round trips`() {
        val sample = RideSample(1_000, -7.1, 110.2, 120.0, 4.0, 15.0, 7.5, 140, 85, 210, 190, 29.5)

        assertEquals(sample, sample.toEntity(3).toDomain())
        assertEquals(3L, sample.toEntity(3).activityId)
    }

    @Test
    fun `events use lowercase names and unknown types are skipped`() {
        val event = RideEvent(5, RideEventType.AUTO_PAUSE)

        assertEquals("auto_pause", event.toEntity(1).type)
        assertEquals(event, event.toEntity(1).toDomain())
        assertNull(EventEntity(activityId = 1, timeMs = 5, type = "future_type").toDomain())
    }

    @Test
    fun `lap round trips`() {
        val lap = Lap(2, 0, 60_000, 500.0, 60, 8.3, 10.0, 4.0, LapTrigger.DISTANCE, 150, 170, 90, 200, 400)

        assertEquals(lap, lap.toEntity(1).toDomain())
        assertEquals("distance", lap.toEntity(1).trigger)
    }

    @Test
    fun `profile round trips`() {
        val profile =
            RiderProfile(72.0, 8.5, 2_096, 188, 52, 230, 1990, Sex.FEMALE, RidingPosition.DROPS, Surface.GRAVEL)

        assertEquals(profile, profile.toEntity().toDomain())
    }

    @Test
    fun `ride settings round trip including switched off options`() {
        val settings = RideSettings(
            isAutoPauseEnabled = false,
            autoPauseDelayS = 10,
            autoLapM = 5_000.0,
            drinkIntervalMin = null,
            eatIntervalMin = 30,
            screenMode = ScreenMode.OFF,
            isBatterySaverEnabled = false,
            isVolumeButtonsEnabled = true,
            unitSystem = UnitSystem.IMPERIAL,
        )

        assertEquals(settings, settings.toEntities().toRideSettings())
    }

    @Test
    fun `missing or unreadable settings fall back to defaults`() {
        val stored = listOf(
            SettingEntity(SettingsKeys.AUTO_PAUSE_DELAY_S, "abc"),
            SettingEntity(SettingsKeys.SCREEN_MODE, "hologram"),
            SettingEntity(SettingsKeys.AUTO_PAUSE_SPEED_MPS, "NaN"),
        )

        assertEquals(RideSettings(), stored.toRideSettings())
        assertEquals(RideSettings(), emptyList<SettingEntity>().toRideSettings())
    }
}
