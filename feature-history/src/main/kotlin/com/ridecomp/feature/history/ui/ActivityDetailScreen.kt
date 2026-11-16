package com.ridecomp.feature.history.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.ridecomp.feature.history.viewmodel.ChartStat
import com.ridecomp.feature.history.viewmodel.ChartStatKind
import com.ridecomp.feature.history.viewmodel.FileMessage
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.AxisLineChart
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.ErrorState
import com.ridecomp.ui.component.IconTile
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.SegmentedTabs
import com.ridecomp.ui.component.StatGrid
import com.ridecomp.ui.component.StatItem
import com.ridecomp.ui.component.TrackSketch
import com.ridecomp.ui.component.ValueTone
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
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
    tipContent: (@Composable () -> Unit)? = null,
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
        tipContent = tipContent,
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
    /** The export tip of the guide (Phase 4G), from the app. */
    tipContent: (@Composable () -> Unit)? = null,
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
                RouteSketch(state)
                Totals(activity.totals, format)
                onShareCard?.let { ShareCardEntry(it) }
                Charts(state, format)
                Laps(state, format)
                tipContent?.invoke()
                ExportSection(state, onEvent, onSaveFit, onOpenStravaHelp)
                MoreSection(onEvent, onCreateSegment)
            }
        }
    }
}

private val START_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(
    FormatStyle.MEDIUM,
    FormatStyle.SHORT,
)

/** The ride's line without map tiles, start in green and finish in accent; hidden without GPS. */
@Composable
private fun RouteSketch(state: ActivityDetailUiState) {
    if (state.route.size < 2) return
    TrackSketch(
        points = state.route,
        description = stringResource(R.string.detail_route),
        lineColor = RideCompTheme.colors.accent,
        hasEndpoints = true,
        modifier = Modifier.height(RideCompDimens.routeSketchHeight),
    )
}

/** Headline numbers; sensor values only when the ride has them. */
@Composable
private fun Totals(totals: RideTotals, format: UnitFormatter) {
    val items = buildList {
        add(
            StatItem(
                stringResource(R.string.detail_distance),
                format.distance(totals.distanceM),
                unit = format.distanceUnit,
            ),
        )
        add(
            StatItem(
                stringResource(R.string.detail_moving_time),
                format.duration(totals.movingS * Units.MS_PER_SECOND),
            ),
        )
        add(
            StatItem(
                stringResource(R.string.detail_avg_speed),
                format.speed(totals.avgSpeedMps),
                unit = format.speedUnit,
            ),
        )
        add(
            StatItem(
                stringResource(R.string.detail_max_speed),
                format.speed(totals.maxSpeedMps),
                unit = format.speedUnit,
            ),
        )
        add(
            StatItem(
                stringResource(R.string.detail_ascent),
                format.elevation(totals.ascentM),
                unit = format.elevationUnit,
            ),
        )
        add(
            StatItem(
                stringResource(R.string.detail_elapsed_time),
                format.duration(totals.elapsedS * Units.MS_PER_SECOND),
            ),
        )
        totals.avgHeartRateBpm?.let { bpm ->
            val unit = stringResource(R.string.detail_unit_bpm)
            add(
                StatItem(
                    label = stringResource(R.string.detail_avg_hr),
                    value = format.integer(bpm),
                    unit = unit,
                    caption = totals.maxHeartRateBpm?.let {
                        stringResource(R.string.detail_max_caption, withUnit(it.toString(), unit))
                    },
                ),
            )
        }
        totals.avgPowerW?.let { watts ->
            add(
                StatItem(
                    stringResource(R.string.detail_avg_power),
                    format.integer(watts),
                    unit = stringResource(R.string.detail_unit_watt),
                ),
            )
        }
        totals.avgCadenceRpm?.let { rpm ->
            add(
                StatItem(
                    stringResource(R.string.detail_avg_cadence),
                    format.integer(rpm),
                    unit = stringResource(R.string.detail_unit_rpm),
                ),
            )
        }
        totals.calories?.let { kcal ->
            add(
                StatItem(
                    stringResource(R.string.detail_calories),
                    format.integer(kcal),
                    unit = stringResource(R.string.detail_unit_kcal),
                ),
            )
        }
    }
    StatGrid(items = items.toImmutableList())
}

private fun withUnit(value: String, unit: String): String = "$value $unit"

