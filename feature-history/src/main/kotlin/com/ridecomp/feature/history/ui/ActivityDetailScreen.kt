package com.ridecomp.feature.history.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.model.StravaUploadError
import com.ridecomp.core.model.StravaUploadState
import com.ridecomp.core.usecase.AnalysisSectionKind
import com.ridecomp.core.usecase.AnalysisStatKind
import com.ridecomp.core.usecase.ExportFormat
import com.ridecomp.core.usecase.isDuration
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.history.R
import com.ridecomp.feature.history.viewmodel.ActivityDetailEffect
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiEvent
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiState
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.DetailSection
import com.ridecomp.feature.history.viewmodel.FileMessage
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.AnalysisCard
import com.ridecomp.ui.component.AnalysisCardTexts
import com.ridecomp.ui.component.AnalysisRow
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.ChartValue
import com.ridecomp.ui.component.ChartYScale
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.ErrorState
import com.ridecomp.ui.component.IconTile
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideChart
import com.ridecomp.ui.component.RideChartLabels
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.StatGrid
import com.ridecomp.ui.component.StatItem
import com.ridecomp.ui.component.TrackSketch
import com.ridecomp.ui.component.ValueTone
import com.ridecomp.ui.map.RouteMap
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
    onOpenStravaSettings: () -> Unit = {},
    viewModel: ActivityDetailViewModel = koinViewModel { parametersOf(activityId) },
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnlineMap by viewModel.isOnlineMap.collectAsStateWithLifecycle()
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
        onOpenStravaSettings = onOpenStravaSettings,
        isOnlineMap = isOnlineMap,
        onOpenStravaActivity = { stravaId ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "${StravaConfig.ACTIVITY_URL_PREFIX}$stravaId".toUri()),
                )
            } catch (_: ActivityNotFoundException) {
                // No browser: the row keeps saying the ride is on Strava.
            }
        },
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
    /** Strava (T5.3): connect first, or open the uploaded activity. */
    onOpenStravaSettings: () -> Unit = {},
    onOpenStravaActivity: (Long) -> Unit = {},
    /** Draws the track on the online map instead of the sketch (D-90). */
    isOnlineMap: Boolean = false,
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
            activity != null -> {
                val format = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
                // Lazy (T11.2): sections below the screen are composed only when they scroll in, and the map
                // is released while scrolled away. Empty items would still get a gap, so they are left out.
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = RideCompDimens.screenPaddingHorizontal,
                        end = RideCompDimens.screenPaddingHorizontal,
                        bottom = RideCompDimens.screenPaddingBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
                ) {
                    // One item: the route arrives after the totals, and a separate item inserted above the
                    // first visible one would open the page scrolled past the map.
                    item(key = ITEM_HEADER) {
                        Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap)) {
                            if (state.route.size >= MIN_LINE_POINTS) RouteSketch(state, isOnlineMap)
                            Totals(activity.totals, format)
                        }
                    }
                    onShareCard?.let { item(key = ITEM_SHARE) { ShareCardEntry(it) } }
                    items(state.sections, key = { it.kind }) { section ->
                        // Elevation in grey behind speed, as on printed ride summaries.
                        val background = state.sections.takeIf { section.kind == AnalysisSectionKind.SPEED }
                            ?.firstOrNull { it.kind == AnalysisSectionKind.ELEVATION }?.values
                        Section(section, background, format)
                    }
                    if (state.laps.size >= MIN_LAPS) item(key = ITEM_LAPS) { Laps(state, format) }
                    tipContent?.let { item(key = ITEM_TIP) { it() } }
                    item(key = ITEM_EXPORT) {
                        ExportSection(state, onEvent, onSaveFit, onOpenStravaHelp) {
                            StravaRow(state, onEvent, onOpenStravaSettings, onOpenStravaActivity)
                        }
                    }
                    item(key = ITEM_MORE) { MoreSection(onEvent, onCreateSegment) }
                }
            }
        }
    }
}

private val START_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(
    FormatStyle.MEDIUM,
    FormatStyle.SHORT,
)

/**
 * The ride's line: on the online map while there is internet (D-90), else without tiles with start in
 * green and finish in accent; hidden without GPS.
 */
@Composable
private fun RouteSketch(state: ActivityDetailUiState, isOnlineMap: Boolean) {
    if (isOnlineMap) {
        RouteMap(
            isOnline = true,
            regionPaths = persistentListOf(),
            route = state.route,
            description = stringResource(R.string.detail_route),
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.routeMapHeight)
                .clip(RoundedCornerShape(RideCompDimens.cornerRadiusCard)),
        )
        return
    }
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

