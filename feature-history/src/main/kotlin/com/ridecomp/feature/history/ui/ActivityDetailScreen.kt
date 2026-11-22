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
import com.ridecomp.core.usecase.FtpEstimate
import com.ridecomp.core.usecase.FtpSource
import com.ridecomp.core.usecase.ZoneTimeSlice
import com.ridecomp.core.usecase.isDuration
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.history.R
import com.ridecomp.feature.history.viewmodel.ActivityDetailEffect
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiEvent
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiState
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.DetailPowerZones
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
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideChart
import com.ridecomp.ui.component.RideChartLabels
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.StatGrid
import com.ridecomp.ui.component.StatItem
import com.ridecomp.ui.component.TipCard
import com.ridecomp.ui.component.TrackSketch
import com.ridecomp.ui.component.ValueTone
import com.ridecomp.ui.component.ZoneBar
import com.ridecomp.ui.component.ZoneBars
import com.ridecomp.ui.map.MapSource
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
import kotlin.math.roundToInt
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
    /** Heart-rate analysis (T11.5): pair a sensor, set HR max. */
    onOpenSensors: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    /** The "Results" card (T11.7), from the app: shown below the totals. */
    resultsContent: (@Composable () -> Unit)? = null,
    viewModel: ActivityDetailViewModel = koinViewModel { parametersOf(activityId) },
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapSource by viewModel.mapSource.collectAsStateWithLifecycle()
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
        mapSource = mapSource,
        onOpenSensors = onOpenSensors,
        onOpenProfile = onOpenProfile,
        resultsContent = resultsContent,
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
    mapSource: MapSource = MapSource(),
    /** Heart-rate analysis (T11.5): pair a sensor, set HR max. */
    onOpenSensors: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    /** The "Results" card (T11.7), from the app: shown below the totals. */
    resultsContent: (@Composable () -> Unit)? = null,
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
                    // first visible one would open the page scrolled past the map. The results may stay empty:
                    // inside the column they leave no gap.
                    item(key = ITEM_HEADER) {
                        Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap)) {
                            if (state.route.size >= MIN_LINE_POINTS) RouteSketch(state, mapSource)
                            Totals(activity.totals, format)
                            resultsContent?.invoke()
                        }
                    }
                    onShareCard?.let { item(key = ITEM_SHARE) { ShareCardEntry(it) } }
                    items(state.sections, key = { it.kind }) { section ->
                        SectionItem(state, section, format, onOpenProfile)
                    }
                    state.powerZones?.takeIf { it.isEstimatedPower }?.let { zones ->
                        item(key = ITEM_ESTIMATED_POWER_ZONES) { EstimatedPowerZonesCard(zones, format, onOpenProfile) }
                    }
                    if (state.isHeartRateTipShown) {
                        item(key = ITEM_HEART_RATE_TIP) {
                            TipCard(
                                title = stringResource(R.string.detail_hr_tip_title),
                                text = stringResource(R.string.detail_hr_tip_text),
                                learnMoreLabel = stringResource(R.string.detail_hr_tip_open_sensors),
                                closeLabel = stringResource(R.string.detail_hr_tip_close),
                                onLearnMore = onOpenSensors,
                                onClose = { onEvent(ActivityDetailUiEvent.DismissHeartRateTip) },
                                iconRes = UiR.drawable.ic_heart,
                                iconTint = RideCompTheme.colors.danger,
                            )
                        }
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
 * The ride's line: on the online map while there is internet or from the tile cache (D-90, T11.11), else
 * without tiles with start in green and finish in accent; hidden without GPS.
 */
@Composable
private fun RouteSketch(state: ActivityDetailUiState, mapSource: MapSource) {
    if (mapSource.hasTiles) {
        RouteMap(
            isOnline = mapSource.isOnline,
            isCached = mapSource.isCached,
            regionPaths = persistentListOf(),
            route = state.route,
            gaps = state.routeGaps,
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
        gaps = state.routeGaps,
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

/** A section with what goes under its numbers: heart-rate zones (T11.5), power-meter zones (T11.6). */
@Composable
private fun SectionItem(
    state: ActivityDetailUiState,
    section: DetailSection,
    format: UnitFormatter,
    onOpenProfile: () -> Unit,
) {
    // Elevation in grey behind speed, as on printed ride summaries.
    val background = state.sections.takeIf { section.kind == AnalysisSectionKind.SPEED }
        ?.firstOrNull { it.kind == AnalysisSectionKind.ELEVATION }?.values
    Section(section, background, format) {
        when (section.kind) {
            AnalysisSectionKind.HEART_RATE -> HeartRateZonesFooter(state, format, onOpenProfile)
            AnalysisSectionKind.POWER -> state.powerZones?.takeUnless { it.isEstimatedPower }?.let {
                PowerZonesBlock(it, format, hasHeading = true, onOpenProfile = onOpenProfile)
            }
            AnalysisSectionKind.ELEVATION -> if (state.isAscentEstimated) {
                Text(
                    text = stringResource(R.string.detail_ascent_estimated),
                    style = RideCompTheme.typography.caption,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
            AnalysisSectionKind.SPEED, AnalysisSectionKind.CADENCE -> Unit
        }
    }
}

/** A section of the analysis (T11.2): title with ⓘ, its chart, then its numbers. */
@Composable
private fun Section(
    section: DetailSection,
    background: ImmutableList<ChartValue>?,
    format: UnitFormatter,
    footer: @Composable () -> Unit = {},
) {
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
        footer = footer,
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
            // Heart rate: its average as a dashed line (T11.5).
            averageY = section.stats.firstOrNull { it.kind == AnalysisStatKind.AVG }
                ?.value?.takeIf { kind == AnalysisSectionKind.HEART_RATE },
        )
    }
}

/**
 * Time in zones Z1..Z5 under the heart-rate numbers (T11.5) and the HR max it is based on, with a link to the
 * profile; without an HR max (nor birth year) only the invitation to fill it.
 */
@Composable
private fun HeartRateZonesFooter(state: ActivityDetailUiState, format: UnitFormatter, onOpenProfile: () -> Unit) {
    val colors = RideCompTheme.colors
    val zones = state.heartRateZones
    if (zones == null && !state.isHrMaxMissing) return
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        Text(
            text = stringResource(R.string.detail_zones_title),
            style = RideCompTheme.typography.bodyStrong,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = RideCompDimens.spacingS),
        )
        if (zones != null) {
            ZoneBars(zones = zoneBars(zones.zones, zones.totalSeconds, ZoneKind.HEART_RATE, format))
            Text(
                text = stringResource(
                    if (zones.isEstimated) R.string.detail_zones_based_on_age else R.string.detail_zones_based_on,
                    zones.hrMaxBpm,
                ),
                style = RideCompTheme.typography.caption,
                color = colors.textSecondary,
            )
            LinkText(text = stringResource(R.string.detail_zones_edit_profile), onClick = onOpenProfile)
        } else {
            Text(
                text = stringResource(R.string.detail_zones_missing_hr_max),
                style = RideCompTheme.typography.body,
                color = colors.textSecondary,
            )
            LinkText(text = stringResource(R.string.detail_zones_open_profile), onClick = onOpenProfile)
        }
    }
}

/** Heart-rate zones (bpm, Z1 below a bound) or power zones (W, Z1 up to and including its bound). */
private enum class ZoneKind(val names: List<Int>) {
    HEART_RATE(
        listOf(
            R.string.detail_zone_1,
            R.string.detail_zone_2,
            R.string.detail_zone_3,
            R.string.detail_zone_4,
            R.string.detail_zone_5,
        ),
    ),
    POWER(
        listOf(
            R.string.detail_power_zone_1,
            R.string.detail_power_zone_2,
            R.string.detail_power_zone_3,
            R.string.detail_power_zone_4,
            R.string.detail_power_zone_5,
            R.string.detail_power_zone_6,
            R.string.detail_power_zone_7,
        ),
    ),
}

/** Bars of [slices] with their share of [totalSeconds], names (as the training plans, D-94) and ranges. */
@Composable
private fun zoneBars(
    slices: ImmutableList<ZoneTimeSlice>,
    totalSeconds: Long,
    kind: ZoneKind,
    format: UnitFormatter,
): ImmutableList<ZoneBar> {
    val colors = RideCompTheme.colors
    val total = totalSeconds.coerceAtLeast(1)
    return slices.map { slice ->
        ZoneBar(
            name = stringResource(kind.names[(slice.zone - 1).coerceIn(0, kind.names.lastIndex)]),
            range = zoneRange(slice, kind),
            fraction = slice.seconds.toFloat() / total,
            percent = stringResource(
                R.string.detail_zone_percent,
                (slice.seconds * Units.PERCENT / total).roundToInt(),
            ),
            duration = format.duration(slice.seconds * Units.MS_PER_SECOND),
            color = colors.zones[(slice.zone - 1).coerceIn(0, colors.zones.lastIndex)],
        )
    }.toImmutableList()
}

@Composable
private fun zoneRange(slice: ZoneTimeSlice, kind: ZoneKind): String {
    val high = slice.high
    val isPower = kind == ZoneKind.POWER
    return when {
        high == null -> stringResource(
            if (isPower) R.string.detail_power_zone_range_above else R.string.detail_zone_range_above,
            slice.low,
        )
        slice.low == 0 && isPower -> stringResource(R.string.detail_power_zone_range_up_to, high)
        slice.low == 0 -> stringResource(R.string.detail_zone_range_below, high + 1)
        else -> stringResource(
            if (isPower) R.string.detail_power_zone_range else R.string.detail_zone_range,
            slice.low,
            high,
        )
    }
}

/**
 * Time in power zones Z1..Z7 (T11.6) and the FTP it is based on, with a link to the profile. With a power
 * meter it sits under the power numbers ([hasHeading]); without, it is the "Perkiraan zona power" card.
 */
@Composable
private fun PowerZonesBlock(
    zones: DetailPowerZones,
    format: UnitFormatter,
    hasHeading: Boolean,
    onOpenProfile: () -> Unit,
) {
    val colors = RideCompTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        if (hasHeading) {
            Text(
                text = stringResource(R.string.detail_power_zones_title),
                style = RideCompTheme.typography.bodyStrong,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = RideCompDimens.spacingS),
            )
        }
        ZoneBars(zones = zoneBars(zones.zones, zones.totalSeconds, ZoneKind.POWER, format))
        Text(
            text = ftpCaption(zones.ftp),
            style = RideCompTheme.typography.caption,
            color = colors.textSecondary,
        )
        LinkText(text = stringResource(R.string.detail_zones_edit_profile), onClick = onOpenProfile)
    }
}

