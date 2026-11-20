package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.ActivePlan
import com.ridecomp.core.usecase.BackupReminder
import com.ridecomp.core.usecase.CheckForUpdateUseCase
import com.ridecomp.core.usecase.CheckGpsAvailabilityUseCase
import com.ridecomp.core.usecase.DismissMapRegionOfferUseCase
import com.ridecomp.core.usecase.DismissUpdateUseCase
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.GpsAvailability
import com.ridecomp.core.usecase.HandleRecoveredRideUseCase
import com.ridecomp.core.usecase.MapPackDownloads
import com.ridecomp.core.usecase.MapRegionOffer
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObserveAvailableUpdateUseCase
import com.ridecomp.core.usecase.ObserveBackupReminderUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveMapRegionOfferUseCase
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
    /** A newer RideComp release to download (T10.9); null = none or put off. */
    val update: AppRelease? = null,
    /** "Download your region's map" (T11.12): the region of the last ride when not installed. */
    val mapOffer: MapRegionOffer? = null,
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

    /** "Unduh": open the release page in the browser. */
    data object OpenUpdate : HomeUiEvent

    /** "Nanti": hide this version's card. */
    data object DismissUpdate : HomeUiEvent

    /** Download, update or resume the offered region map (T11.12). */
    data object DownloadMap : HomeUiEvent

    data object CancelMap : HomeUiEvent

    /** "Nanti": not offered again for this region and map version. */
    data object DismissMap : HomeUiEvent
}

sealed interface HomeEffect {
    data object OpenPreRide : HomeEffect

    data object OpenRide : HomeEffect

    data object OpenBackup : HomeEffect

    /** A release page of the project (validated when read, D-103). */
    data class OpenUrl(val url: String) : HomeEffect
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
    val updates: HomeUpdateUseCases,
    val maps: HomeMapUseCases,
)

/** The region map offer (T11.12). */
data class HomeMapUseCases(
    val observeOffer: ObserveMapRegionOfferUseCase,
    val downloads: MapPackDownloads,
    val dismiss: DismissMapRegionOfferUseCase,
)

/** The in-app update check (T10.9). */
data class HomeUpdateUseCases(
    val check: CheckForUpdateUseCase,
    val observe: ObserveAvailableUpdateUseCase,
    val dismiss: DismissUpdateUseCase,
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
        // Opening the app checks for a new release at most once a day, when the rider allows it (D-103).
        viewModelScope.launch { useCases.updates.check() }
        viewModelScope.launch { useCases.updates.observe().collect { updateState { copy(update = it) } } }
        viewModelScope.launch { useCases.maps.observeOffer().collect { updateState { copy(mapOffer = it) } } }
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
            HomeUiEvent.OpenUpdate -> currentState.update?.let { sendEffect(HomeEffect.OpenUrl(it.pageUrl)) }
            HomeUiEvent.DismissUpdate -> currentState.update?.let { release ->
                viewModelScope.launch { useCases.updates.dismiss(release.version) }
            }
            HomeUiEvent.DownloadMap, HomeUiEvent.CancelMap, HomeUiEvent.DismissMap -> onMapEvent(event)
        }
    }

    /** The region map offer (T11.12). */
    private fun onMapEvent(event: HomeUiEvent) {
        val offer = currentState.mapOffer ?: return
        when (event) {
            HomeUiEvent.DownloadMap -> useCases.maps.downloads.start(offer.state.region, offer.version)
            HomeUiEvent.CancelMap -> useCases.maps.downloads.cancel(offer.state.region)
            HomeUiEvent.DismissMap -> viewModelScope.launch { useCases.maps.dismiss(offer) }
            else -> Unit
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
