package com.ridecomp.feature.routes.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.map.covering
import com.ridecomp.core.engine.route.GpxError
import com.ridecomp.core.engine.route.PolylineSimplifier
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.CreateSegmentFromClimbUseCase
import com.ridecomp.core.usecase.DeleteRouteUseCase
import com.ridecomp.core.usecase.GetRouteGuidanceUseCase
import com.ridecomp.core.usecase.GetRoutePointsUseCase
import com.ridecomp.core.usecase.ImportGpxUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveRouteUseCase
import com.ridecomp.core.usecase.ObserveRoutesUseCase
import com.ridecomp.core.usecase.RenameRouteUseCase
import com.ridecomp.core.usecase.ReverseRouteUseCase
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.downsample
import com.ridecomp.ui.component.ChartValue
import com.ridecomp.ui.map.MapPoint
import com.ridecomp.ui.map.MapSource
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class RoutesUiState(
    val routes: ImmutableList<Route> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isImporting: Boolean = false,
    val importError: GpxError? = null,
    val isLoaded: Boolean = false,
)

sealed interface RoutesUiEvent {
    /** A GPX document from the picker or "Open with"; [fallbackName] when the file has none. */
    data class Import(val uri: String, val fallbackName: String) : RoutesUiEvent

    data object ErrorShown : RoutesUiEvent
}

sealed interface RoutesEffect {
    data class Imported(val routeId: Long) : RoutesEffect
}

/** Routes list and GPX import (T3.1). */
class RoutesViewModel(
    observeRoutes: ObserveRoutesUseCase,
    observeSettings: ObserveRideSettingsUseCase,
    private val importGpx: ImportGpxUseCase,
) : UdfViewModel<RoutesUiState, RoutesUiEvent, RoutesEffect>(RoutesUiState()) {
    init {
        viewModelScope.launch {
            combine(observeRoutes(), observeSettings()) { routes, settings -> routes to settings.unitSystem }
                .collect { (routes, units) ->
                    updateState { copy(routes = routes.toImmutableList(), unitSystem = units, isLoaded = true) }
                }
        }
    }

    override fun onEvent(event: RoutesUiEvent) {
        when (event) {
            is RoutesUiEvent.Import -> import(event)
            RoutesUiEvent.ErrorShown -> updateState { copy(importError = null) }
        }
    }

    private fun import(event: RoutesUiEvent.Import) {
        if (currentState.isImporting) return
        updateState { copy(isImporting = true, importError = null) }
        viewModelScope.launch {
            when (val result = importGpx(event.uri, event.fallbackName)) {
                is Outcome.Success -> {
                    updateState { copy(isImporting = false) }
                    sendEffect(RoutesEffect.Imported(result.value))
                }
                is Outcome.Failure -> updateState { copy(isImporting = false, importError = result.error) }
            }
        }
    }
}

/** Use cases of the route detail, grouped to keep the constructor short. */
data class RouteDetailUseCases(
    val observeRoute: ObserveRouteUseCase,
    val getPoints: GetRoutePointsUseCase,
    val getGuidance: GetRouteGuidanceUseCase,
    val observeMaps: ObserveOfflineMapsUseCase,
    val observeSettings: ObserveRideSettingsUseCase,
    val rename: RenameRouteUseCase,
    val reverse: ReverseRouteUseCase,
    val delete: DeleteRouteUseCase,
    val makeSegment: CreateSegmentFromClimbUseCase,
    val observeMapTiles: ObserveMapTilesUseCase,
)

@Immutable
data class RouteDetailUiState(
    val route: Route? = null,
    val isMissing: Boolean = false,
    /** Elevation (m or ft) against distance (km or mi); empty when the route has no elevation. */
    val elevation: ImmutableList<ChartValue> = persistentListOf(),
    /** Climbs found at import (T3.11), in route order. */
    val climbs: ImmutableList<RouteClimb> = persistentListOf(),
    /** Simplified route line for the map (13-UI-SCREENS #12). */
    val mapLine: ImmutableList<MapPoint> = persistentListOf(),
    /** Imported regions; without internet the map shows only when one covers the route start (D-90). */
    val mapRegions: ImmutableList<String> = persistentListOf(),
    val isAreaOffline: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /** Climbs already turned into a segment on this screen ("Jadikan segmen", T4.2). */
    val segmentClimbs: ImmutableSet<Int> = persistentSetOf(),
)

