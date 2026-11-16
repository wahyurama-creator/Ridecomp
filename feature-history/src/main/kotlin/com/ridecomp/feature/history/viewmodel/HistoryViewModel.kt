package com.ridecomp.feature.history.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.PeriodTotals
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.source.Clock
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveMonthTotalsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveRidesOnStravaUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
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
    /** "BULAN INI" card (13-UI-SCREENS #11). */
    val month: PeriodTotals = PeriodTotals(),
    /** Now, for "Hari ini · 05:31" labels; from the injected clock. */
    val nowMs: Long = 0,
    /** Rides already on Strava (T5.3). */
    val onStrava: ImmutableSet<Long> = persistentSetOf(),
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
    observeMonth: ObserveMonthTotalsUseCase,
    clock: Clock,
    observeOnStrava: ObserveRidesOnStravaUseCase,
) : UdfViewModel<HistoryUiState, HistoryUiEvent, NoEffect>(HistoryUiState()) {
    private val loadedCount = MutableStateFlow(ActivityConfig.HISTORY_PAGE_SIZE)

    init {
        viewModelScope.launch {
            combine(
                loadedCount.flatMapLatest { count -> observePage(page = 0, pageSize = count) },
                observeCount(),
                observeSettings(),
                observeMonth(),
            ) { rides, total, settings, month ->
                HistoryUiState(
                    rides.toImmutableList(),
                    total,
                    settings.unitSystem,
                    isLoaded = true,
                    month,
                    clock.nowMs(),
                )
            }.collect { state -> updateState { state.copy(onStrava = onStrava) } }
        }
        viewModelScope.launch {
            observeOnStrava().collect { ids -> updateState { copy(onStrava = ids.toImmutableSet()) } }
        }
    }

    override fun onEvent(event: HistoryUiEvent) {
        when (event) {
            HistoryUiEvent.LoadMore -> if (currentState.hasMore) loadedCount.value += ActivityConfig.HISTORY_PAGE_SIZE
        }
    }
}
