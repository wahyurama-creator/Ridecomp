package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.viewmodel.PreRideEffect
import com.ridecomp.feature.ride.viewmodel.PreRideUiEvent
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

/** Pre-ride check (02-DESIGN §3.3): GPS fix, battery; START or start without waiting. */
@Composable
fun PreRideRoute(onStarted: () -> Unit, modifier: Modifier = Modifier, viewModel: PreRideViewModel = koinViewModel()) {
    val gps by viewModel.gpsStatus.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            PreRideEffect.OpenRide -> onStarted()
        }
    }
    PreRideScreen(gps = gps, battery = state.battery, onStart = {
        viewModel.onEvent(PreRideUiEvent.Start)
    }, modifier = modifier)
}

@Composable
fun PreRideScreen(gps: GpsStatus, battery: BatteryStatus?, onStart: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.pre_ride_title)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            val colors = RideCompTheme.colors
            val (gpsText, gpsColor) = when (gps) {
                GpsStatus.NoPermission -> stringResource(R.string.pre_ride_gps_no_permission) to colors.danger
                GpsStatus.Disabled -> stringResource(R.string.pre_ride_gps_disabled) to colors.danger
                GpsStatus.Searching -> stringResource(R.string.pre_ride_gps_searching) to colors.warnText
                is GpsStatus.Ready -> (
                    gps.accuracyM?.let { stringResource(R.string.pre_ride_gps_ready, it.roundToInt().toString()) }
                        ?: stringResource(R.string.pre_ride_gps_ready_unknown)
                    ) to colors.ok
            }
            Text(text = gpsText, style = RideCompTheme.typography.title, color = gpsColor)
            battery?.let {
                Text(
                    text = stringResource(R.string.pre_ride_battery, it.percent),
                    style = RideCompTheme.typography.body,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
            if (gps is GpsStatus.Ready) {
                BigButton(text = stringResource(R.string.pre_ride_start), onClick = onStart)
            } else {
                // Recording starts now; position follows once the GPS has a fix (02-DESIGN §3.13).
                BigButton(
                    text = stringResource(R.string.pre_ride_start_without_fix),
                    onClick = onStart,
                    style = BigButtonStyle.SECONDARY,
                    isEnabled = gps != GpsStatus.NoPermission,
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun PreRideSearchingPreview() {
    RideCompPreview { PreRideScreen(gps = GpsStatus.Searching, battery = BatteryStatus(76, false), onStart = {}) }
}

@ThemePreviews
@Composable
private fun PreRideReadyPreview() {
    RideCompPreview { PreRideScreen(gps = GpsStatus.Ready(4.0), battery = BatteryStatus(76, false), onStart = {}) }
}
