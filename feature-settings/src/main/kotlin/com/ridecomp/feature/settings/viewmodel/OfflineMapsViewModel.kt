package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.map.MapCacheLimit
import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.OfflineMap
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.core.usecase.ClearMapCacheUseCase
import com.ridecomp.core.usecase.DeleteOfflineMapUseCase
import com.ridecomp.core.usecase.GetFreeStorageUseCase
import com.ridecomp.core.usecase.GetMapCacheInfoUseCase
import com.ridecomp.core.usecase.ImportOfflineMapUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.SetMapCacheLimitUseCase
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
    val isClearCacheAsked: Boolean = false,
    val isClearingCache: Boolean = false,
) {
    val usedBytes: Long get() = maps.sumOf { it.sizeBytes }
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

    data object AskClearCache : OfflineMapsUiEvent

    data object ConfirmClearCache : OfflineMapsUiEvent

    data object DismissClearCache : OfflineMapsUiEvent
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
)

/**
 * Maps (T3.4, D-90; 13-UI-SCREENS #26): the online map setting, the tile cache with its limit and "clear"
 * (T11.11), and offline region files to import, list and delete with confirmation.
 */
class OfflineMapsViewModel(private val useCases: OfflineMapsUseCases) :
    UdfViewModel<OfflineMapsUiState, OfflineMapsUiEvent, NoEffect>(OfflineMapsUiState()) {
    init {
        viewModelScope.launch {
            useCases.observeSettings().collect { settings ->
                updateState { copy(onlineMapMode = settings.onlineMapMode, cacheLimit = settings.mapCacheLimit) }
            }
        }
        refreshCache()
        viewModelScope.launch {
            useCases.observe().collect { maps ->
                updateState { copy(maps = maps.toImmutableList(), isLoaded = true) }
                refreshFree()
            }
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
            OfflineMapsUiEvent.AskClearCache -> updateState { copy(isClearCacheAsked = true) }
            OfflineMapsUiEvent.DismissClearCache -> updateState { copy(isClearCacheAsked = false) }
            OfflineMapsUiEvent.ConfirmClearCache -> clearCache()
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
