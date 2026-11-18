package com.ridecomp.core.usecase

import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.BuiltInProfiles
import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.Surface
import com.ridecomp.core.model.withProfile
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ActivityProfileUseCasesTest {
    private val repository = FakeActivityProfileRepository()
    private val known = setOf("speed", "distance", "clock", "grade", "heart_rate")

    @Test
    fun `built-ins cover every kind but custom, the folding bike last, and indoor has no gps`() {
        assertEquals(ActivityKind.entries - ActivityKind.CUSTOM, BuiltInProfiles.ALL.map { it.kind })
        assertEquals(BuiltInProfiles.ALL, BuiltInProfiles.FIRST + BuiltInProfiles.FOLDING)
        assertEquals(listOf(ActivityKind.FOLDING), BuiltInProfiles.FOLDING.map { it.kind })
        assertEquals(listOf(ActivityKind.INDOOR), BuiltInProfiles.ALL.filterNot { it.isGpsEnabled }.map { it.kind })
    }

    @Test
    fun `the last used profile is preselected and falls back to the first`() = runTest {
        val choice = ObserveProfileChoiceUseCase(repository)

        assertEquals(ActivityKind.ROAD, choice().first().selected?.kind)
        SelectActivityProfileUseCase(repository)(4)
        assertEquals(ActivityKind.INDOOR, choice().first().selected?.kind)

        repository.delete(4)
        assertEquals(ActivityKind.ROAD, choice().first().selected?.kind)
        assertEquals(ActivityKind.ROAD, GetRideProfileUseCase(repository)(id = 99)?.kind)
    }

    @Test
    fun `profile overrides apply on top of the general settings`() {
        val general = RideSettings(isAutoPauseEnabled = true, autoLapM = 5_000.0, screenMode = ScreenMode.AUTO_DIM)
        val inherit = ActivityProfile(kind = ActivityKind.ROAD)
        val custom = inherit.copy(
            isAutoPauseEnabled = false,
            autoLapM = ActivityProfile.AUTO_LAP_OFF,
            screenMode = ScreenMode.ALWAYS_ON,
            surface = Surface.GRAVEL,
            ridingPosition = RidingPosition.DROPS,
        )

        assertEquals(general, general.withProfile(inherit))
        val applied = general.withProfile(custom)
        assertFalse(applied.isAutoPauseEnabled)
        assertNull(applied.autoLapM)
        assertEquals(ScreenMode.ALWAYS_ON, applied.screenMode)
        assertEquals(10_000.0, general.withProfile(inherit.copy(autoLapM = 10_000.0)).autoLapM)
        assertEquals(Surface.GRAVEL, RiderProfile().withProfile(custom).surface)
        assertEquals(RidingPosition.DROPS, RiderProfile().withProfile(custom).ridingPosition)
    }

    @Test
    fun `duplicate makes a custom copy placed last, only custom profiles can be deleted`() = runTest {
        val copyId = DuplicateActivityProfileUseCase(repository)(id = 3, copyName = "  MTB 2  ")
        val copy = copyId?.let { repository.get(it) }

        assertEquals("MTB 2", copy?.name)
        assertEquals(ActivityKind.CUSTOM, copy?.kind)
        assertEquals(repository.get(3)?.dataPages, copy?.dataPages)
        assertEquals(copy, repository.observeAll().first().last())

        val delete = DeleteActivityProfileUseCase(repository)
        assertFalse(delete(1))
        assertTrue(delete(copyId ?: -1))
        assertNull(DuplicateActivityProfileUseCase(repository)(id = 999, copyName = "x"))
    }

    @Test
    fun `names are trimmed and limited`() = runTest {
        val id = SaveActivityProfileUseCase(repository)(
            ActivityProfile(kind = ActivityKind.CUSTOM, name = " " + "x".repeat(ProfileLimits.NAME_MAX_LENGTH + 5)),
        )

        assertEquals(ProfileLimits.NAME_MAX_LENGTH, repository.get(id)?.name?.length)
    }

    @Test
    fun `a profile's own pages win, reset falls back to the general pages`() = runTest {
        val settings = FakeSettingsRepository()
        val general = DataPagesLayout(listOf(DataPageLayout(listOf("speed"))))
        settings.dataPages.value = general
        val observe = ObserveDataPagesUseCase(settings, repository)

        assertEquals(general, observe(known, profileId = 1).first())
        SaveDataPagesUseCase(settings, repository)(DataPagesLayout(listOf(DataPageLayout(listOf("grade")))), known, 1)
        assertEquals(listOf("grade"), observe(known, profileId = 1).first()?.pages?.single()?.fields)
        assertEquals(general, observe(known).first())

        ResetDataPagesUseCase(settings, repository)(profileId = 1)
        assertEquals(general, observe(known, profileId = 1).first())
    }
}
