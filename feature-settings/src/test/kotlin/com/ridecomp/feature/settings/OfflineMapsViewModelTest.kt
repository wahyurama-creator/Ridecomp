package com.ridecomp.feature.settings

import com.ridecomp.core.engine.map.MapCacheLimit
import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.core.testing.FakeMapCacheSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.usecase.ClearMapCacheUseCase
import com.ridecomp.core.usecase.DeleteOfflineMapUseCase
import com.ridecomp.core.usecase.GetFreeStorageUseCase
import com.ridecomp.core.usecase.GetMapCacheInfoUseCase
import com.ridecomp.core.usecase.ImportOfflineMapUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.SetMapCacheLimitUseCase
import com.ridecomp.feature.settings.viewmodel.OfflineMapsUiEvent
import com.ridecomp.feature.settings.viewmodel.OfflineMapsUseCases
import com.ridecomp.feature.settings.viewmodel.OfflineMapsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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

    private val settings = FakeSettingsRepository()
    private val cache = FakeMapCacheSource(size = CACHE_BYTES)

    private fun viewModel() = OfflineMapsViewModel(
        OfflineMapsUseCases(
            ObserveOfflineMapsUseCase(repository),
            ImportOfflineMapUseCase(repository),
            DeleteOfflineMapUseCase(repository),
            GetFreeStorageUseCase(repository),
            ObserveRideSettingsUseCase(settings),
            SaveRideSettingsUseCase(settings),
            GetMapCacheInfoUseCase(cache, settings),
            SetMapCacheLimitUseCase(settings),
            ClearMapCacheUseCase(cache, settings),
        ),
    )

    @Test
    fun `the map cache shows its size, its limit is saved and clearing asks first`() = runTest {
        settings.setMapCacheFilled(true)
        val viewModel = viewModel()
        assertEquals(CACHE_BYTES, viewModel.uiState.value.cacheBytes)
        assertTrue(viewModel.uiState.value.hasCachedTiles)
        assertEquals(MapCacheLimit.MEDIUM, viewModel.uiState.value.cacheLimit)

        viewModel.onEvent(OfflineMapsUiEvent.CacheLimit(MapCacheLimit.SMALL))
        assertEquals(MapCacheLimit.SMALL, settings.getRideSettings().mapCacheLimit)
        assertEquals(MapCacheLimit.SMALL, viewModel.uiState.value.cacheLimit)

        viewModel.onEvent(OfflineMapsUiEvent.AskClearCache)
        assertTrue(viewModel.uiState.value.isClearCacheAsked)
        assertEquals(0, cache.clears)
        viewModel.onEvent(OfflineMapsUiEvent.ConfirmClearCache)

        assertEquals(1, cache.clears)
        assertEquals(0L, viewModel.uiState.value.cacheBytes)
        assertFalse(viewModel.uiState.value.hasCachedTiles)
        assertFalse(settings.mapCacheFilled.value)
    }

    @Test
    fun `the online map mode is saved`() = runTest {
        val viewModel = viewModel()
        assertEquals(OnlineMapMode.ALWAYS, viewModel.uiState.value.onlineMapMode)

        viewModel.onEvent(OfflineMapsUiEvent.OnlineMode(OnlineMapMode.WIFI_ONLY))

        assertEquals(OnlineMapMode.WIFI_ONLY, settings.getRideSettings().onlineMapMode)
        assertEquals(OnlineMapMode.WIFI_ONLY, viewModel.uiState.value.onlineMapMode)
    }

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

    private companion object {
        const val CACHE_BYTES = 42_000_000L
    }
}
