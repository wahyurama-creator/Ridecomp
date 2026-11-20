package com.ridecomp.core.usecase

import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.engine.update.AppVersion
import com.ridecomp.core.engine.update.UpdateCheckResult
import com.ridecomp.core.engine.update.UpdateConfig
import com.ridecomp.core.engine.update.UpdatePreferences
import com.ridecomp.core.testing.FakeAppInfoSource
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeReleaseSource
import com.ridecomp.core.testing.FakeUpdateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** In-app update check (T10.9, D-103). */
class UpdateUseCasesTest {
    private val repository = FakeUpdateRepository()
    private val releases = FakeReleaseSource()
    private val app = FakeAppInfoSource("0.9.1")
    private val clock = FakeClock(START_MS)
    private val check = CheckForUpdateUseCase(repository, releases, app, clock)

    @Test
    fun `versions compare part by part and only plain versions parse`() {
        assertTrue(version("0.9.10") > version("0.9.9"))
        assertTrue(version("v1.0") > version("0.99.99"))
        assertEquals(version("1.0"), version("1.0.0"))
        assertEquals(version("0.9.1"), version("0.9.1-debug"))
        listOf("", "latest", "1..2", "1.2.3.4.5", "1.2;rm", "99999.1").forEach { assertNull(AppVersion.parse(it)) }
        // Map pack releases share the repository (T11.13, D-129): never an app update.
        assertNull(AppVersion.parse("maps-2026-11"))
    }

    @Test
    fun `the automatic check runs once a day and finds a newer release`() = runTest {
        releases.release = AppRelease("0.9.2", PAGE)

        assertEquals(UpdateCheckResult.UPDATE_AVAILABLE, check())
        assertNull(check())
        clock.advanceBy(UpdateConfig.CHECK_INTERVAL_MS)
        assertEquals(UpdateCheckResult.UPDATE_AVAILABLE, check())
        assertEquals(2, releases.requests)
        assertEquals(AppRelease("0.9.2", PAGE), ObserveAvailableUpdateUseCase(repository, app)().first())
    }

    @Test
    fun `switched off, nothing is asked unless the rider checks now`() = runTest {
        repository.state.value = UpdatePreferences(isAutoCheckEnabled = false)
        releases.release = AppRelease("0.9.1", PAGE)

        assertNull(check())
        assertEquals(0, releases.requests)
        assertEquals(UpdateCheckResult.UP_TO_DATE, check(force = true))
    }

    @Test
    fun `a failed check keeps what was known and retries next time`() = runTest {
        assertEquals(UpdateCheckResult.FAILED, check())
        assertNull(repository.state.value.lastCheckMs)
        releases.release = AppRelease("0.9.2", PAGE)
        assertEquals(UpdateCheckResult.UPDATE_AVAILABLE, check())
    }

    @Test
    fun `later hides that version only, an older or same release is never offered`() = runTest {
        val available = ObserveAvailableUpdateUseCase(repository, app)
        repository.saveCheck(START_MS, AppRelease("0.9.2", PAGE))

        DismissUpdateUseCase(repository)("0.9.2")
        assertNull(available().first())
        repository.saveCheck(START_MS, AppRelease("0.9.3", PAGE))
        assertEquals("0.9.3", available().first()?.version)
        repository.saveCheck(START_MS, AppRelease("0.9.1", PAGE))
        assertNull(available().first())
        assertNull(ObserveUpdateSettingsUseCase(repository, app)().first().available)
    }

    private fun version(text: String) = requireNotNull(AppVersion.parse(text))

    private companion object {
        const val START_MS = 1_000_000L
        const val PAGE = "https://github.com/wahyurama-creator/Ridecomp/releases/tag/v0.9.2"
    }
}
