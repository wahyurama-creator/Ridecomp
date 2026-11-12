package com.ridecomp.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ridecomp.feature.history.R
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews

/** History entry point (UI-01). The activity list arrives in T1.13. */
@Composable
fun HistoryRoute(onStartRide: () -> Unit, modifier: Modifier = Modifier) {
    HistoryScreen(onStartRide = onStartRide, modifier = modifier)
}

/** Stateless History screen; currently its empty state (02-DESIGN §3.13). */
@Composable
fun HistoryScreen(onStartRide: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.history_title)) {
        EmptyState(
            message = stringResource(R.string.history_empty),
            actionLabel = stringResource(R.string.history_empty_action),
            onAction = onStartRide,
        )
    }
}

@ThemePreviews
@Composable
private fun HistoryScreenPreview() {
    RideCompPreview { HistoryScreen(onStartRide = {}) }
}