sealed interface RouteDetailUiEvent {
    data class Rename(val name: String) : RouteDetailUiEvent

    /** "Balik arah" (T3.2); [reversedName] is the localized "<name> (reverse)". */
    data class Reverse(val reversedName: String) : RouteDetailUiEvent

    data object Delete : RouteDetailUiEvent

    /** "Jadikan segmen" on a climb (T4.2); [name] is the localized default name. */
    data class MakeSegment(val climb: RouteClimb, val name: String) : RouteDetailUiEvent
}

sealed interface RouteDetailEffect {
    data class Open(val routeId: Long) : RouteDetailEffect

    data object Deleted : RouteDetailEffect
}

/** Route detail (T3.2): statistics, elevation profile, rename, reverse, delete. */
class RouteDetailViewModel(
    private val routeId: Long,
    private val useCases: RouteDetailUseCases,
    private val dispatchers: DispatcherProvider,
) : UdfViewModel<RouteDetailUiState, RouteDetailUiEvent, RouteDetailEffect>(RouteDetailUiState()) {
    /**
     * Online map tiles available now, from the network or the tile cache (D-90, T11.11); the network is
     * watched only while the screen is shown.
     */
    val mapSource: StateFlow<MapSource> = useCases.observeMapTiles()
        .map { MapSource(isOnline = it.isOnline, isCached = it.hasCache) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapSource())

    init {
        viewModelScope.launch {
            combine(useCases.observeRoute(routeId), useCases.observeSettings()) { route, settings -> route to settings }
                .collect { (route, settings) ->
                    updateState { copy(route = route, isMissing = route == null, unitSystem = settings.unitSystem) }
                }
        }
        viewModelScope.launch {
            val points = useCases.getPoints(routeId)
            val units = currentState.unitSystem
            // Conversion and downsampling are CPU work: off the main thread (02-DESIGN §6).
            val chart = withContext(dispatchers.default) { points.elevationChart(units) }
            val line = withContext(dispatchers.default) {
                PolylineSimplifier.simplify(points).map { MapPoint(it.latDeg, it.lonDeg) }.toImmutableList()
            }
            updateState { copy(elevation = chart, mapLine = line) }
            val start = points.firstOrNull()
            useCases.observeMaps().collect { maps ->
                val covering = start?.let { maps.covering(it.latDeg, it.lonDeg) }.orEmpty()
                updateState {
                    copy(
                        mapRegions = maps.map { it.path }.toImmutableList(),
                        isAreaOffline = covering.isNotEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            val climbs = useCases.getGuidance(routeId).climbs.toImmutableList()
            updateState { copy(climbs = climbs) }
        }
    }

    override fun onEvent(event: RouteDetailUiEvent) {
        when (event) {
            is RouteDetailUiEvent.Rename -> viewModelScope.launch { useCases.rename(routeId, event.name) }
            is RouteDetailUiEvent.Reverse -> viewModelScope.launch {
                useCases.reverse(routeId, event.reversedName)?.let { sendEffect(RouteDetailEffect.Open(it)) }
            }
            RouteDetailUiEvent.Delete -> viewModelScope.launch {
                useCases.delete(routeId)
                sendEffect(RouteDetailEffect.Deleted)
            }
            is RouteDetailUiEvent.MakeSegment -> viewModelScope.launch {
                if (useCases.makeSegment(routeId, event.climb, event.name) is Outcome.Success) {
                    updateState { copy(segmentClimbs = (segmentClimbs + event.climb.index).toImmutableSet()) }
                }
            }
        }
    }

    private companion object {
        /** Keeps the network watch across quick configuration changes only (CO-07). */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Every n-th point, at most [ActivityConfig.CHART_MAX_POINTS]; null elevations leave gaps. */
internal fun List<RoutePoint>.elevationChart(units: UnitSystem): ImmutableList<ChartValue> {
    if (none { it.elevationM != null }) return persistentListOf()
    val isMetric = units == UnitSystem.METRIC
    val distanceDivisor = if (isMetric) Units.METERS_PER_KM else Units.METERS_PER_MILE
    val elevationDivisor = if (isMetric) 1.0 else Units.METERS_PER_FOOT
    return downsample(ActivityConfig.CHART_MAX_POINTS)
        .map { ChartValue(it.cumDistanceM / distanceDivisor, it.elevationM?.div(elevationDivisor)) }
        .toImmutableList()
}
