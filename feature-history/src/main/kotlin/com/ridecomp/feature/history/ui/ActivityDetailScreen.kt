package com.ridecomp.feature.history.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.usecase.ExportFormat
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.history.R
import com.ridecomp.feature.history.viewmodel.ActivityDetailEffect
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiEvent
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiState
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.ChartKind
import com.ridecomp.feature.history.viewmodel.FileMessage
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.ErrorState
import com.ridecomp.ui.component.LineChart
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SegmentedTabs
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Ride detail (T1.13, 13-UI-SCREENS #23) with export/share (T1.14) and the Strava guide link (T1.15). */
@Composable
fun ActivityDetailRoute(
    activityId: Long,
    onDeleted: () -> Unit,
    onOpenStravaHelp: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onCreateSegment: (() -> Unit)? = null,
    onShareCard: (() -> Unit)? = null,
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
        onBack = onBack,
        onCreateSegment = onCreateSegment,
        onShareCard = onShareCard,
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
    onBack: () -> Unit = {},
    /** Opens the segment editor (T4.2); null hides the action. */
    onCreateSegment: (() -> Unit)? = null,
    /** Opens the share card editor (T4.5); null hides the action. */
    onShareCard: (() -> Unit)? = null,
) {
    val activity = state.activity
    ScreenScaffold(
        modifier = modifier,
        title = activity?.name ?: stringResource(R.string.detail_title),
        subtitle = activity?.let {
            START_FORMAT.format(Instant.ofEpochMilli(it.startedAtMs).atZone(ZoneId.systemDefault()))
        },
        onBack = onBack,
        backLabel = stringResource(R.string.detail_back),
        banner = state.message?.let { message ->
            { MessageBanner(message, onShown = { onEvent(ActivityDetailUiEvent.MessageShown) }) }
        },
    ) {
        when {
            state.isMissing -> ErrorState(message = stringResource(R.string.detail_not_found))
            activity != null -> Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = RideCompDimens.screenPaddingHorizontal,
                        end = RideCompDimens.screenPaddingHorizontal,
                        bottom = RideCompDimens.screenPaddingBottom,
                    ),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
            ) {
                val format = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
                Totals(activity.totals, format)
                Charts(state)
                Laps(state, format)
                onShareCard?.let { ShareCardButton(it) }
                Actions(state, onEvent, onSaveFit, onOpenStravaHelp, onCreateSegment)
            }
        }
    }
}

private val START_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(
    FormatStyle.MEDIUM,
    FormatStyle.SHORT,
)

/** Four headline numbers (13-UI-SCREENS #23). */
@Composable
private fun Totals(totals: RideTotals, format: UnitFormatter) {
    RideCompCard {
        Row {
            TotalStat(format.distance(totals.distanceM), format.distanceUnit, Modifier.weight(1f))
            TotalStat(
                format.duration(totals.movingS * Units.MS_PER_SECOND),
                stringResource(R.string.detail_moving_short),
                Modifier.weight(1f),
            )
            TotalStat(
                format.elevation(totals.ascentM),
                stringResource(R.string.detail_ascent_short, format.elevationUnit),
                Modifier.weight(1f),
            )
            TotalStat(
                format.integer(totals.avgHeartRateBpm),
                stringResource(R.string.detail_avg_bpm),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TotalStat(value: String, caption: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = RideCompTheme.typography.cardTitle, color = RideCompTheme.colors.textPrimary)
        Text(text = caption, style = RideCompTheme.typography.navLabel, color = RideCompTheme.colors.textSecondary)
    }
}

/** One chart at a time, chosen with segmented tabs (only kinds with data). */
@Composable
private fun Charts(state: ActivityDetailUiState) {
    if (state.charts.isEmpty()) return
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, state.charts.lastIndex)
    val tabs = state.charts.map { stringResource(it.kind.tabRes()) }.toImmutableList()
    SegmentedTabs(tabs = tabs, selectedIndex = index, onSelect = { selected = it })
    val chart = state.charts[index]
    RideCompCard {
        LineChart(
            title = stringResource(R.string.detail_chart_vs_distance, stringResource(chart.kind.titleRes())),
            values = chart.values,
            color = chart.kind.color(),
        )
    }
}

/** Lap table; the fastest lap is highlighted in the accent color (and bold, not by color alone). */
@Composable
private fun Laps(state: ActivityDetailUiState, format: UnitFormatter) {
    if (state.laps.size < 2) return
    val fastest = state.laps.maxByOrNull { it.avgSpeedMps }
    RideCompCard {
        LapRow(
            listOf(
                stringResource(R.string.detail_lap_column),
                stringResource(R.string.detail_lap_distance),
                stringResource(R.string.detail_lap_time),
                format.speedUnit.uppercase(),
                stringResource(R.string.detail_lap_hr),
            ),
            isHeader = true,
        )
        state.laps.forEach { lap ->
            LapRow(
                listOf(
                    (lap.index + 1).toString(),
                    format.distance(lap.distanceM),
                    format.duration(lap.movingS * Units.MS_PER_SECOND),
                    format.speed(lap.avgSpeedMps),
                    format.integer(lap.avgHeartRateBpm),
                ),
                isHighlighted = lap == fastest,
            )
        }
    }
}

@Composable
private fun LapRow(cells: List<String>, isHeader: Boolean = false, isHighlighted: Boolean = false) {
    val colors = RideCompTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEachIndexed { column, text ->
            val isSpeed = column == SPEED_COLUMN
            Text(
                text = text,
                style = if (isHeader) {
                    RideCompTheme.typography.navLabel
                } else if (isHighlighted && isSpeed) {
                    RideCompTheme.typography.bodyStrong
                } else {
                    RideCompTheme.typography.body
                },
                color = when {
                    isHeader -> colors.textSecondary
                    isHighlighted && isSpeed -> colors.accent
                    else -> colors.textPrimary
                },
                modifier = Modifier.weight(LAP_COLUMN_WEIGHTS[column]),
            )
        }
    }
}

