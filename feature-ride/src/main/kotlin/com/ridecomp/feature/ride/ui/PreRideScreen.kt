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
import com.ridecomp.core.model.BuiltInProfiles
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.displayName
import com.ridecomp.feature.ride.viewmodel.PreRideEffect
import com.ridecomp.feature.ride.viewmodel.PreRideUiEvent
import com.ridecomp.feature.ride.viewmodel.PreRideUiState
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList
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
    PreRideScreen(gps = gps, state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun PreRideScreen(
    gps: GpsStatus,
    state: PreRideUiState,
    onEvent: (PreRideUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onStart = { onEvent(PreRideUiEvent.Start) }
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.pre_ride_title)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            ProfilePicker(state, onEvent)
            val colors = RideCompTheme.colors
            val (gpsText, gpsColor) = when (gps) {
                GpsStatus.NoPermission -> stringResource(R.string.pre_ride_gps_no_permission) to colors.danger
                GpsStatus.Disabled -> stringResource(R.string.pre_ride_gps_disabled) to colors.danger
                GpsStatus.Searching -> stringResource(R.string.pre_ride_gps_searching) to colors.warnText
                GpsStatus.NotUsed -> stringResource(R.string.pre_ride_gps_not_used) to colors.ok
                is GpsStatus.Ready -> (
                    gps.accuracyM?.let { stringResource(R.string.pre_ride_gps_ready, it.roundToInt().toString()) }
                        ?: stringResource(R.string.pre_ride_gps_ready_unknown)
                    ) to colors.ok
            }
            Text(text = gpsText, style = RideCompTheme.typography.title, color = gpsColor)
            state.battery?.let {
                Text(
                    text = stringResource(R.string.pre_ride_battery, it.percent),
                    style = RideCompTheme.typography.body,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
            if (gps is GpsStatus.Ready || gps == GpsStatus.NotUsed) {
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

@Composable
private fun ProfilePicker(state: PreRideUiState, onEvent: (PreRideUiEvent) -> Unit) {
    val selected = state.selected ?: return
    ChoiceChips(
        label = stringResource(R.string.pre_ride_profile),
        choices = state.profiles.map { Choice(it.id, it.displayName()) }.toImmutableList(),
        selected = selected.id,
        onSelect = { onEvent(PreRideUiEvent.SelectProfile(it)) },
    )
}

private val previewProfiles = BuiltInProfiles.ALL.mapIndexed { index, profile -> profile.copy(id = index + 1L) }
private val previewState = PreRideUiState(
    battery = BatteryStatus(76, false),
    profiles = previewProfiles.toImmutableList(),
    selected = previewProfiles.first(),
)

@ThemePreviews
@Composable
private fun PreRideSearchingPreview() {
    RideCompPreview { PreRideScreen(gps = GpsStatus.Searching, state = previewState, onEvent = {}) }
}

@ThemePreviews
@Composable
private fun PreRideReadyPreview() {
    RideCompPreview { PreRideScreen(gps = GpsStatus.Ready(4.0), state = previewState, onEvent = {}) }
}
