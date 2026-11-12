package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.HandleRecoveredRideUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.RecoveryChoice
import com.ridecomp.core.usecase.isRideInProgress
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    /** A ride interrupted by a crash/kill, offered for resume/save/discard (T1.6). */
    val recoverable: Activity? = null,
    val lastRide: Activity? = null,
    val isRideInProgress: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
)

sealed interface HomeUiEvent {
    data object StartRide : HomeUiEvent

    data object OpenRide : HomeUiEvent

    data class Recover(val choice: RecoveryChoice) : HomeUiEvent
}

sealed interface HomeEffect {
    data object OpenPreRide : HomeEffect

    data object OpenRide : HomeEffect
}

class HomeViewModel(
    private val getRecoverable: GetRecoverableRideUseCase,
    private val handleRecovered: HandleRecoveredRideUseCase,
    observeHistory: ObserveHistoryPageUseCase,
    private val observeLive: ObserveLiveRideUseCase,
    observeSettings: ObserveRideSettingsUseCase,
) : UdfViewModel<HomeUiState, HomeUiEvent, HomeEffect>(HomeUiState()) {
    init {
        viewModelScope.launch {
            combine(observeLive(), observeHistory(page = 0, pageSize = 1), observeSettings()) { live, last, settings ->
                Triple(live.isRideInProgress(), last.firstOrNull(), settings.unitSystem)
            }.collect { (inProgress, last, units) ->
                // The service's own ride is "recording" in the database too: never offer it for recovery.
                val recoverable = if (inProgress) null else getRecoverable()
                updateState {
                    copy(recoverable = recoverable, lastRide = last, isRideInProgress = inProgress, unitSystem = units)
                }
            }
        }
    }

    override fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.StartRide -> sendEffect(HomeEffect.OpenPreRide)
            HomeUiEvent.OpenRide -> sendEffect(HomeEffect.OpenRide)
            is HomeUiEvent.Recover -> recover(event.choice)
        }
    }

    private fun recover(choice: RecoveryChoice) {
        val ride = currentState.recoverable ?: return
        updateState { copy(recoverable = null) }
        viewModelScope.launch {
            handleRecovered(ride.id, choice)
            if (choice == RecoveryChoice.RESUME) sendEffect(HomeEffect.OpenRide)
        }
    }
}
