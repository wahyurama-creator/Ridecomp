package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.ObserveProfileChoiceUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveRouteUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.ReadBatteryUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.SelectActivityProfileUseCase
import com.ridecomp.core.usecase.StartRideUseCase
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Battery and the profile picker of the pre-ride check; GPS status is a separate lifecycle-bound flow. */
@Immutable
data class PreRideUiState(
    val battery: BatteryStatus?,
    val profiles: ImmutableList<ActivityProfile> = persistentListOf(),
    val selected: ActivityProfile? = null,
    /** Route to follow (T3.6), chosen from the route detail; null for a free ride. */
    val route: Route? = null,
    /** Saved sensors for the check list (13-UI-SCREENS #5); they connect when the ride starts. */
    val sensors: ImmutableList<SavedSensor> = persistentListOf(),
    val settings: RideSettings = RideSettings(),
) {
    /** Screen mode used by the ride: the profile's own choice wins over the general setting (D-32). */
    val screenMode: ScreenMode get() = selected?.screenMode ?: settings.screenMode

    /** The profile sets the screen mode itself: change it in the profile, not here. */
    val isScreenModeFromProfile: Boolean get() = selected?.screenMode != null
}

sealed interface PreRideUiEvent {
    data class SelectProfile(val id: Long) : PreRideUiEvent

    /** Ride without the route. */
    data object ClearRoute : PreRideUiEvent

    /** General screen mode setting (13-UI-SCREENS #5). */
    data class SelectScreenMode(val mode: ScreenMode) : PreRideUiEvent

    data object Start : PreRideUiEvent
}

sealed interface PreRideEffect {
    data object OpenRide : PreRideEffect
}

/**
 * Pre-ride check (02-DESIGN §3.3) with the activity profile picker (12-FEATURE-SPECS §4). GPS warms up only
 * while the screen is visible and only for profiles that use GPS: [gpsStatus] is shared with
 * `WhileSubscribed`, so leaving the screen or choosing Indoor stops location updates (BAT-01, CO-07).
 */
/** Use cases of the pre-ride check, grouped to keep the constructor short. */
data class PreRideUseCases(
    val observeGps: ObserveGpsStatusUseCase,
    val readBattery: ReadBatteryUseCase,
    val observeProfiles: ObserveProfileChoiceUseCase,
    val selectProfile: SelectActivityProfileUseCase,
    val observeRoute: ObserveRouteUseCase,
    val startRide: StartRideUseCase,
    val options: PreRideOptionUseCases,
)

/** Sensors and the screen mode setting shown on the pre-ride check. */
data class PreRideOptionUseCases(
    val observeSensors: ObserveSavedSensorsUseCase,
    val observeSettings: ObserveRideSettingsUseCase,
    val saveSettings: SaveRideSettingsUseCase,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PreRideViewModel(routeId: Long?, private val useCases: PreRideUseCases) :
    UdfViewModel<PreRideUiState, PreRideUiEvent, PreRideEffect>(PreRideUiState(useCases.readBattery())) {
    private val choice = useCases.observeProfiles()

    val gpsStatus: StateFlow<GpsStatus> = choice
        .map { it.selected?.isGpsEnabled != false }
        .distinctUntilChanged()
        .flatMapLatest { isGpsUsed -> if (isGpsUsed) useCases.observeGps() else flowOf(GpsStatus.NotUsed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GpsStatus.Searching())

    init {
        viewModelScope.launch {
            choice.collect { updateState { copy(profiles = it.profiles.toImmutableList(), selected = it.selected) } }
        }
        viewModelScope.launch {
            useCases.options.observeSensors().collect { updateState { copy(sensors = it.toImmutableList()) } }
        }
        viewModelScope.launch {
            useCases.options.observeSettings().collect { updateState { copy(settings = it) } }
        }
        routeId?.let { id ->
            viewModelScope.launch { useCases.observeRoute(id).collect { updateState { copy(route = it) } } }
        }
    }

    override fun onEvent(event: PreRideUiEvent) {
        when (event) {
            is PreRideUiEvent.SelectProfile -> viewModelScope.launch { useCases.selectProfile(event.id) }
            PreRideUiEvent.ClearRoute -> updateState { copy(route = null) }
            is PreRideUiEvent.SelectScreenMode -> viewModelScope.launch {
                useCases.options.saveSettings(currentState.settings.copy(screenMode = event.mode))
            }
            PreRideUiEvent.Start -> {
                useCases.startRide(currentState.selected?.id, currentState.route?.id)
                sendEffect(PreRideEffect.OpenRide)
            }
        }
    }

    private companion object {
        /** Keeps GPS across quick configuration changes only (CO-07). */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
