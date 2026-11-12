package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ridecomp.feature.ride.R
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Home entry point (UI-01). Wires navigation callbacks; state comes in Phase 1 (T1.6, T1.12). */
@Composable
fun HomeRoute(onStartRide: () -> Unit, modifier: Modifier = Modifier) {
    HomeScreen(onStartRide = onStartRide, modifier = modifier)
}

/** Stateless Home screen: big START RIDE button (≥ 72 dp, 02-DESIGN §3.2). */
@Composable
fun HomeScreen(onStartRide: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.ride_home_title)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            BigButton(text = stringResource(R.string.ride_start_button), onClick = onStartRide)
            Text(
                text = stringResource(R.string.ride_home_no_last_ride),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textSecondary,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun HomeScreenPreview() {
    RideCompPreview { HomeScreen(onStartRide = {}) }
}