@Composable
private fun ftpCaption(ftp: FtpEstimate): String = when (ftp.source) {
    FtpSource.PROFILE -> stringResource(R.string.detail_ftp_profile, ftp.ftpW)
    FtpSource.BEST_20_MIN -> stringResource(R.string.detail_ftp_best_20_min, ftp.ftpW)
    // Whole kilograms without decimals ("70"), else one ("68,5").
    FtpSource.WEIGHT -> stringResource(
        R.string.detail_ftp_weight,
        number(ftp.weightKg, if (ftp.weightKg % 1.0 == 0.0) 0 else 1),
        ftp.ftpW,
    )
}

/** Without a power meter: the zones of the estimated power, said so in the title and the ⓘ (T11.6). */
@Composable
private fun EstimatedPowerZonesCard(zones: DetailPowerZones, format: UnitFormatter, onOpenProfile: () -> Unit) {
    val title = stringResource(R.string.detail_estimated_power_zones_title)
    AnalysisCard(
        texts = AnalysisCardTexts(
            title = title,
            info = stringResource(R.string.detail_info_estimated_power_zones),
            infoLabel = stringResource(R.string.detail_section_info_label, title),
        ),
        rows = persistentListOf(),
        footer = { PowerZonesBlock(zones, format, hasHeading = false, onOpenProfile = onOpenProfile) },
    )
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
private const val ITEM_HEART_RATE_TIP = "heart_rate_tip"
private const val ITEM_ESTIMATED_POWER_ZONES = "estimated_power_zones"
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
                    leading = { IconTile(UiR.drawable.ic_trophy, tint = RideCompTheme.colors.warnText) },
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
    AnalysisSectionKind.POWER -> RideCompTheme.colors.warnText
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
    // Elevation names its numbers in full (T11.4): "Elevasi maks", not just "Maksimum".
    AnalysisStatKind.MAX -> if (section ==
        AnalysisSectionKind.ELEVATION
    ) {
        R.string.detail_stat_elevation_max
    } else {
        R.string.detail_stat_max
    }
    AnalysisStatKind.MIN -> if (section ==
        AnalysisSectionKind.ELEVATION
    ) {
        R.string.detail_stat_elevation_min
    } else {
        R.string.detail_stat_min
    }
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
