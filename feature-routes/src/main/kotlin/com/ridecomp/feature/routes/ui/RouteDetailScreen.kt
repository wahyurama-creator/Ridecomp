package com.ridecomp.feature.routes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.routes.R
import com.ridecomp.feature.routes.viewmodel.RouteDetailEffect
import com.ridecomp.feature.routes.viewmodel.RouteDetailUiEvent
import com.ridecomp.feature.routes.viewmodel.RouteDetailUiState
import com.ridecomp.feature.routes.viewmodel.RouteDetailViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.LineChart
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.StatRow
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
    modifier: Modifier = Modifier,
    viewModel: RouteDetailViewModel = koinViewModel(key = routeId.toString()) { parametersOf(routeId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is RouteDetailEffect.Open -> onOpenRoute(effect.routeId)
            RouteDetailEffect.Deleted -> onDeleted()
        }
    }
    RouteDetailScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun RouteDetailScreen(state: RouteDetailUiState, onEvent: (RouteDetailUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val route = state.route
    ScreenScaffold(modifier = modifier, title = route?.name ?: stringResource(R.string.routes_title)) {
        when {
            state.isMissing -> EmptyState(message = stringResource(R.string.route_missing))
            route != null -> RouteContent(route, state, onEvent)
        }
    }
}

@Composable
private fun RouteContent(route: Route, state: RouteDetailUiState, onEvent: (RouteDetailUiEvent) -> Unit) {
    val format = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    var name by rememberSaveable(route.id) { mutableStateOf(route.name) }
    var isDeleteDialogShown by remember { mutableStateOf(false) }
    val reversedName = stringResource(R.string.route_reversed_name, route.name)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = RideCompDimens.spacingL),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
    ) {
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

@ThemePreviews
@Composable
private fun RouteDetailPreview() {
    RideCompPreview {
        RouteDetailScreen(
            state = RouteDetailUiState(route = Route(1, "Kaliurang loop", "kaliurang.gpx", 42_300.0, 780.0, 0)),
            onEvent = {},
        )
    }
}
