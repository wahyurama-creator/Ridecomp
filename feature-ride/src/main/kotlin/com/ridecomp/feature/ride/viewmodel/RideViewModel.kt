package com.ridecomp.feature.ride.viewmodel

import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.map.BackToStart
import com.ridecomp.core.engine.map.TrailPoint
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.sensors.ErgConfig
import com.ridecomp.core.engine.sensors.TrainerState
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.withProfile
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.GetRoutePointsUseCase
import com.ridecomp.core.usecase.LoadRideTrailUseCase
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.ObserveDataPagesUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveSafetyUseCase
import com.ridecomp.core.usecase.ObserveTrainerUseCase
import com.ridecomp.core.usecase.PerformIncidentActionUseCase
import com.ridecomp.core.usecase.PerformRideActionUseCase
import com.ridecomp.core.usecase.SetTrainerTargetUseCase
import com.ridecomp.feature.ride.model.DataPage
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.RideMapView
import com.ridecomp.feature.ride.model.RideMetricKeys
import com.ridecomp.feature.ride.model.RouteView
import com.ridecomp.feature.ride.model.toDataPages
import com.ridecomp.feature.ride.ui.component.ErgPresets
import com.ridecomp.feature.ride.ui.component.TrainerAction
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RideEffect {
    /** Ride finished: show the summary (02-DESIGN §3.6). */
    data object OpenSummary : RideEffect

    /** No ride any more (saved/discarded elsewhere). */
    data object Close : RideEffect
}

/**
 * Ride screen (T1.8). Live data comes straight from the service's StateFlow; each field derives its own
 * text so only changed fields recompose (UI-04). User input only goes out as [RideAction]s (ARCH-06).
 */
/** Use cases of the ride screen, grouped to keep the constructor short. */
data class RideUseCases(
    val observeLive: ObserveLiveRideUseCase,
    val observeSettings: ObserveRideSettingsUseCase,
    val observeDataPages: ObserveDataPagesUseCase,
    val observeProfiles: ObserveActivityProfilesUseCase,
    val observeSafety: ObserveSafetyUseCase,
    val performAction: PerformRideActionUseCase,
    val performIncident: PerformIncidentActionUseCase,
    val getRoutePoints: GetRoutePointsUseCase,
)

/** The map page: which tiles to draw and the ridden line of a ride without a route (D-90). */
data class RideMapUseCases(val observeTiles: ObserveMapTilesUseCase, val loadTrail: LoadRideTrailUseCase)