/** LAP · JARAK · WAKTU · KM/H · HR widths (13-UI-SCREENS #23). */
private val LAP_COLUMN_WEIGHTS = listOf(0.6f, 1f, 1f, 1f, 0.8f)
private const val SPEED_COLUMN = 3

@Composable
private fun Actions(
    state: ActivityDetailUiState,
    onEvent: (ActivityDetailUiEvent) -> Unit,
    onSaveFit: () -> Unit,
    onOpenStravaHelp: () -> Unit,
    onCreateSegment: (() -> Unit)?,
) {
    var isDeleteDialogShown by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
            BigButton(
                text = stringResource(R.string.detail_export_fit),
                onClick = onSaveFit,
                style = BigButtonStyle.OUTLINE,
                size = BigButtonSize.TERTIARY,
                iconRes = UiR.drawable.ic_download,
                isEnabled = !state.isWorking,
                modifier = Modifier.weight(1f),
            )
            BigButton(
                text = stringResource(R.string.detail_share_fit),
                onClick = { onEvent(ActivityDetailUiEvent.Share(ExportFormat.FIT)) },
                style = BigButtonStyle.SECONDARY,
                size = BigButtonSize.TERTIARY,
                iconRes = UiR.drawable.ic_share,
                isEnabled = !state.isWorking,
                modifier = Modifier.weight(1f),
            )
        }
        LinkText(stringResource(R.string.detail_export_gpx), onClick = {
            onEvent(ActivityDetailUiEvent.Share(ExportFormat.GPX))
        })
        LinkText(stringResource(R.string.detail_strava_help), onClick = onOpenStravaHelp)
        onCreateSegment?.let { LinkText(stringResource(R.string.detail_create_segment), onClick = it) }
        BigButton(
            text = stringResource(R.string.detail_delete),
            onClick = { isDeleteDialogShown = true },
            style = BigButtonStyle.TEXT_DANGER,
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
        FileMessage.SAVED -> stringResource(R.string.detail_saved_copy) to AlertSeverity.OK
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

@StringRes
private fun ChartKind.titleRes(): Int = when (this) {
    ChartKind.SPEED -> R.string.detail_chart_speed
    ChartKind.ELEVATION -> R.string.detail_chart_elevation
    ChartKind.HEART_RATE -> R.string.detail_chart_heart_rate
    ChartKind.CADENCE -> R.string.detail_chart_cadence
    ChartKind.POWER -> R.string.detail_chart_power
}

@StringRes
private fun ChartKind.tabRes(): Int = when (this) {
    ChartKind.SPEED -> R.string.detail_tab_speed
    ChartKind.ELEVATION -> R.string.detail_tab_elevation
    ChartKind.HEART_RATE -> R.string.detail_tab_heart_rate
    ChartKind.CADENCE -> R.string.detail_tab_cadence
    ChartKind.POWER -> R.string.detail_tab_power
}

@Composable
private fun ChartKind.color(): Color = when (this) {
    ChartKind.SPEED, ChartKind.CADENCE -> RideCompTheme.colors.accent
    ChartKind.ELEVATION -> RideCompTheme.colors.track
    ChartKind.HEART_RATE -> RideCompTheme.colors.danger
    ChartKind.POWER -> RideCompTheme.colors.warn
}

/** "Bagikan" as an image card for WhatsApp/Instagram (13-UI-SCREENS #21). */
@Composable
private fun ShareCardButton(onClick: () -> Unit) {
    BigButton(
        text = stringResource(R.string.detail_share_card),
        onClick = onClick,
        size = BigButtonSize.SECONDARY,
        iconRes = UiR.drawable.ic_share,
    )
}
