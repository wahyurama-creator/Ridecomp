package com.ridecomp.feature.ride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.viewmodel.SummaryEffect
import com.ridecomp.feature.ride.viewmodel.SummaryUiEvent
import com.ridecomp.feature.ride.viewmodel.SummaryUiState
import com.ridecomp.feature.ride.viewmodel.SummaryViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.RideCompTextField
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.StatGrid
import com.ridecomp.ui.component.StatItem
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

/** Summary after finishing (T1.12, 13-UI-SCREENS #10): name, totals, SIMPAN or Buang. */
@Composable
fun SummaryRoute(
    onSaved: (activityId: Long) -> Unit,
    onDiscarded: () -> Unit,
    modifier: Modifier = Modifier,
    newRecordsContent: (@Composable (activityId: Long) -> Unit)? = null,
    viewModel: SummaryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is SummaryEffect.Saved -> onSaved(effect.activityId)
            SummaryEffect.Discarded -> onDiscarded()
        }
    }
    SummaryScreen(
        state = state,
        onEvent = viewModel::onEvent,
        newRecordsContent = newRecordsContent,
        modifier = modifier,
    )
}

/** [newRecordsContent] is the "new records" card of the training feature, provided by the app (ARCH-02). */
@Composable
fun SummaryScreen(
    state: SummaryUiState,
    onEvent: (SummaryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    newRecordsContent: (@Composable (activityId: Long) -> Unit)? = null,
) {
    var isDiscardDialogShown by remember { mutableStateOf(false) }
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.summary_title)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) {
            RideCompTextField(
                label = stringResource(R.string.summary_name),
                value = state.name,
                onValueChange = { onEvent(SummaryUiEvent.NameChanged(it)) },
                isLarge = true,
            )
            StatGrid(summaryStats(state.live, formatter))
            state.activityId?.let { id -> newRecordsContent?.invoke(id) }
            BigButton(
                text = stringResource(R.string.summary_save),
                onClick = { onEvent(SummaryUiEvent.Save) },
                isEnabled = !state.isWorking && state.activityId != null,
                size = BigButtonSize.SECONDARY,
            )
            Text(
                text = stringResource(R.string.summary_export_hint),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            BigButton(
                text = stringResource(R.string.ride_discard),
                onClick = { isDiscardDialogShown = true },
                style = BigButtonStyle.TEXT_DANGER,
                isEnabled = !state.isWorking,
            )
        }
    }
    if (isDiscardDialogShown) {
        ConfirmDialog(
            title = stringResource(R.string.ride_discard_title),
            message = stringResource(R.string.ride_discard_message),
            confirmLabel = stringResource(R.string.ride_discard),
            dismissLabel = stringResource(R.string.ride_cancel),
            onConfirm = {
                isDiscardDialogShown = false
                onEvent(SummaryUiEvent.Discard)
            },
            onDismiss = { isDiscardDialogShown = false },
        )
    }
}

/**
 * The 3 × 3 statistics of 13-UI-SCREENS #10. Without a power meter the estimate is labelled EST and is
 * never written to the FIT power field (CLAUDE.md).
 */
@Composable
private fun summaryStats(live: LiveRide, f: UnitFormatter): ImmutableList<StatItem> {
    val sensors = live.sensors
    val hasPowerMeter = sensors.snapshot.hasPowerMeter
    return listOfNotNull(
        StatItem(stringResource(R.string.summary_distance), f.distance(live.distanceM), f.distanceUnit),
        StatItem(
            stringResource(R.string.summary_moving),
            f.duration(live.movingMs),
            stringResource(R.string.summary_hms),
        ),
        StatItem(stringResource(R.string.summary_ascent), f.elevation(live.elevation.ascentM), f.elevationUnit),
        StatItem(
            stringResource(R.string.summary_speed),
            f.speed(live.avgSpeedMps.takeIf { live.movingMs > 0 }),
            stringResource(R.string.summary_max, f.speed(live.maxSpeedMps), f.speedUnit),
        ),
        // Sensor values only when the ride has them: no "--" cells for riders without sensors (D-86).
        sensors.avgHeartRateBpm?.let { bpm ->
            StatItem(
                stringResource(R.string.summary_hr),
                f.integer(bpm),
                stringResource(
                    R.string.summary_max,
                    f.integer(sensors.maxHeartRateBpm),
                    stringResource(R.string.unit_bpm),
                ),
            )
        },
        sensors.avgCadenceRpm?.let { rpm ->
            StatItem(
                stringResource(R.string.summary_cadence),
                f.integer(rpm),
                stringResource(R.string.summary_cadence_caption),
            )
        },
        if (hasPowerMeter) {
            StatItem(
                stringResource(R.string.summary_power),
                f.integer(sensors.avgPowerW),
                stringResource(R.string.unit_watt),
            )
        } else {
            StatItem(
                stringResource(R.string.summary_power_estimate),
                f.integer(sensors.avgEstimatedPowerW),
                stringResource(R.string.summary_power_estimate_caption),
                isLabelWarn = true,
            )
        },
        StatItem(
            stringResource(R.string.summary_calories),
            f.integer(sensors.calories),
            stringResource(R.string.unit_kcal),
        ),
        live.lap?.number?.let { laps ->
            StatItem(
                stringResource(R.string.summary_laps),
                f.integer(laps),
                stringResource(R.string.summary_laps_caption),
            )
        },
    ).toImmutableList()
}

@ThemePreviews
@Composable
private fun SummaryScreenPreview() {
    RideCompPreview { SummaryScreen(state = SummaryUiState(name = "Gowes Pagi", activityId = 1), onEvent = {}) }
}
