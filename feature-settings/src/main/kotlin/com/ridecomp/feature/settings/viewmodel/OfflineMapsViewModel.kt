package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.OfflineMap
import com.ridecomp.core.usecase.DeleteOfflineMapUseCase
import com.ridecomp.core.usecase.GetFreeStorageUseCase
import com.ridecomp.core.usecase.ImportOfflineMapUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.core.util.Outcome
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Immutable
data class OfflineMapsUiState(
    val maps: ImmutableList<OfflineMap> = persistentListOf(),
    val freeBytes: Long? = null,
    val isImporting: Boolean = false,
    val error: MapImportError? = null,
    val deleteCandidate: OfflineMap? = null,
    val isLoaded: Boolean = false,
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
}

/** Use cases of the offline maps screen. */
data class OfflineMapsUseCases(
    val observe: ObserveOfflineMapsUseCase,
    val import: ImportOfflineMapUseCase,
    val delete: DeleteOfflineMapUseCase,
    val freeStorage: GetFreeStorageUseCase,
)

/** Offline maps manager (T3.4, 13-UI-SCREENS #26): import a region file, list, delete with confirmation. */
class OfflineMapsViewModel(private val useCases: OfflineMapsUseCases) :
    UdfViewModel<OfflineMapsUiState, OfflineMapsUiEvent, NoEffect>(OfflineMapsUiState()) {
    init {
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