/** "Bagikan sebagai gambar": the one highlighted action of the screen (13-UI-SCREENS #21). */
@Composable
private fun ShareCardEntry(onClick: () -> Unit) {
    val colors = RideCompTheme.colors
    RideCompCard(tone = CardTone.ACCENT, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            IconTile(UiR.drawable.ic_share)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.detail_share_card_title),
                    style = RideCompTheme.typography.bodyStrong,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.detail_share_card_subtitle),
                    style = RideCompTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
            Icon(
                painter = painterResource(UiR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(RideCompDimens.iconSize),
            )
        }
    }
}

/** One chart at a time (tabs when there are several), with its summary and numbers on the axes. */
@Composable
private fun Charts(state: ActivityDetailUiState, format: UnitFormatter) {
    if (state.charts.isEmpty()) return
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, state.charts.lastIndex)
    if (state.charts.size > 1) {
        val tabs = state.charts.map { stringResource(it.kind.tabRes()) }.toImmutableList()
        SegmentedTabs(tabs = tabs, selectedIndex = index, onSelect = { selected = it })
    }
    val chart = state.charts[index]
    val unit = chart.kind.unit(format)
    val decimals = chart.kind.decimals()
    val distanceDecimals = if ((chart.values.lastOrNull()?.x ?: 0.0) < SHORT_RIDE_DISTANCE) 2 else 1
    val color = chart.kind.color()
    RideCompCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(chart.kind.tabRes()),
                style = RideCompTheme.typography.cardTitle,
                color = RideCompTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM)) {
                chart.stats.forEach { stat ->
                    ChartStatValue(stat, withUnit(number(stat.value, decimals), unit), color)
                }
            }
        }
        AxisLineChart(
            values = chart.values,
            formatX = { withUnit(number(it, distanceDecimals), format.distanceUnit) },
            formatY = { withUnit(number(it, decimals), unit) },
            formatYAxis = { number(it, decimals) },
            hint = stringResource(R.string.detail_chart_hint),
            description = stringResource(chart.kind.titleRes()),
            color = color,
        )
    }
}

@Composable
private fun ChartStatValue(stat: ChartStat, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(stat.kind.labelRes()).uppercase(),
            style = RideCompTheme.typography.navLabel,
            color = RideCompTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = RideCompTheme.typography.bodyStrong,
            color = if (stat.kind == ChartStatKind.MAX) color else RideCompTheme.colors.textPrimary,
        )
    }
}

private fun number(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), if (decimals == 0) NO_DECIMAL else "%.${decimals}f", value)

/** Below this (km or mi) the distance axis shows two decimals. */
private const val SHORT_RIDE_DISTANCE = 10.0
private const val NO_DECIMAL = "%.0f"

