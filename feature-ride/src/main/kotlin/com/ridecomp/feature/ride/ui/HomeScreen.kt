package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.usecase.BackupReminder
import com.ridecomp.core.usecase.RecoveryChoice
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.viewmodel.HomeEffect
import com.ridecomp.feature.ride.viewmodel.HomeUiEvent
import com.ridecomp.feature.ride.viewmodel.HomeUiState
import com.ridecomp.feature.ride.viewmodel.HomeViewModel
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import org.koin.androidx.compose.koinViewModel

/** Home (02-DESIGN §3.2): START RIDE, ride in progress, recovery banner, last ride. */
@Composable
fun HomeRoute(
    onOpenPreRide: () -> Unit,
    onOpenRide: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBackup: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            HomeEffect.OpenPreRide -> onOpenPreRide()
            HomeEffect.OpenRide -> onOpenRide()
            HomeEffect.OpenBackup -> onOpenBackup()
        }
    }
    HomeScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun HomeScreen(state: HomeUiState, onEvent: (HomeUiEvent) -> Unit, modifier: Modifier = Modifier) {
    var isDiscardDialogShown by remember { mutableStateOf(false) }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.ride_home_title),
        banner = state.recoverable?.let {
            {
                AlertBanner(
                    message = stringResource(R.string.ride_recovery_message),
                    severity = AlertSeverity.WARN,
                    actions = listOf(
                        BannerAction(stringResource(R.string.ride_recovery_resume)) {
                            onEvent(HomeUiEvent.Recover(RecoveryChoice.RESUME))
                        },
                        BannerAction(stringResource(R.string.ride_recovery_save)) {
                            onEvent(HomeUiEvent.Recover(RecoveryChoice.SAVE))
                        },
                        BannerAction(stringResource(R.string.ride_recovery_discard)) { isDiscardDialogShown = true },
                    ),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            if (state.isRideInProgress) {
                Text(
                    text = stringResource(R.string.ride_home_in_progress),
                    style = RideCompTheme.typography.title,
                    color = RideCompTheme.colors.ok,
                )
                BigButton(text = stringResource(R.string.ride_home_open_ride), onClick = {
                    onEvent(HomeUiEvent.OpenRide)
                })
            } else {
                BigButton(text = stringResource(R.string.ride_start_button), onClick = {
                    onEvent(HomeUiEvent.StartRide)
                })
            }
            LastRide(state)
            state.backupReminder?.let { BackupReminderCard(it, onEvent) }
        }
    }
    if (isDiscardDialogShown) {
        ConfirmDialog(
            title = stringResource(R.string.ride_recovery_discard_title),
            message = stringResource(R.string.ride_recovery_discard_message),
            confirmLabel = stringResource(R.string.ride_recovery_discard),
            dismissLabel = stringResource(R.string.ride_cancel),
            onConfirm = {
                isDiscardDialogShown = false
                onEvent(HomeUiEvent.Recover(RecoveryChoice.DISCARD))
            },
            onDismiss = { isDiscardDialogShown = false },
        )
    }
}

@Composable
private fun LastRide(state: HomeUiState) {
    val ride = state.lastRide
    val text = if (ride == null) {
        stringResource(R.string.ride_home_no_last_ride)
    } else {
        val formatter = UnitFormatter(state.unitSystem)
        val summary = listOf(
            ride.name,
            "${formatter.distance(ride.totals.distanceM)} ${formatter.distanceUnit}",
            formatter.duration(ride.totals.movingS * Units.MS_PER_SECOND),
        ).joinToString(SEPARATOR)
        stringResource(R.string.ride_home_last_ride, summary)
    }
    Text(text = text, style = RideCompTheme.typography.body, color = RideCompTheme.colors.textSecondary)
}

private const val SEPARATOR = " · "

@ThemePreviews
@Composable
private fun HomeScreenPreview() {
    RideCompPreview { HomeScreen(state = HomeUiState(), onEvent = {}) }
}

/** Shown when opening the app, never by a background job (12-FEATURE-SPECS §12.4, BAT-05). */
@Composable
private fun BackupReminderCard(reminder: BackupReminder, onEvent: (HomeUiEvent) -> Unit) {
    val days = reminder.daysSinceBackup
    AlertBanner(
        message = if (days == null) {
            pluralStringResource(R.plurals.home_backup_never, reminder.newActivities, reminder.newActivities)
        } else {
            pluralStringResource(R.plurals.home_backup_days, days.toInt(), days.toInt())
        },
        severity = AlertSeverity.INFO,
        actions = listOf(
            BannerAction(stringResource(R.string.home_backup_now)) { onEvent(HomeUiEvent.OpenBackup) },
            BannerAction(stringResource(R.string.home_backup_later)) { onEvent(HomeUiEvent.SnoozeBackupReminder) },
        ),
    )
}
