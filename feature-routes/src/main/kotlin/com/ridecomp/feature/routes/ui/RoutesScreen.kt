package com.ridecomp.feature.routes.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ridecomp.feature.routes.R
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews

/** Routes entry point (UI-01). GPX import arrives in T3.1. */
@Composable
fun RoutesRoute(onImportGpx: () -> Unit, modifier: Modifier = Modifier) {
    RoutesScreen(onImportGpx = onImportGpx, modifier = modifier)
}

/** Stateless Routes screen; currently its empty state. */
@Composable
fun RoutesScreen(onImportGpx: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.routes_title)) {
        EmptyState(
            message = stringResource(R.string.routes_empty),
            actionLabel = stringResource(R.string.routes_import_action),
            onAction = onImportGpx,
        )
    }
}

@ThemePreviews
@Composable
private fun RoutesScreenPreview() {
    RideCompPreview { RoutesScreen(onImportGpx = {}) }
}