/** Lap table; the fastest lap is highlighted in the accent color (and bold, not by color alone). */
@Composable
private fun Laps(state: ActivityDetailUiState, format: UnitFormatter) {
    if (state.laps.size < 2) return
    val fastest = state.laps.maxByOrNull { it.avgSpeedMps }
    RideCompCard {
        Text(
            text = stringResource(R.string.detail_laps),
            style = RideCompTheme.typography.cardTitle,
            color = RideCompTheme.colors.textPrimary,
        )
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

/** Files and Strava: one list, each row says what the file is for. */
@Composable
private fun ExportSection(
    state: ActivityDetailUiState,
    onEvent: (ActivityDetailUiEvent) -> Unit,
    onSaveFit: () -> Unit,
    onOpenStravaHelp: () -> Unit,
) {
    val colors = RideCompTheme.colors

    // One file action at a time; the rows stay visible while a file is made.
    fun guarded(action: () -> Unit): () -> Unit = { if (!state.isWorking) action() }
    Column {
        SectionHeader(stringResource(R.string.detail_section_export), horizontalPadding = RideCompDimens.none)
        ListCard {
            ListCardRow(
                title = stringResource(R.string.detail_send_fit_title),
                subtitle = stringResource(R.string.detail_send_fit_subtitle),
                onClick = guarded { onEvent(ActivityDetailUiEvent.Share(ExportFormat.FIT)) },
                leading = { IconTile(UiR.drawable.ic_upload) },
            )
            ListCardRow(
                title = stringResource(R.string.detail_save_fit_title),
                subtitle = stringResource(R.string.detail_save_fit_subtitle),
                value = state.activity?.fitExportedAtMs?.let { stringResource(R.string.history_fit_exported) },
                valueTone = ValueTone.OK,
                onClick = guarded(onSaveFit),
                leading = { IconTile(UiR.drawable.ic_download) },
            )
            ListCardRow(
                title = stringResource(R.string.detail_send_gpx_title),
                subtitle = stringResource(R.string.detail_send_gpx_subtitle),
                onClick = guarded { onEvent(ActivityDetailUiEvent.Share(ExportFormat.GPX)) },
                leading = { IconTile(UiR.drawable.ic_map) },
            )
            ListCardRow(
                title = stringResource(R.string.detail_strava_help),
                subtitle = stringResource(R.string.detail_strava_help_subtitle),
                onClick = onOpenStravaHelp,
                leading = { IconTile(UiR.drawable.ic_info, tint = colors.ok) },
            )
            FormatHelp()
        }
    }
}

/** "Apa itu FIT dan GPX?": opens in place, so a new rider can choose without leaving the screen. */
@Composable
private fun FormatHelp() {
    val colors = RideCompTheme.colors
    var isOpen by rememberSaveable { mutableStateOf(false) }
    ListCardRow(
        title = stringResource(R.string.detail_formats_title),
        subtitle = stringResource(R.string.detail_formats_subtitle),
        onClick = { isOpen = !isOpen },
        hasDivider = false,
        hasChevron = false,
        leading = { IconTile(UiR.drawable.ic_help, tint = colors.ok) },
        trailing = {
            Icon(
                painter = painterResource(UiR.drawable.ic_chevron_down),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(RideCompDimens.iconSize)
                    .rotate(if (isOpen) HALF_TURN_DEG else 0f),
            )
        },
    )
    if (isOpen) {
        Column(
            modifier = Modifier.padding(
                start = RideCompDimens.cardPaddingHorizontal,
                end = RideCompDimens.cardPaddingHorizontal,
                bottom = RideCompDimens.cardPaddingVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            FormatInfo(stringResource(R.string.detail_format_fit), stringResource(R.string.detail_format_fit_text))
            FormatInfo(stringResource(R.string.detail_format_gpx), stringResource(R.string.detail_format_gpx_text))
            Text(
                text = stringResource(R.string.detail_formats_advice),
                style = RideCompTheme.typography.caption,
                color = colors.ok,
            )
        }
    }
}

/** A format name as a small badge, then what the file holds and where it goes. */
@Composable
private fun FormatInfo(name: String, text: String) {
    val colors = RideCompTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        Text(
            text = name,
            style = RideCompTheme.typography.navLabel,
            color = colors.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(RideCompDimens.formatBadgeWidth)
                .clip(RoundedCornerShape(RideCompDimens.cornerRadius))
                .background(colors.accentTint)
                .padding(vertical = RideCompDimens.spacingXs),
        )
        Text(
            text = text,
            style = RideCompTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val HALF_TURN_DEG = 180f

@Composable
private fun MoreSection(onEvent: (ActivityDetailUiEvent) -> Unit, onCreateSegment: (() -> Unit)?) {
    var isDeleteDialogShown by remember { mutableStateOf(false) }
    Column {
        SectionHeader(stringResource(R.string.detail_section_more), horizontalPadding = RideCompDimens.none)
        ListCard {
            onCreateSegment?.let {
                ListCardRow(
                    title = stringResource(R.string.detail_create_segment),
                    subtitle = stringResource(R.string.detail_create_segment_subtitle),
                    onClick = it,
                    leading = { IconTile(UiR.drawable.ic_trophy, tint = RideCompTheme.colors.warn) },
                )
            }
            ListCardRow(
                title = stringResource(R.string.detail_delete),
                onClick = { isDeleteDialogShown = true },
                hasDivider = false,
                hasChevron = false,
                isDestructive = true,
                leading = { IconTile(UiR.drawable.ic_trash, tint = RideCompTheme.colors.danger) },
            )
        }
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

@StringRes
private fun ChartStatKind.labelRes(): Int = when (this) {
    ChartStatKind.AVG -> R.string.detail_stat_avg
    ChartStatKind.MAX -> R.string.detail_stat_max
    ChartStatKind.MIN -> R.string.detail_stat_min
}

@Composable
private fun ChartKind.unit(format: UnitFormatter): String = when (this) {
    ChartKind.SPEED -> format.speedUnit
    ChartKind.ELEVATION -> format.elevationUnit
    ChartKind.HEART_RATE -> stringResource(R.string.detail_unit_bpm)
    ChartKind.CADENCE -> stringResource(R.string.detail_unit_rpm)
    ChartKind.POWER -> stringResource(R.string.detail_unit_watt)
}

/** Speed reads to 0.1; the others are whole numbers. */
private fun ChartKind.decimals(): Int = if (this == ChartKind.SPEED) 1 else 0
