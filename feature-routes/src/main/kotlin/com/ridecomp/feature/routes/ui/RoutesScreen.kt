package com.ridecomp.feature.routes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.route.GpxError
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.routes.R
import com.ridecomp.feature.routes.viewmodel.RoutesEffect
import com.ridecomp.feature.routes.viewmodel.RoutesUiEvent
import com.ridecomp.feature.routes.viewmodel.RoutesUiState
import com.ridecomp.feature.routes.viewmodel.RoutesViewModel
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.ListRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

/**
 * GPX types offered by the picker; some file managers report GPX as XML, and Android's media store has no
 * GPX type, so `.gpx` files are often `application/octet-stream` (D-51). The parser rejects anything that
 * is not a valid GPX, so offering octet-stream here is safe; the "Open with" filter stays narrow (D-44).
 */
private val GPX_MIME_TYPES = arrayOf(
    "application/gpx+xml",
    "application/gpx",
    "text/xml",
    "application/xml",
    "application/octet-stream",
)

/**
 * Routes entry point (T3.1). [incomingUri] is a GPX opened from another app ("Open with"); it is imported
 * once when the screen appears.
 */
@Composable
fun RoutesRoute(
    onOpenRoute: (Long) -> Unit,
    modifier: Modifier = Modifier,
    incomingUri: String? = null,
    onIncomingHandled: () -> Unit = {},
    tipContent: (@Composable () -> Unit)? = null,
    viewModel: RoutesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fallbackName = stringResource(R.string.routes_unnamed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onEvent(RoutesUiEvent.Import(it.toString(), fallbackName)) }
    }
    LaunchedEffect(incomingUri) {
        incomingUri?.let {
            viewModel.onEvent(RoutesUiEvent.Import(it, fallbackName))
            onIncomingHandled()
        }
    }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is RoutesEffect.Imported -> onOpenRoute(effect.routeId)
        }
    }
    RoutesScreen(
        state = state,
        onImport = { picker.launch(GPX_MIME_TYPES) },
        onOpenRoute = onOpenRoute,
        onErrorShown = { viewModel.onEvent(RoutesUiEvent.ErrorShown) },
        modifier = modifier,
        tipContent = tipContent,
    )
}

@Composable
fun RoutesScreen(
    state: RoutesUiState,
    onImport: () -> Unit,
    onOpenRoute: (Long) -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    tipContent: (@Composable () -> Unit)? = null,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.routes_title),
        banner = state.importError?.let { error ->
            {
                AlertBanner(
                    message = stringResource(error.messageRes()),
                    severity = AlertSeverity.WARN,
                    actions = listOf(BannerAction(stringResource(R.string.routes_ok), onErrorShown)),
                )
            }
        },
    ) {
        tipContent?.let {
            Box(modifier = Modifier.padding(horizontal = RideCompDimens.screenPaddingHorizontal)) { it() }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (state.isLoaded && state.routes.isEmpty()) {
                EmptyState(message = stringResource(R.string.routes_empty))
            } else {
                val format = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
                LazyColumn {
                    items(state.routes, key = { it.id }) { route -> RouteRow(route, format) { onOpenRoute(route.id) } }
                }
            }
        }
        BigButton(
            text = stringResource(if (state.isImporting) R.string.routes_importing else R.string.routes_import_action),
            onClick = onImport,
            isEnabled = !state.isImporting,
            modifier = Modifier.fillMaxWidth().padding(RideCompDimens.spacingM),
        )
    }
}

@Composable
private fun RouteRow(route: Route, format: UnitFormatter, onClick: () -> Unit) {
    ListRow(
        title = route.name,
        subtitle = stringResource(
            R.string.routes_stats,
            format.distance(route.distanceM),
            format.distanceUnit,
            format.elevation(route.ascentM),
            format.elevationUnit,
        ),
        onClick = onClick,
    )
}

@StringRes
internal fun GpxError.messageRes(): Int = when (this) {
    GpxError.TOO_LARGE -> R.string.routes_error_too_large
    GpxError.TOO_MANY_POINTS -> R.string.routes_error_too_many_points
    GpxError.DOCTYPE_NOT_ALLOWED, GpxError.MALFORMED -> R.string.routes_error_malformed
    GpxError.NO_POINTS -> R.string.routes_error_no_points
    GpxError.INVALID_VALUE -> R.string.routes_error_invalid_value
}

@ThemePreviews
@Composable
private fun RoutesScreenPreview() {
    RideCompPreview {
        RoutesScreen(
            state = RoutesUiState(
                routes = persistentListOf(Route(1, "Kaliurang loop", "kaliurang.gpx", 42_300.0, 780.0, 0)),
                isLoaded = true,
            ),
            onImport = {},
            onOpenRoute = {},
            onErrorShown = {},
        )
    }
}
