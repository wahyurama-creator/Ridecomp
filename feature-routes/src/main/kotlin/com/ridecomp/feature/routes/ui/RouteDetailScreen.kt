package com.ridecomp.feature.routes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.routes.R
import com.ridecomp.feature.routes.viewmodel.RouteDetailEffect
import com.ridecomp.feature.routes.viewmodel.RouteDetailUiEvent
import com.ridecomp.feature.routes.viewmodel.RouteDetailUiState
import com.ridecomp.feature.routes.viewmodel.RouteDetailViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.LineChart
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.StatRow
import com.ridecomp.ui.component.WarningCard
import com.ridecomp.ui.map.MapSource
import com.ridecomp.ui.map.RouteMap
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Route detail entry point (T3.2). */
@Composable
fun RouteDetailRoute(
    routeId: Long,
    onOpenRoute: (Long) -> Unit,
    onDeleted: () -> Unit,
    onRideRoute: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenOfflineMaps: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: RouteDetailViewModel = koinViewModel(key = routeId.toString()) { parametersOf(routeId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapSource by viewModel.mapSource.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is RouteDetailEffect.Open -> onOpenRoute(effect.routeId)
            RouteDetailEffect.Deleted -> onDeleted()
        }
    }
    RouteDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onRide = { onRideRoute(routeId) },
        onOpenOfflineMaps = onOpenOfflineMaps,
        onBack = onBack,
        modifier = modifier,
        mapSource = mapSource,
    )
}

@Composable
fun RouteDetailScreen(
    state: RouteDetailUiState,
    onEvent: (RouteDetailUiEvent) -> Unit,
    onRide: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenOfflineMaps: () -> Unit = {},
    onBack: () -> Unit = {},
    mapSource: MapSource = MapSource(),
) {
    val route = state.route
    ScreenScaffold(
        modifier = modifier,
        title = route?.name ?: stringResource(R.string.routes_title),
        subtitle = route?.sourceLabel,
        onBack = onBack,
        backLabel = stringResource(R.string.route_cancel),
    ) {
        when {
            state.isMissing -> EmptyState(message = stringResource(R.string.route_missing))
            route != null -> RouteContent(route, state, onEvent, onRide, onOpenOfflineMaps, mapSource)
        }
    }
}

/**
 * Route map (13-UI-SCREENS #12, D-90): online while there is internet, else from the tile cache (T11.11) and
 * an offline file that covers the area, else a note that the map shows with internet.
 */
@Composable
private fun RouteMapSection(state: RouteDetailUiState, mapSource: MapSource, onOpenOfflineMaps: () -> Unit) {
    if (mapSource.hasTiles || state.isAreaOffline) {
        RouteMap(
            isOnline = mapSource.isOnline,
            isCached = mapSource.isCached,
            regionPaths = state.mapRegions,
            route = state.mapLine,
            description = stringResource(R.string.route_map_description),
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.routeMapHeight)
                .clip(RoundedCornerShape(RideCompDimens.cornerRadiusCard)),
        )
        if (!mapSource.isOnline && state.isAreaOffline) {
            RideCompCard(tone = CardTone.OK) {
                Text(
                    text = stringResource(R.string.route_map_offline),
                    style = RideCompTheme.typography.bodyStrong,
                    color = RideCompTheme.colors.ok,
                )
            }
        }
    } else {
        WarningCard(
            title = stringResource(R.string.route_map_missing_title),
            message = stringResource(R.string.route_map_missing),
            actionLabel = stringResource(R.string.route_map_import),
            onAction = onOpenOfflineMaps,
        )
    }
}

