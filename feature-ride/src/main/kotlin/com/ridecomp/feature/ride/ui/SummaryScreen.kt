package com.ridecomp.feature.ride.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.ui.component.rememberMetricUnitLabels
import com.ridecomp.feature.ride.viewmodel.SummaryEffect
import com.ridecomp.feature.ride.viewmodel.SummaryUiEvent
import com.ridecomp.feature.ride.viewmodel.SummaryUiState
import com.ridecomp.feature.ride.viewmodel.SummaryViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.StatRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.udf.ObserveEffects
import org.koin.androidx.compose.koinViewModel

/** Metrics on the summary (02-DESIGN §3.6); sensor values and charts follow in Phase 2 / detail. */
private val SUMMARY_METRICS = listOf(
    RideMetric.DISTANCE,
    RideMetric.MOVING_TIME,
    RideMetric.ELAPSED_TIME,
    RideMetric.AVG_SPEED,
    RideMetric.MAX_SPEED,
    RideMetric.ASCENT,
    RideMetric.DESCENT,
)

/** Summary after finishing (T1.12): name, totals, SAVE or DISCARD. */
@Composable
fun SummaryRoute(
    onSaved: (activityId: Long) -> Unit,
    onDiscarded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is SummaryEffect.Saved -> onSaved(effect.activityId)
            SummaryEffect.Discarded -> onDiscarded()
        }
    }
    SummaryScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun SummaryScreen(state: SummaryUiState, onEvent: (SummaryUiEvent) -> Unit, modifier: Modifier = Modifier) {
    var isDiscardDialogShown by remember { mutableStateOf(false) }
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    val labels = rememberMetricUnitLabels()
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.summary_title)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(SummaryUiEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.summary_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
            )
            SUMMARY_METRICS.forEach { metric ->
                StatRow(
                    label = stringResource(metric.labelRes),
                    value = metric.value(state.live, formatter) + (
                        metric.unit(formatter, labels)?.let {
                            " $it"
                        } ?: ""
                        ),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
            ) {
                BigButton(
                    text = stringResource(R.string.summary_save),
                    onClick = { onEvent(SummaryUiEvent.Save) },
                    isEnabled = !state.isWorking && state.activityId != null,
                )
                BigButton(
                    text = stringResource(R.string.ride_discard),
                    onClick = { isDiscardDialogShown = true },
                    style = BigButtonStyle.DANGER,
                    isEnabled = !state.isWorking,
                )
            }
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

@ThemePreviews
@Composable
private fun SummaryScreenPreview() {
    RideCompPreview { SummaryScreen(state = SummaryUiState(name = "Gowes Pagi", activityId = 1), onEvent = {}) }
}
