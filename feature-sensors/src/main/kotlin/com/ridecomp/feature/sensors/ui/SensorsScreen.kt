package com.ridecomp.feature.sensors.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ridecomp.feature.sensors.R
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews

/** Sensors entry point (UI-01). Scan & pairing arrive in T2.1. */
@Composable
fun SensorsRoute(onScan: () -> Unit, modifier: Modifier = Modifier) {
    SensorsScreen(onScan = onScan, modifier = modifier)
}

/** Stateless Sensors screen; currently its empty state. */
@Composable
fun SensorsScreen(onScan: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.sensors_title)) {
        EmptyState(
            message = stringResource(R.string.sensors_empty),
            actionLabel = stringResource(R.string.sensors_scan_action),
            onAction = onScan,
        )
    }
}

@ThemePreviews
@Composable
private fun SensorsScreenPreview() {
    RideCompPreview { SensorsScreen(onScan = {}) }
}
