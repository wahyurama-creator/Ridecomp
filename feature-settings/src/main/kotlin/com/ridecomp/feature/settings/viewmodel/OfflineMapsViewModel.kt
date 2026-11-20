package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.map.MapCacheLimit
import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.MapRegion
import com.ridecomp.core.engine.map.MapRegionState
import com.ridecomp.core.engine.map.OfflineMap
import com.ridecomp.core.engine.map.OfflineMapLimit
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.core.usecase.ClearMapCacheUseCase
import com.ridecomp.core.usecase.DeleteMapRegionUseCase
import com.ridecomp.core.usecase.DeleteOfflineMapUseCase
import com.ridecomp.core.usecase.GetFreeStorageUseCase
import com.ridecomp.core.usecase.GetMapCacheInfoUseCase
import com.ridecomp.core.usecase.ImportOfflineMapUseCase
import com.ridecomp.core.usecase.MapPackDownloads
import com.ridecomp.core.usecase.ObserveMapMobileDataUseCase
import com.ridecomp.core.usecase.ObserveMapRegionsUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.SetMapCacheLimitUseCase
import com.ridecomp.core.usecase.SetMapMobileDataUseCase
import com.ridecomp.core.util.Outcome
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
data class OfflineMapsUiState(
    val maps: ImmutableList<OfflineMap> = persistentListOf(),
    val freeBytes: Long? = null,
    val isImporting: Boolean = false,
    val error: MapImportError? = null,
    val deleteCandidate: OfflineMap? = null,
    val isLoaded: Boolean = false,
    /** When the online map may load tiles (D-90). */
    val onlineMapMode: OnlineMapMode = OnlineMapMode.ALWAYS,
    /** Space the tile cache uses now (T11.11); null until read. */
    val cacheBytes: Long? = null,
    /** The cache holds tiles; "clear" is offered only then. */
    val hasCachedTiles: Boolean = false,
    val cacheLimit: MapCacheLimit = MapCacheLimit.DEFAULT,
    /** Space all offline maps may use (T11.14). */
    val offlineMapLimit: OfflineMapLimit = OfflineMapLimit.DEFAULT,
    val isClearCacheAsked: Boolean = false,
    val isClearingCache: Boolean = false,
    /** Province maps of the index (T11.12), the rider's region first. */
    val regions: ImmutableList<MapRegionState> = persistentListOf(),
    val isMobileDataAllowed: Boolean = false,
    val regionDeleteCandidate: MapRegion? = null,
    val regionVersion: String = "",
) {
    val usedBytes: Long get() = maps.sumOf { it.sizeBytes }

    /** Imported files only: downloaded province maps are listed with their region. */
    val importedMaps: List<OfflineMap>
        get() {
            val packNames = regions.flatMap { state -> state.region.files.map { it.mapName } }.toSet()
            return maps.filterNot { it.name in packNames }
        }
}

sealed interface OfflineMapsUiEvent {
    /** A document was picked with SAF; [fallbackName] names it when its own name is unusable. */
    data class Import(val uri: String, val fallbackName: String) : OfflineMapsUiEvent

    data class AskDelete(val map: OfflineMap) : OfflineMapsUiEvent

    data object ConfirmDelete : OfflineMapsUiEvent

    data object DismissDelete : OfflineMapsUiEvent

    data object ErrorShown : OfflineMapsUiEvent

    data class OnlineMode(val mode: OnlineMapMode) : OfflineMapsUiEvent

    data class CacheLimit(val limit: MapCacheLimit) : OfflineMapsUiEvent

    data class OfflineLimit(val limit: OfflineMapLimit) : OfflineMapsUiEvent

    data object AskClearCache : OfflineMapsUiEvent

    data object ConfirmClearCache : OfflineMapsUiEvent

    data object DismissClearCache : OfflineMapsUiEvent

    /** Download, update or resume a province map (T11.12). */
    data class DownloadRegion(val region: MapRegion) : OfflineMapsUiEvent

    data class CancelRegion(val region: MapRegion) : OfflineMapsUiEvent

    data class AskDeleteRegion(val region: MapRegion) : OfflineMapsUiEvent

    data object ConfirmDeleteRegion : OfflineMapsUiEvent

    data object DismissDeleteRegion : OfflineMapsUiEvent

    data class MobileData(val isAllowed: Boolean) : OfflineMapsUiEvent
}

/** Use cases of the offline maps screen. */
data class OfflineMapsUseCases(
    val observe: ObserveOfflineMapsUseCase,
    val import: ImportOfflineMapUseCase,
    val delete: DeleteOfflineMapUseCase,
    val freeStorage: GetFreeStorageUseCase,
    val observeSettings: ObserveRideSettingsUseCase,
    val saveSettings: SaveRideSettingsUseCase,
    val cacheInfo: GetMapCacheInfoUseCase,
    val setCacheLimit: SetMapCacheLimitUseCase,
    val clearCache: ClearMapCacheUseCase,
    val regions: OfflineMapRegionUseCases,
)

/** Province maps (T11.12). */
data class OfflineMapRegionUseCases(
    val observe: ObserveMapRegionsUseCase,
    val downloads: MapPackDownloads,
    val delete: DeleteMapRegionUseCase,
    val observeMobileData: ObserveMapMobileDataUseCase,
    val setMobileData: SetMapMobileDataUseCase,
)

