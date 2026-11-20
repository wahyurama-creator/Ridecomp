package com.ridecomp.core.usecase

import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.OfflineMapLimit
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.util.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class OfflineMapUseCasesTest {
    private val repository = FakeOfflineMapRepository()
    private val settings = FakeSettingsRepository()

    @Test
    fun `the document name becomes a safe file name`() = runTest {
        repository.names["content://a"] = "../Jawa Tengah (2026).pmtiles"

        val map = (ImportOfflineMapUseCase(repository, settings)("content://a", "map") as Outcome.Success).value

        assertEquals("Jawa_Tengah__2026", map.name)
        assertEquals(listOf(map), ObserveOfflineMapsUseCase(repository)().first())
    }

    @Test
    fun `without a usable document name the fallback is used, and delete removes the map`() = runTest {
        repository.names["content://b"] = "???.pmtiles"

        val map = (
            ImportOfflineMapUseCase(
                repository,
                settings,
            )("content://b", "Peta offline") as Outcome.Success
            ).value
        DeleteOfflineMapUseCase(repository)(map.name)

        assertEquals("Peta_offline", map.name)
        assertTrue(ObserveOfflineMapsUseCase(repository)().first().isEmpty())
    }

    @Test
    fun `imports stay within the total offline map limit, a same-name map is replaced`() = runTest {
        val import = ImportOfflineMapUseCase(repository, settings)
        repository.importSize = 400_000_000L
        assertTrue(import("content://a", "jateng") is Outcome.Success)
        assertTrue(import("content://b", "jatim") is Outcome.Success)

        // 800 MB stored: another 400 MB is over the 1 GB default, the same name replaces its file.
        assertEquals(MapImportError.TOTAL_LIMIT, (import("content://c", "bali") as Outcome.Failure).error)
        assertTrue(import("content://a", "jateng") is Outcome.Success)

        settings.saveRideSettings(settings.getRideSettings().copy(offlineMapLimit = OfflineMapLimit.LARGE))
        assertTrue(import("content://c", "bali") is Outcome.Success)
    }
}
