package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.BackupReminder
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.HandleRecoveredRideUseCase
import com.ridecomp.core.usecase.ObserveBackupReminderUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.RecoveryChoice
import com.ridecomp.core.usecase.SnoozeBackupReminderUseCase
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
    /** "Last backup N days ago" card (12-FEATURE-SPECS §12.4); null when not due. */
    val backupReminder: BackupReminder? = null,
)

sealed interface HomeUiEvent {
    data object StartRide : HomeUiEvent

    data object OpenRide : HomeUiEvent

    data class Recover(val choice: RecoveryChoice) : HomeUiEvent

    data object OpenBackup : HomeUiEvent

    /** Hides the backup card for 7 days. */
    data object SnoozeBackupReminder : HomeUiEvent
}

sealed interface HomeEffect {
    data object OpenPreRide : HomeEffect

    data object OpenRide : HomeEffect

    data object OpenBackup : HomeEffect
}

/** Use cases of the home screen, grouped to keep the constructor short. */
data class HomeUseCases(
    val getRecoverable: GetRecoverableRideUseCase,
    val handleRecovered: HandleRecoveredRideUseCase,
    val observeHistory: ObserveHistoryPageUseCase,
    val observeLive: ObserveLiveRideUseCase,
    val observeSettings: ObserveRideSettingsUseCase,
    val observeBackupReminder: ObserveBackupReminderUseCase,
    val snoozeBackupReminder: SnoozeBackupReminderUseCase,
)

class HomeViewModel(private val useCases: HomeUseCases) :
    UdfViewModel<HomeUiState, HomeUiEvent, HomeEffect>(HomeUiState()) {
    init {
        viewModelScope.launch {
            useCases.observeBackupReminder().collect { updateState { copy(backupReminder = it) } }
        }
        viewModelScope.launch {
            val history = useCases.observeHistory(page = 0, pageSize = 1)
            combine(useCases.observeLive(), history, useCases.observeSettings()) { live, last, settings ->
                Triple(live.isRideInProgress(), last.firstOrNull(), settings.unitSystem)
            }.collect { (inProgress, last, units) ->
                // The service's own ride is "recording" in the database too: never offer it for recovery.
                val recoverable = if (inProgress) null else useCases.getRecoverable()
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
            HomeUiEvent.OpenBackup -> sendEffect(HomeEffect.OpenBackup)
            HomeUiEvent.SnoozeBackupReminder -> viewModelScope.launch { useCases.snoozeBackupReminder() }
        }
    }

    private fun recover(choice: RecoveryChoice) {
        val ride = currentState.recoverable ?: return
        updateState { copy(recoverable = null) }
        viewModelScope.launch {
            useCases.handleRecovered(ride.id, choice)
            if (choice == RecoveryChoice.RESUME) sendEffect(HomeEffect.OpenRide)
        }
    }
}