/**
 * Maps (T3.4, D-90; 13-UI-SCREENS #26): the online map setting, the tile cache with its limit and "clear"
 * (T11.11), province maps to download (T11.12), and offline region files to import, list and delete with
 * confirmation.
 */
class OfflineMapsViewModel(private val useCases: OfflineMapsUseCases) :
    UdfViewModel<OfflineMapsUiState, OfflineMapsUiEvent, NoEffect>(OfflineMapsUiState()) {
    init {
        viewModelScope.launch {
            useCases.observeSettings().collect { settings ->
                updateState {
                    copy(
                        onlineMapMode = settings.onlineMapMode,
                        cacheLimit = settings.mapCacheLimit,
                        offlineMapLimit = settings.offlineMapLimit,
                    )
                }
            }
        }
        refreshCache()
        viewModelScope.launch {
            useCases.observe().collect { maps ->
                updateState { copy(maps = maps.toImmutableList(), isLoaded = true) }
                refreshFree()
            }
        }
        viewModelScope.launch {
            useCases.regions.observe().collect { all ->
                // The rider's region first, then by name.
                val sorted = all.regions.sortedWith(
                    compareByDescending<MapRegionState> { it.isHere }.thenBy { it.region.name },
                )
                updateState { copy(regions = sorted.toImmutableList(), regionVersion = all.version) }
            }
        }
        viewModelScope.launch {
            useCases.regions.observeMobileData().collect { updateState { copy(isMobileDataAllowed = it) } }
        }
    }

    override fun onEvent(event: OfflineMapsUiEvent) {
        when (event) {
            is OfflineMapsUiEvent.Import -> import(event)
            is OfflineMapsUiEvent.AskDelete -> updateState { copy(deleteCandidate = event.map) }
            OfflineMapsUiEvent.ConfirmDelete -> {
                val map = currentState.deleteCandidate ?: return
                updateState { copy(deleteCandidate = null) }
                viewModelScope.launch { useCases.delete(map.name) }
            }
            OfflineMapsUiEvent.DismissDelete -> updateState { copy(deleteCandidate = null) }
            OfflineMapsUiEvent.ErrorShown -> updateState { copy(error = null) }
            is OfflineMapsUiEvent.OnlineMode -> viewModelScope.launch {
                useCases.saveSettings(useCases.observeSettings().first().copy(onlineMapMode = event.mode))
            }
            is OfflineMapsUiEvent.CacheLimit -> viewModelScope.launch { useCases.setCacheLimit(event.limit) }
            is OfflineMapsUiEvent.OfflineLimit -> viewModelScope.launch {
                useCases.saveSettings(useCases.observeSettings().first().copy(offlineMapLimit = event.limit))
            }
            OfflineMapsUiEvent.AskClearCache -> updateState { copy(isClearCacheAsked = true) }
            OfflineMapsUiEvent.DismissClearCache -> updateState { copy(isClearCacheAsked = false) }
            OfflineMapsUiEvent.ConfirmClearCache -> clearCache()
            is OfflineMapsUiEvent.DownloadRegion, is OfflineMapsUiEvent.CancelRegion,
            is OfflineMapsUiEvent.AskDeleteRegion, OfflineMapsUiEvent.DismissDeleteRegion,
            OfflineMapsUiEvent.ConfirmDeleteRegion, is OfflineMapsUiEvent.MobileData,
            -> onRegionEvent(event)
        }
    }

    /** Province maps (T11.12). */
    private fun onRegionEvent(event: OfflineMapsUiEvent) {
        val regions = useCases.regions
        when (event) {
            is OfflineMapsUiEvent.DownloadRegion -> regions.downloads.start(event.region, currentState.regionVersion)
            is OfflineMapsUiEvent.CancelRegion -> regions.downloads.cancel(event.region)
            is OfflineMapsUiEvent.AskDeleteRegion -> updateState { copy(regionDeleteCandidate = event.region) }
            OfflineMapsUiEvent.DismissDeleteRegion -> updateState { copy(regionDeleteCandidate = null) }
            OfflineMapsUiEvent.ConfirmDeleteRegion -> currentState.regionDeleteCandidate?.let { region ->
                updateState { copy(regionDeleteCandidate = null) }
                viewModelScope.launch { regions.delete(region) }
            }
            is OfflineMapsUiEvent.MobileData -> viewModelScope.launch { regions.setMobileData(event.isAllowed) }
            else -> Unit
        }
    }

    private fun clearCache() {
        updateState { copy(isClearCacheAsked = false, isClearingCache = true) }
        viewModelScope.launch {
            useCases.clearCache()
            updateState { copy(isClearingCache = false) }
            refreshCache()
        }
    }

    private fun refreshCache() {
        viewModelScope.launch {
            val info = useCases.cacheInfo()
            updateState { copy(cacheBytes = info.sizeBytes, hasCachedTiles = info.hasTiles) }
        }
    }

    private fun import(event: OfflineMapsUiEvent.Import) {
        updateState { copy(isImporting = true, error = null) }
        viewModelScope.launch {
            val result = useCases.import(event.uri, event.fallbackName)
            updateState { copy(isImporting = false, error = (result as? Outcome.Failure)?.error) }
        }
    }

    private fun refreshFree() {
        viewModelScope.launch {
            val free = useCases.freeStorage()
            updateState { copy(freeBytes = free) }
        }
    }
}
