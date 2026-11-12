package com.ridecomp.feature.history.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@Immutable
data class HistoryUiState(
    val rides: ImmutableList<Activity> = persistentListOf(),
    val totalCount: Int = 0,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isLoaded: Boolean = false,
) {
    val hasMore: Boolean get() = rides.size < totalCount
}

sealed interface HistoryUiEvent {
    /** The list was scrolled near its end. */
    data object LoadMore : HistoryUiEvent
}

/** Saved rides, newest first, loaded 20 at a time (02-DESIGN §3.7, DATA-04). */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    observePage: ObserveHistoryPageUseCase,
    observeCount: ObserveHistoryCountUseCase,
    observeSettings: ObserveRideSettingsUseCase,
) : UdfViewModel<HistoryUiState, HistoryUiEvent, NoEffect>(HistoryUiState()) {
    private val loadedCount = MutableStateFlow(ActivityConfig.HISTORY_PAGE_SIZE)

    init {
        viewModelScope.launch {
            combine(
                loadedCount.flatMapLatest { count -> observePage(page = 0, pageSize = count) },
                observeCount(),
                observeSettings(),
            ) { rides, total, settings ->
                HistoryUiState(rides.toImmutableList(), total, settings.unitSystem, isLoaded = true)
            }.collect { state -> updateState { state } }
        }
    }

    override fun onEvent(event: HistoryUiEvent) {
        when (event) {
            HistoryUiEvent.LoadMore -> if (currentState.hasMore) loadedCount.value += ActivityConfig.HISTORY_PAGE_SIZE
        }
    }
}
