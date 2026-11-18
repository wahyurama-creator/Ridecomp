package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.ComponentWear
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.usecase.BikeChoice
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObserveBikeChoiceUseCase
import com.ridecomp.core.usecase.ObserveComponentWearUseCase
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.ObserveHasProfileUseCase
import com.ridecomp.core.usecase.ObserveProfileChoiceUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveRouteUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.ProfileChoice
import com.ridecomp.core.usecase.ReadBatteryUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.SelectBikeUseCase
import com.ridecomp.core.usecase.StartRideUseCase
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Battery and the bike picker of the pre-ride check; GPS status is a separate lifecycle-bound flow. */
@Immutable
data class PreRideUiState(
    val battery: BatteryStatus?,
    /** Saved bikes (D-89); the picked one decides the activity profile. */
    val bikes: ImmutableList<Bike> = persistentListOf(),
    val bike: Bike? = null,
    /** Parts of the picked bike that reached their limit (T7.3). */
    val dueComponents: ImmutableList<ComponentWear> = persistentListOf(),
    val profiles: ImmutableList<ActivityProfile> = persistentListOf(),
    /** The activity profile of the picked bike. */
    val selected: ActivityProfile? = null,
    /** Route to follow (T3.6), chosen from the route detail; null for a free ride. */
    val route: Route? = null,
    /** Saved sensors for the check list (13-UI-SCREENS #5); they connect when the ride starts. */
    val sensors: ImmutableList<SavedSensor> = persistentListOf(),
    val settings: RideSettings = RideSettings(),
    /** Today's session of the followed plan (T7.4b); null = none or a rest day. */
    val session: PlanSession? = null,
    /** Measure this ride against [session]; on by default. */
    val isSessionOn: Boolean = true,
    /** Onboarding's profile step was skipped: offer to fill it in (T10.12). */
    val isProfileMissing: Boolean = false,
) {
    /** Screen mode used by the ride: the profile's own choice wins over the general setting (D-32). */
    val screenMode: ScreenMode get() = selected?.screenMode ?: settings.screenMode

    /** The profile sets the screen mode itself: change it in the profile, not here. */
    val isScreenModeFromProfile: Boolean get() = selected?.screenMode != null
}

sealed interface PreRideUiEvent {
    data class SelectBike(val id: Long) : PreRideUiEvent

    /** Ride without the route. */
    data object ClearRoute : PreRideUiEvent

    /** General screen mode setting (13-UI-SCREENS #5). */
    data class SelectScreenMode(val mode: ScreenMode) : PreRideUiEvent

    data class SessionOn(val isOn: Boolean) : PreRideUiEvent

    data object Start : PreRideUiEvent
}

sealed interface PreRideEffect {
    data object OpenRide : PreRideEffect
}

/** Use cases of the pre-ride check, grouped to keep the constructor short. */
data class PreRideUseCases(
    val observeGps: ObserveGpsStatusUseCase,
    val readBattery: ReadBatteryUseCase,
    val bikes: PreRideBikeUseCases,
    val observeRoute: ObserveRouteUseCase,
    val startRide: StartRideUseCase,
    val options: PreRideOptionUseCases,
)

/** The bike picker: saved bikes and their activity profiles (D-89). */
data class PreRideBikeUseCases(
    val observeBikes: ObserveBikeChoiceUseCase,
    val selectBike: SelectBikeUseCase,
    val observeProfiles: ObserveProfileChoiceUseCase,
    val observeWear: ObserveComponentWearUseCase,
)

/** Sensors and the screen mode setting shown on the pre-ride check. */
data class PreRideOptionUseCases(
    val observeSensors: ObserveSavedSensorsUseCase,
    val observeSettings: ObserveRideSettingsUseCase,
    val saveSettings: SaveRideSettingsUseCase,
    val observeActivePlan: ObserveActivePlanUseCase,
    val observeHasProfile: ObserveHasProfileUseCase,
)

/** The picked bike with its activity profile. */
private data class RideChoice(val bikes: BikeChoice, val profiles: ProfileChoice) {
    /** A bike whose custom profile was deleted rides with the default profile. */
    val profile: ActivityProfile?
        get() = profiles.profiles.firstOrNull { it.id == bikes.selected?.profileId } ?: profiles.selected
}

/**
 * Pre-ride check (02-DESIGN §3.3) with the bike picker (D-89); the bike's type is its activity profile
 * (12-FEATURE-SPECS §4). GPS warms up only while the screen is visible and only for profiles that use GPS:
 * [gpsStatus] is shared with `WhileSubscribed`, so leaving the screen or picking an indoor bike stops
 * location updates (BAT-01, CO-07).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreRideViewModel(routeId: Long?, private val useCases: PreRideUseCases) :
    UdfViewModel<PreRideUiState, PreRideUiEvent, PreRideEffect>(PreRideUiState(useCases.readBattery())) {
    private val choice = combine(useCases.bikes.observeBikes(), useCases.bikes.observeProfiles(), ::RideChoice)

    val gpsStatus: StateFlow<GpsStatus> = choice
        .map { it.profile?.isGpsEnabled != false }
        .distinctUntilChanged()
        .flatMapLatest { isGpsUsed -> if (isGpsUsed) useCases.observeGps() else flowOf(GpsStatus.NotUsed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GpsStatus.Searching())

    init {
        viewModelScope.launch {
            choice.collect { choice ->
                updateState {
                    copy(
                        bikes = choice.bikes.bikes.toImmutableList(),
                        bike = choice.bikes.selected,
                        profiles = choice.profiles.profiles.toImmutableList(),
                        selected = choice.profile,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(choice, useCases.bikes.observeWear.due()) { current, due ->
                due.filter { it.component.bikeId == current.bikes.selected?.id }
            }.collect { due -> updateState { copy(dueComponents = due.toImmutableList()) } }
        }
        viewModelScope.launch {
            useCases.options.observeActivePlan().collect { plan ->
                updateState { copy(session = plan?.today?.session) }
            }
        }
        viewModelScope.launch {
            useCases.options.observeSensors().collect { updateState { copy(sensors = it.toImmutableList()) } }
        }
        viewModelScope.launch {
            useCases.options.observeSettings().collect { updateState { copy(settings = it) } }
        }
        viewModelScope.launch {
            useCases.options.observeHasProfile().collect { has -> updateState { copy(isProfileMissing = !has) } }
        }
        routeId?.let { id ->
            viewModelScope.launch { useCases.observeRoute(id).collect { updateState { copy(route = it) } } }
        }
    }

    override fun onEvent(event: PreRideUiEvent) {
        when (event) {
            is PreRideUiEvent.SelectBike -> viewModelScope.launch { useCases.bikes.selectBike(event.id) }
            PreRideUiEvent.ClearRoute -> updateState { copy(route = null) }
            is PreRideUiEvent.SelectScreenMode -> viewModelScope.launch {
                useCases.options.saveSettings(currentState.settings.copy(screenMode = event.mode))
            }
            is PreRideUiEvent.SessionOn -> updateState { copy(isSessionOn = event.isOn) }
            PreRideUiEvent.Start -> {
                val state = currentState
                useCases.startRide(
                    state.selected?.id,
                    state.route?.id,
                    withSession =
                    state.session != null && state.isSessionOn,
                )
                sendEffect(PreRideEffect.OpenRide)
            }
        }
    }

    private companion object {
        /** Keeps GPS across quick configuration changes only (CO-07). */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