/** A section of the analysis (T11.2): title with ⓘ, its chart, then its numbers. */
@Composable
private fun Section(section: DetailSection, background: ImmutableList<ChartValue>?, format: UnitFormatter) {
    val kind = section.kind
    val unit = kind.unit(format)
    val decimals = kind.decimals()
    val distanceDecimals = if ((section.values.lastOrNull()?.x ?: 0.0) < SHORT_RIDE_DISTANCE) 2 else 1
    val color = kind.color()
    val title = stringResource(kind.titleRes())
    val rows = section.stats.map { stat ->
        AnalysisRow(
            label = stringResource(stat.kind.labelRes(kind)),
            value = if (stat.kind.isDuration) {
                format.duration(stat.value.toLong() * Units.MS_PER_SECOND)
            } else {
                withUnit(number(stat.value, decimals), unit)
            },
            valueColor = color.takeIf { stat.kind == AnalysisStatKind.MAX },
        )
    }.toImmutableList()
    AnalysisCard(
        texts = AnalysisCardTexts(
            title = title,
            info = stringResource(kind.infoRes()),
            infoLabel = stringResource(R.string.detail_section_info_label, title),
        ),
        rows = rows,
    ) {
        RideChart(
            values = section.values,
            labels = RideChartLabels(
                yUnit = unit,
                xUnit = format.distanceUnit,
                hint = stringResource(R.string.detail_chart_hint),
                // Read aloud as "Speed, Average 20.9 km/h, Maximum 50.4 km/h" (14-PHASE11-PLAN T11.1).
                description = (listOf(title) + rows.map { "${it.label} ${it.value}" }).joinToString(", "),
            ),
            formatX = { withUnit(number(it, distanceDecimals), format.distanceUnit) },
            formatY = { withUnit(number(it, decimals), unit) },
            color = color,
            // Elevation shows its shape; the others start at zero.
            yScale = if (kind == AnalysisSectionKind.ELEVATION) ChartYScale.DATA_RANGE else ChartYScale.FROM_ZERO,
            background = background,
        )
    }
}

private fun number(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), if (decimals == 0) NO_DECIMAL else "%.${decimals}f", value)

/** Below this (km or mi) the distance axis shows two decimals. */
private const val SHORT_RIDE_DISTANCE = 10.0

/** A line needs two points; a lap table, two laps. */
private const val MIN_LINE_POINTS = 2
private const val MIN_LAPS = 2

/** Keys of the page's fixed items (the sections are keyed by their kind). */
private const val ITEM_HEADER = "header"
private const val ITEM_SHARE = "share"
private const val ITEM_LAPS = "laps"
private const val ITEM_TIP = "tip"
private const val ITEM_EXPORT = "export"
private const val ITEM_MORE = "more"
private const val NO_DECIMAL = "%.0f"