@Composable
private fun RouteContent(
    route: Route,
    state: RouteDetailUiState,
    onEvent: (RouteDetailUiEvent) -> Unit,
    onRide: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    mapSource: MapSource,
) {
    val format = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    var name by rememberSaveable(route.id) { mutableStateOf(route.name) }
    var isDeleteDialogShown by remember { mutableStateOf(false) }
    val reversedName = stringResource(R.string.route_reversed_name, route.name)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = RideCompDimens.spacingL),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = RideCompDimens.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) { RouteMapSection(state, mapSource, onOpenOfflineMaps) }
        BigButton(
            text = stringResource(R.string.route_ride),
            onClick = onRide,
            modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
        )
        StatRow(stringResource(R.string.route_distance), "${format.distance(route.distanceM)} ${format.distanceUnit}")
        StatRow(stringResource(R.string.route_ascent), "${format.elevation(route.ascentM)} ${format.elevationUnit}")
        route.sourceLabel?.let { StatRow(stringResource(R.string.route_source), it) }
        if (state.elevation.isEmpty()) {
            Text(
                text = stringResource(R.string.route_no_elevation),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
            )
        } else {
            LineChart(
                title = stringResource(R.string.route_elevation_profile),
                values = state.elevation,
                color = RideCompTheme.colors.track,
                modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
            )
            ClimbList(state, format) { climb, name -> onEvent(RouteDetailUiEvent.MakeSegment(climb, name)) }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.route_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
        )
        BigButton(
            text = stringResource(R.string.route_rename),
            onClick = { onEvent(RouteDetailUiEvent.Rename(name)) },
            style = BigButtonStyle.SECONDARY,
            isEnabled = name.isNotBlank() && name != route.name,
            modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
        )
        BigButton(
            text = stringResource(R.string.route_reverse),
            onClick = { onEvent(RouteDetailUiEvent.Reverse(reversedName)) },
            style = BigButtonStyle.SECONDARY,
            modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
        )
        BigButton(
            text = stringResource(R.string.route_delete),
            onClick = { isDeleteDialogShown = true },
            style = BigButtonStyle.DANGER,
            modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
        )
    }
    if (isDeleteDialogShown) {
        ConfirmDialog(
            title = stringResource(R.string.route_delete_title, route.name),
            message = stringResource(R.string.route_delete_message),
            confirmLabel = stringResource(R.string.route_delete),
            dismissLabel = stringResource(R.string.route_cancel),
            onConfirm = {
                isDeleteDialogShown = false
                onEvent(RouteDetailUiEvent.Delete)
            },
            onDismiss = { isDeleteDialogShown = false },
        )
    }
}

/** "Climb 2 of 4 · 2.1 km · 6.3% · ↑ 132 m" (12-FEATURE-SPECS §6.2). */
@Composable
private fun ClimbList(state: RouteDetailUiState, format: UnitFormatter, onMakeSegment: (RouteClimb, String) -> Unit) {
    val climbs = state.climbs
    Text(
        text = if (climbs.isEmpty()) {
            stringResource(R.string.route_no_climbs)
        } else {
            stringResource(R.string.route_climbs_title, climbs.size)
        },
        style = RideCompTheme.typography.dataLabel,
        color = RideCompTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
    )
    climbs.forEachIndexed { index, climb ->
        StatRow(
            stringResource(R.string.route_climb_label, index + 1, climbs.size),
            stringResource(
                R.string.route_climb_stats,
                format.distance(climb.lengthM),
                format.distanceUnit,
                format.grade(climb.avgGradePercent),
                format.elevation(climb.gainM),
                format.elevationUnit,
            ),
        )
        if (climb.index in state.segmentClimbs) {
            Text(
                text = stringResource(R.string.route_climb_segment_made),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.ok,
                modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
            )
        } else {
            val name = stringResource(R.string.route_climb_segment_name, state.route?.name.orEmpty(), index + 1)
            LinkText(stringResource(R.string.route_climb_make_segment), onClick = { onMakeSegment(climb, name) })
        }
    }
}

@ThemePreviews
@Composable
private fun RouteDetailPreview() {
    RideCompPreview {
        RouteDetailScreen(
            state = RouteDetailUiState(route = Route(1, "Kaliurang loop", "kaliurang.gpx", 42_300.0, 780.0, 0)),
            onEvent = {},
            onRide = {},
        )
    }
}
