package com.ridecomp.core.usecase

import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.util.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class OfflineMapUseCasesTest {
    private val repository = FakeOfflineMapRepository()

    @Test
    fun `the document name becomes a safe file name`() = runTest {
        repository.names["content://a"] = "../Jawa Tengah (2026).pmtiles"

        val map = (ImportOfflineMapUseCase(repository)("content://a", "map") as Outcome.Success).value

        assertEquals("Jawa_Tengah__2026", map.name)
        assertEquals(listOf(map), ObserveOfflineMapsUseCase(repository)().first())
    }

    @Test
    fun `without a usable document name the fallback is used, and delete removes the map`() = runTest {
        repository.names["content://b"] = "???.pmtiles"

        val map = (ImportOfflineMapUseCase(repository)("content://b", "Peta offline") as Outcome.Success).value
        DeleteOfflineMapUseCase(repository)(map.name)

        assertEquals("Peta_offline", map.name)
        assertTrue(ObserveOfflineMapsUseCase(repository)().first().isEmpty())
    }
}