/** Lap table; the fastest lap is highlighted in the accent color (and bold, not by color alone). */
@Composable
private fun Laps(state: ActivityDetailUiState, format: UnitFormatter) {
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
    stravaRow: @Composable () -> Unit,
) {
    val colors = RideCompTheme.colors

    // One file action at a time; the rows stay visible while a file is made.
    fun guarded(action: () -> Unit): () -> Unit = { if (!state.isWorking) action() }
    Column {
        SectionHeader(stringResource(R.string.detail_section_export), horizontalPadding = RideCompDimens.none)
        ListCard {
            stravaRow()
            ListCardRow(
                title = stringResource(R.string.detail_send_fit_title),
                subtitle = stringResource(R.string.detail_send_fit_subtitle),
                onClick = guarded { onEvent(ActivityDetailUiEvent.Share(ExportFormat.FIT)) },
                leading = { IconTile(UiR.drawable.ic_share) },
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

/**
 * Upload to Strava (T5.3): connect first when needed, then one tap; while the ride is on its way the row
 * says where it is, and once on Strava it opens the activity.
 */
@Composable
private fun StravaRow(
    state: ActivityDetailUiState,
    onEvent: (ActivityDetailUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenActivity: (Long) -> Unit,
) {
    val row = state.stravaRowLook(onEvent, onOpenSettings, onOpenActivity) ?: return
    val colors = RideCompTheme.colors
    ListCardRow(
        title = stringResource(row.titleRes),
        subtitle = stringResource(row.subtitleRes),
        value = if (row.isDone) stringResource(R.string.detail_strava_sent) else null,
        valueTone = ValueTone.OK,
        onClick = row.onClick,
        leading = {
            IconTile(
                if (row.isDone) UiR.drawable.ic_check else UiR.drawable.ic_upload,
                tint = when {
                    row.isDone -> colors.ok
                    row.isFailed -> colors.danger
                    else -> colors.accent
                },
            )
        },
    )
}

/** What the Strava row says and does; null hides it (a build without Strava, D-82, or still loading). */
private fun ActivityDetailUiState.stravaRowLook(
    onEvent: (ActivityDetailUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenActivity: (Long) -> Unit,
): StravaRowLook? {
    if (stravaStatus == null || stravaStatus == StravaStatus.Unavailable) return null
    val upload = stravaUpload
    val stravaId = upload?.stravaActivityId
    val isConnected = stravaStatus is StravaStatus.Connected
    return when {
        upload?.state?.isOnStrava == true && stravaId != null -> StravaRowLook(
            R.string.detail_strava_view,
            R.string.detail_strava_on_strava,
            onClick = { onOpenActivity(stravaId) },
            isDone = true,
        )
        !isConnected -> StravaRowLook(
            R.string.detail_strava_upload,
            R.string.detail_strava_connect_first,
            onOpenSettings,
        )
        upload?.state == StravaUploadState.PROCESSING ->
            StravaRowLook(R.string.detail_strava_upload, R.string.detail_strava_processing, onClick = null)
        upload?.state == StravaUploadState.QUEUED -> StravaRowLook(
            R.string.detail_strava_upload,
            upload.error.queuedText(),
            onClick = { onEvent(ActivityDetailUiEvent.UploadToStrava) },
        )
        upload?.state == StravaUploadState.FAILED -> StravaRowLook(
            R.string.detail_strava_retry,
            upload.error.failedText(),
            onClick = { onEvent(ActivityDetailUiEvent.UploadToStrava) },
            isFailed = true,
        )
        else -> StravaRowLook(
            R.string.detail_strava_upload,
            R.string.detail_strava_upload_subtitle,
            onClick = { onEvent(ActivityDetailUiEvent.UploadToStrava) },
        )
    }
}

private class StravaRowLook(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val onClick: (() -> Unit)?,
    val isDone: Boolean = false,
    val isFailed: Boolean = false,
)

@StringRes
private fun StravaUploadError?.queuedText(): Int = when (this) {
    StravaUploadError.OFFLINE -> R.string.detail_strava_waiting_internet
    StravaUploadError.RATE_LIMITED, StravaUploadError.SERVER -> R.string.detail_strava_busy
    StravaUploadError.NOT_CONNECTED, StravaUploadError.REJECTED, StravaUploadError.FILE, null ->
        R.string.detail_strava_sending
}

@StringRes
private fun StravaUploadError?.failedText(): Int = when (this) {
    StravaUploadError.NOT_CONNECTED -> R.string.detail_strava_failed_account
    StravaUploadError.REJECTED -> R.string.detail_strava_failed_rejected
    StravaUploadError.FILE -> R.string.detail_strava_failed_file
    StravaUploadError.OFFLINE, StravaUploadError.RATE_LIMITED, StravaUploadError.SERVER, null ->
        R.string.detail_strava_failed
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
private fun AnalysisSectionKind.titleRes(): Int = when (this) {
    AnalysisSectionKind.SPEED -> R.string.detail_section_speed
    AnalysisSectionKind.ELEVATION -> R.string.detail_section_elevation
    AnalysisSectionKind.HEART_RATE -> R.string.detail_section_heart_rate
    AnalysisSectionKind.POWER -> R.string.detail_section_power
    AnalysisSectionKind.CADENCE -> R.string.detail_section_cadence
}

@StringRes
private fun AnalysisSectionKind.infoRes(): Int = when (this) {
    AnalysisSectionKind.SPEED -> R.string.detail_info_speed
    AnalysisSectionKind.ELEVATION -> R.string.detail_info_elevation
    AnalysisSectionKind.HEART_RATE -> R.string.detail_info_heart_rate
    AnalysisSectionKind.POWER -> R.string.detail_info_power
    AnalysisSectionKind.CADENCE -> R.string.detail_info_cadence
}

@Composable
private fun AnalysisSectionKind.color(): Color = when (this) {
    AnalysisSectionKind.SPEED, AnalysisSectionKind.CADENCE -> RideCompTheme.colors.accent
    AnalysisSectionKind.ELEVATION -> RideCompTheme.colors.track
    AnalysisSectionKind.HEART_RATE -> RideCompTheme.colors.danger
    AnalysisSectionKind.POWER -> RideCompTheme.colors.warn
}

@StringRes
private fun AnalysisStatKind.labelRes(section: AnalysisSectionKind): Int = when (this) {
    // Speed averages only while moving (T11.3); said so, next to the moving time.
    AnalysisStatKind.AVG -> if (section ==
        AnalysisSectionKind.SPEED
    ) {
        R.string.detail_stat_avg_moving
    } else {
        R.string.detail_stat_avg
    }
    AnalysisStatKind.MAX -> R.string.detail_stat_max
    AnalysisStatKind.MIN -> R.string.detail_stat_min
    AnalysisStatKind.ASCENT -> R.string.detail_stat_ascent
    AnalysisStatKind.DESCENT -> R.string.detail_stat_descent
    AnalysisStatKind.NORMALIZED -> R.string.detail_stat_normalized
    AnalysisStatKind.MOVING_TIME -> R.string.detail_moving_time
    AnalysisStatKind.ELAPSED_TIME -> R.string.detail_elapsed_time
}

@Composable
private fun AnalysisSectionKind.unit(format: UnitFormatter): String = when (this) {
    AnalysisSectionKind.SPEED -> format.speedUnit
    AnalysisSectionKind.ELEVATION -> format.elevationUnit
    AnalysisSectionKind.HEART_RATE -> stringResource(R.string.detail_unit_bpm)
    AnalysisSectionKind.CADENCE -> stringResource(R.string.detail_unit_rpm)
    AnalysisSectionKind.POWER -> stringResource(R.string.detail_unit_watt)
}

/** Speed reads to 0.1; the others are whole numbers. */
private fun AnalysisSectionKind.decimals(): Int = if (this == AnalysisSectionKind.SPEED) 1 else 0