/** The smart trainer on the ride screen (T9.1). */
data class RideTrainerUseCases(
    val observe: ObserveTrainerUseCase,
    val setTarget: SetTrainerTargetUseCase,
    val observeProfile: ObserveProfileUseCase,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RideViewModel(
    private val useCases: RideUseCases,
    private val dispatchers: DispatcherProvider,
    private val maps: RideMapUseCases,
    private val trainerUseCases: RideTrainerUseCases,
) : UdfViewModel<Unit, RideAction, RideEffect>(Unit) {
    val liveRide: StateFlow<LiveRide> = useCases.observeLive()

    /** Smart trainer link and ERG target (T9.1). */
    val trainer: StateFlow<TrainerState> = trainerUseCases.observe()

    /** "Santai" / "Zona N" buttons for an interval session (zone 4–5) with FTP known (T9.1). */
    val ergPresets: StateFlow<ErgPresets?> = combine(
        liveRide.map { it.target?.intensity }.distinctUntilChanged(),
        trainerUseCases.observeProfile().map { it.ftpW }.distinctUntilChanged(),
    ) { intensity, ftp ->
        if (intensity == null || ftp == null || intensity.zone <= ErgConfig.STEADY_UP_TO_ZONE) {
            null
        } else {
            ErgPresets(ErgConfig.watts(Intensity.ENDURANCE, ftp), intensity.zone, ErgConfig.watts(intensity, ftp))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onTrainer(action: TrainerAction) {
        when (action) {
            is TrainerAction.Adjust -> trainerUseCases.setTarget.adjust(action.deltaW)
            is TrainerAction.Set -> trainerUseCases.setTarget.set(action.watts)
        }
    }

    /**
     * Ridden line of a GPS ride: rebuilt from the stored samples (the screen may open mid-ride or after a
     * recovery), then extended with each live position. Bounded by [RideTrail]. Drawn on rides without a
     * route and used by "back to start" (T7.2).
     */
    private val trail: Flow<List<TrailPoint>> = combine(
        useCases.observeLive.activeActivityId(),
        liveRide.map { it.isGpsEnabled }.distinctUntilChanged(),
    ) { id, isGpsRide -> id.takeIf { isGpsRide } }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                flow {
                    val trail = maps.loadTrail(id)
                    emit(trail.snapshot)
                    liveRide.collect { live ->
                        val lat = live.latDeg
                        val lon = live.lonDeg
                        if (lat != null && lon != null && trail.add(lat, lon)) emit(trail.snapshot)
                    }
                }
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private val isBackToStartOn = MutableStateFlow(false)

    /** Way back to the start while the rider asked for it (T7.2, D-92); null when off or without a position. */
    val backToStart: StateFlow<BackToStart?> = isBackToStartOn
        .flatMapLatest { isOn ->
            if (!isOn) {
                flowOf(null)
            } else {
                combine(trail, liveRide.map { it.latDeg to it.lonDeg }.distinctUntilChanged()) { points, position ->
                    withContext(dispatchers.default) { BackToStart.of(points, position.first, position.second) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Whether "back to start" is on (it stays on until the rider turns it off). */
    val isBackToStart: StateFlow<Boolean> = isBackToStartOn

    fun toggleBackToStart() {
        isBackToStartOn.value = !isBackToStartOn.value
    }

    /** Online tiles, offline regions or none, plus the ridden line (D-90); the network is watched only while shown. */
    val map: StateFlow<RideMapView> = combine(maps.observeTiles(), trail) { tiles, points -> tiles to points }
        .map { (tiles, points) -> withContext(dispatchers.default) { RideMapView.of(tiles, points) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RideMapView())

    /** Manual SOS is offered only when someone can be alerted (12-FEATURE-SPECS §5.3). */
    val isSosAvailable: StateFlow<Boolean> = useCases.observeSafety()
        .map { it.settings.isEnabled && it.contacts.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Who gets the alert SMS, named on the countdown screen (13-UI-SCREENS #18). */
    val contactNames: StateFlow<ImmutableList<String>> = useCases.observeSafety()
        .map { safety -> safety.contacts.map { it.displayName }.toImmutableList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

    /** General settings with the ride profile's overrides (screen mode, auto pause…). */
    val settings: StateFlow<RideSettings> = combine(useCases.observeSettings(), useCases.observeProfiles(), liveRide) {
            general,
            all,
            live,
        ->
        all.firstOrNull { it.id == live.profileId }?.let(general::withProfile) ?: general
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RideSettings())

    /** The ride profile's pages (T2.13), else the general pages (T2.8), else the defaults. */
    /** The followed route, simplified for drawing (T3.8, T3.9); null without a route. */
    val route: StateFlow<RouteView?> = liveRide
        .map { it.routeId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf<RouteView?>(null)
            } else {
                flow {
                    val points = useCases.getRoutePoints(id)
                    emit(withContext(dispatchers.default) { RouteView.of(points, settings.value.unitSystem) })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val pages: StateFlow<List<DataPage>> = liveRide
        .map { it.profileId }
        .distinctUntilChanged()
        .flatMapLatest { profileId -> useCases.observeDataPages(RideMetricKeys.ALL, profileId) }
        .map { layout -> layout?.toDataPages() ?: DefaultDataPages.ALL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DefaultDataPages.ALL)

    init {
        viewModelScope.launch {
            var hasSeenRide = false
            liveRide.collect { live ->
                when (live.status) {
                    RideStatus.FINISHED -> sendEffect(RideEffect.OpenSummary)
                    RideStatus.IDLE -> if (hasSeenRide) sendEffect(RideEffect.Close)
                    RideStatus.RECORDING, RideStatus.PAUSED, RideStatus.AUTO_PAUSED -> hasSeenRide = true
                }
            }
        }
    }

    override fun onEvent(event: RideAction) {
        useCases.performAction(event)
    }

    /** Incident countdown buttons, "I am safe" and manual SOS. */
    fun onIncident(action: IncidentAction) {
        useCases.performIncident(action)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
