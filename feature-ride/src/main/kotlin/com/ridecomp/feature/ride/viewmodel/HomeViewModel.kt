package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.ActivePlan
import com.ridecomp.core.usecase.BackupReminder
import com.ridecomp.core.usecase.CheckGpsAvailabilityUseCase
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.GpsAvailability
import com.ridecomp.core.usecase.HandleRecoveredRideUseCase
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObserveBackupReminderUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.ReadBatteryUseCase
import com.ridecomp.core.usecase.RecoveryChoice
import com.ridecomp.core.usecase.SnoozeBackupReminderUseCase
import com.ridecomp.core.usecase.isRideInProgress
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
    /** Phone battery for the header (13-UI-SCREENS #3). */
    val battery: BatteryStatus? = null,
    /** Saved sensors for the status tiles: last known battery, never a live connection. */
    val sensors: ImmutableList<SavedSensor> = persistentListOf(),
    /** GPS permission and switch only; GPS itself stays off until a ride starts (BAT-01). */
    val gps: GpsAvailability = GpsAvailability.READY,
    /** The training plan followed and today in it (T7.4b); null = none. */
    val plan: ActivePlan? = null,
)

sealed interface HomeUiEvent {
    data object StartRide : HomeUiEvent

    data object OpenRide : HomeUiEvent

    data class Recover(val choice: RecoveryChoice) : HomeUiEvent

    data object OpenBackup : HomeUiEvent

    /** Hides the backup card for 7 days. */
    data object SnoozeBackupReminder : HomeUiEvent

    /** Screen resumed: re-read battery and GPS availability. */
    data object Refresh : HomeUiEvent
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
    val device: HomeDeviceUseCases,
    val observeActivePlan: ObserveActivePlanUseCase,
)

/** Phone and sensor status shown on the home tiles (13-UI-SCREENS #3). */
data class HomeDeviceUseCases(
    val readBattery: ReadBatteryUseCase,
    val observeSensors: ObserveSavedSensorsUseCase,
    val checkGps: CheckGpsAvailabilityUseCase,
)

class HomeViewModel(private val useCases: HomeUseCases) :
    UdfViewModel<HomeUiState, HomeUiEvent, HomeEffect>(HomeUiState()) {
    init {
        refreshDevice()
        viewModelScope.launch { useCases.observeActivePlan().collect { plan -> updateState { copy(plan = plan) } } }
        viewModelScope.launch {
            useCases.observeBackupReminder().collect { updateState { copy(backupReminder = it) } }
        }
        viewModelScope.launch {
            useCases.device.observeSensors().collect { updateState { copy(sensors = it.toImmutableList()) } }
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
            HomeUiEvent.Refresh -> refreshDevice()
        }
    }

    /** Battery and GPS permission/switch can change while away (settings, charging): re-read on resume. */
    private fun refreshDevice() {
        updateState { copy(battery = useCases.device.readBattery(), gps = useCases.device.checkGps()) }
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
