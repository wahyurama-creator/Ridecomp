package com.ridecomp.feature.settings

import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.usecase.DeleteOfflineMapUseCase
import com.ridecomp.core.usecase.GetFreeStorageUseCase
import com.ridecomp.core.usecase.ImportOfflineMapUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.feature.settings.viewmodel.OfflineMapsUiEvent
import com.ridecomp.feature.settings.viewmodel.OfflineMapsUseCases
import com.ridecomp.feature.settings.viewmodel.OfflineMapsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMapsViewModelTest {
    private val repository = FakeOfflineMapRepository(freeSpace = 5_000)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = OfflineMapsViewModel(
        OfflineMapsUseCases(
            ObserveOfflineMapsUseCase(repository),
            ImportOfflineMapUseCase(repository),
            DeleteOfflineMapUseCase(repository),
            GetFreeStorageUseCase(repository),
        ),
    )

    @Test
    fun `import adds a region, delete needs confirmation`() {
        repository.names["content://jateng"] = "jawa-tengah.pmtiles"
        val viewModel = viewModel()

        viewModel.onEvent(OfflineMapsUiEvent.Import("content://jateng", "map"))
        val map = viewModel.uiState.value.maps.single()
        assertEquals("jawa-tengah", map.name)
        assertEquals(5_000L, viewModel.uiState.value.freeBytes)

        viewModel.onEvent(OfflineMapsUiEvent.AskDelete(map))
        viewModel.onEvent(OfflineMapsUiEvent.DismissDelete)
        assertEquals(1, viewModel.uiState.value.maps.size)
        viewModel.onEvent(OfflineMapsUiEvent.AskDelete(map))
        viewModel.onEvent(OfflineMapsUiEvent.ConfirmDelete)
        assertTrue(viewModel.uiState.value.maps.isEmpty())
    }

    @Test
    fun `a refused file shows its reason until acknowledged`() {
        repository.failure = MapImportError.NOT_VECTOR
        val viewModel = viewModel()

        viewModel.onEvent(OfflineMapsUiEvent.Import("content://x", "map"))
        assertEquals(MapImportError.NOT_VECTOR, viewModel.uiState.value.error)
        viewModel.onEvent(OfflineMapsUiEvent.ErrorShown)
        assertNull(viewModel.uiState.value.error)
    }
}
