package com.ridecomp.feature.history.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.usecase.ExportFormat
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.history.R
import com.ridecomp.feature.history.viewmodel.ActivityDetailEffect
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiEvent
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiState
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.FileMessage
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.ErrorState
import com.ridecomp.ui.component.LineChart
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.StatRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Ride detail (T1.13) with export/share (T1.14) and the Strava guide link (T1.15). */
@Composable
fun ActivityDetailRoute(
    activityId: Long,
    onDeleted: () -> Unit,
    onOpenStravaHelp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityDetailViewModel = koinViewModel { parametersOf(activityId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ActivityDetailEffect.Deleted -> onDeleted()
        }
    }
    val scope = rememberCoroutineScope()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.FIT.mimeType),
    ) { uri -> uri?.let { viewModel.onEvent(ActivityDetailUiEvent.SaveTo(ExportFormat.FIT, it.toString())) } }
    ActivityDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onSaveFit = {
            scope.launch { viewModel.suggestedFileName(ExportFormat.FIT)?.let(saveLauncher::launch) }
        },
        onOpenStravaHelp = onOpenStravaHelp,
        modifier = modifier,
    )
}

@Composable
fun ActivityDetailScreen(
    state: ActivityDetailUiState,
    onEvent: (ActivityDetailUiEvent) -> Unit,
    onSaveFit: () -> Unit,
    onOpenStravaHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = state.activity
    ScreenScaffold(
        modifier = modifier,
        title = activity?.name ?: stringResource(R.string.detail_title),
        banner = state.message?.let { message ->
            { MessageBanner(message, onShown = { onEvent(ActivityDetailUiEvent.MessageShown) }) }
        },
    ) {
        when {
            state.isMissing -> ErrorState(message = stringResource(R.string.detail_not_found))
            activity != null -> Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = RideCompDimens.spacingM),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
            ) {
                Totals(state)
                Charts(state)
                Laps(state)
                Actions(state, onEvent, onSaveFit, onOpenStravaHelp)
            }
        }
    }
}

@Composable
private fun Totals(state: ActivityDetailUiState) {
    val totals = state.activity?.totals ?: return
    val format = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    StatRow(stringResource(R.string.detail_distance), "${format.distance(totals.distanceM)} ${format.distanceUnit}")
    StatRow(stringResource(R.string.detail_moving_time), format.duration(totals.movingS * Units.MS_PER_SECOND))
    StatRow(stringResource(R.string.detail_elapsed_time), format.duration(totals.elapsedS * Units.MS_PER_SECOND))
    StatRow(stringResource(R.string.detail_avg_speed), "${format.speed(totals.avgSpeedMps)} ${format.speedUnit}")
    StatRow(stringResource(R.string.detail_max_speed), "${format.speed(totals.maxSpeedMps)} ${format.speedUnit}")
    StatRow(stringResource(R.string.detail_ascent), "${format.elevation(totals.ascentM)} ${format.elevationUnit}")
    StatRow(stringResource(R.string.detail_descent), "${format.elevation(totals.descentM)} ${format.elevationUnit}")
}

@Composable
private fun Charts(state: ActivityDetailUiState) {
    Column(modifier = Modifier.padding(horizontal = RideCompDimens.spacingM)) {
        if (state.speedChart.size > 1) {
            LineChart(title = stringResource(R.string.detail_chart_speed), values = state.speedChart)
        }
        if (state.elevationChart.any { it.y != null }) {
            LineChart(
                title = stringResource(R.string.detail_chart_elevation),
                values = state.elevationChart,
                color = RideCompTheme.colors.track,
            )
        }
    }
}

@Composable
private fun Laps(state: ActivityDetailUiState) {
    if (state.laps.size < 2) return
    val format = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    Text(
        text = stringResource(R.string.detail_laps),
        style = RideCompTheme.typography.title,
        color = RideCompTheme.colors.textPrimary,
        modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
    )
    state.laps.forEach { lap ->
        StatRow(
            label = stringResource(R.string.detail_lap_row, lap.index + 1),
            value = listOf(
                "${format.distance(lap.distanceM)} ${format.distanceUnit}",
                format.duration(lap.movingS * Units.MS_PER_SECOND),
                "${format.speed(lap.avgSpeedMps)} ${format.speedUnit}",
            ).joinToString(SEPARATOR),
        )
    }
}

@Composable
private fun Actions(
    state: ActivityDetailUiState,
    onEvent: (ActivityDetailUiEvent) -> Unit,
    onSaveFit: () -> Unit,
    onOpenStravaHelp: () -> Unit,
) {
    var isDeleteDialogShown by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(RideCompDimens.spacingM),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        BigButton(
            text = stringResource(R.string.detail_export_fit),
            onClick = { onEvent(ActivityDetailUiEvent.Share(ExportFormat.FIT)) },
            isEnabled = !state.isWorking,
        )
        BigButton(
            text = stringResource(R.string.detail_save_fit),
            onClick = onSaveFit,
            style = BigButtonStyle.SECONDARY,
            isEnabled = !state.isWorking,
        )
        BigButton(
            text = stringResource(R.string.detail_export_gpx),
            onClick = { onEvent(ActivityDetailUiEvent.Share(ExportFormat.GPX)) },
            style = BigButtonStyle.SECONDARY,
            isEnabled = !state.isWorking,
        )
        TextButton(onClick = onOpenStravaHelp) { Text(stringResource(R.string.detail_strava_help)) }
        BigButton(
            text = stringResource(R.string.detail_delete),
            onClick = { isDeleteDialogShown = true },
            style = BigButtonStyle.DANGER,
        )
    }
    if (isDeleteDialogShown) {
        ConfirmDialog(
            title = stringResource(R.string.detail_delete_title),
            message = stringResource(R.string.detail_delete_message),
            confirmLabel = stringResource(R.string.detail_delete),
            dismissLabel = stringResource(R.string.detail_cancel),
            onConfirm = {
                isDeleteDialogShown = false
                onEvent(ActivityDetailUiEvent.Delete)
            },
            onDismiss = { isDeleteDialogShown = false },
        )
    }
}

@Composable
private fun MessageBanner(message: FileMessage, onShown: () -> Unit) {
    val (text, severity) = when (message) {
        FileMessage.EXPORT_FAILED -> stringResource(R.string.detail_export_failed) to AlertSeverity.DANGER
        FileMessage.SAVED -> stringResource(R.string.detail_saved_copy) to AlertSeverity.INFO
    }
    AlertBanner(
        message = text,
        severity = severity,
        actions = listOf(BannerAction(stringResource(R.string.detail_ok), onShown)),
    )
}

@ThemePreviews
@Composable
private fun ActivityDetailMissingPreview() {
    RideCompPreview {
        ActivityDetailScreen(
            state = ActivityDetailUiState(isMissing = true),
            onEvent = {},
            onSaveFit = {},
            onOpenStravaHelp = {},
        )
    }
}
private const val SEPARATOR = " · "
