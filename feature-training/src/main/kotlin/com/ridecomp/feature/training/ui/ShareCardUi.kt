package com.ridecomp.feature.training.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.share.ShareFormat
import com.ridecomp.core.engine.share.ShareStat
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.viewmodel.ShareCardUiEvent
import com.ridecomp.feature.training.viewmodel.ShareCardUiState
import com.ridecomp.feature.training.viewmodel.ShareCardViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.EmptyState
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.ThemeMode
import java.text.DateFormat
import java.util.Date
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Share card editor (T4.5, 13-UI-SCREENS #21). */
@Composable
fun ShareCardRoute(
    activityId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareCardViewModel = koinViewModel(key = "share-$activityId") { parametersOf(activityId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShareCardScreen(state, viewModel::onEvent, onBack, modifier)
}

@Composable
fun ShareCardScreen(
    state: ShareCardUiState,
    onEvent: (ShareCardUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.share_title),
        onBack = onBack,
        backLabel = stringResource(R.string.segment_back),
    ) {
        when {
            state.isMissing -> EmptyState(message = stringResource(R.string.share_missing))
            state.data != null -> Editor(state, onEvent)
        }
    }
    if (state.isSharing && state.data != null) CardCapture(state, onEvent)
}

@Composable
private fun Editor(state: ShareCardUiState, onEvent: (ShareCardUiEvent) -> Unit) {
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
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShareCard(
                state = state,
                // A thin frame shows where the card ends on a screen of the same color.
                modifier = Modifier
                    .heightIn(max = RideCompDimens.shareCardPreviewHeight)
                    .aspectRatio(state.format.widthPx.toFloat() / state.format.heightPx)
                    .border(RideCompDimens.borderThin, RideCompTheme.colors.divider),
            )
        }
        ChoiceChips(
            label = stringResource(R.string.share_format),
            choices = persistentListOf(
                Choice(ShareFormat.STORY, stringResource(R.string.share_format_story)),
                Choice(ShareFormat.SQUARE, stringResource(R.string.share_format_square)),
            ),
            selected = state.format,
            onSelect = { onEvent(ShareCardUiEvent.SetFormat(it)) },
            horizontalPadding = RideCompDimens.none,
            columns = FORMAT_COLUMNS,
        )
        ListCard {
            SwitchRow(
                label = stringResource(R.string.share_hide_ends),
                description = stringResource(R.string.share_hide_ends_hint),
                isChecked = state.isEndsHidden,
                onCheckedChange = { onEvent(ShareCardUiEvent.SetEndsHidden(it)) },
            )
            SwitchRow(
                label = stringResource(R.string.share_show_track),
                isChecked = state.isTrackShown,
                onCheckedChange = { onEvent(ShareCardUiEvent.SetTrackShown(it)) },
            )
            SwitchRow(
                label = stringResource(R.string.share_dark),
                isChecked = state.isDark,
                onCheckedChange = { onEvent(ShareCardUiEvent.SetDark(it)) },
            )
        }
        SectionHeader(stringResource(R.string.share_stats), horizontalPadding = RideCompDimens.none)
        ListCard {
            state.availableStats.forEach { stat ->
                SwitchRow(
                    label = stringResource(stat.labelRes()),
                    isChecked = stat in state.stats,
                    onCheckedChange = { onEvent(ShareCardUiEvent.ToggleStat(stat)) },
                )
            }
        }
        BigButton(
            text = stringResource(R.string.share_send),
            onClick = { onEvent(ShareCardUiEvent.Rendering) },
            isEnabled = !state.isSharing,
        )
    }
}

/**
 * Draws the card once at its real pixel size (1080 × 1920 or 1080 × 1080) into a graphics layer that is not
 * shown; the ViewModel encodes it as PNG off the main thread (§11: rendered offline).
 */
@Composable
private fun CardCapture(state: ShareCardUiState, onEvent: (ShareCardUiEvent) -> Unit) {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val width = with(density) { state.format.widthPx.toDp() }
    val height = with(density) { state.format.heightPx.toDp() }
    Box(modifier = Modifier.size(0.dp).clipToBounds()) {
        ShareCard(
            state = state,
            modifier = Modifier
                .requiredSize(width, height)
                .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
        )
    }
    LaunchedEffect(state.format, state.isDark, state.stats, state.line, state.isTrackShown) {
        // Two frames: the card is laid out and its layer recorded before reading it.
        withFrameNanos { }
        withFrameNanos { }
        onEvent(ShareCardUiEvent.Captured(layer.toImageBitmap()))
    }
}

/**
 * The card itself, laid out in proportion to its width so the preview and the 1080 px image look the same:
 * date, ride name, the line (no map tiles, no coordinates), up to 4 statistics, records and the RideComp name.
 */
@Composable
fun ShareCard(state: ShareCardUiState, modifier: Modifier = Modifier) {
    val data = state.data ?: return
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    RideCompTheme(themeMode = if (state.isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
        val colors = RideCompTheme.colors
        BoxWithConstraints(modifier = modifier.background(colors.bg)) {
            val unit = maxWidth / CARD_UNITS
            val density = LocalDensity.current
            fun Dp.sp() = with(density) { toSp() }
            Column(modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS)) {
                Text(
                    text = DateFormat.getDateInstance(DateFormat.FULL).format(Date(data.activity.startedAtMs)),
                    fontSize = (unit * DATE_UNITS).sp(),
                    color = colors.textSecondary,
                )
                Text(
                    text = data.activity.name,
                    fontSize = (unit * NAME_UNITS).sp(),
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = unit * PADDING_UNITS)) {
                    if (state.isTrackShown && state.line.size >= 2) {
                        val accent = colors.accent
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val side = minOf(size.width, size.height)
                            val left = (size.width - side) / 2
                            val top = (size.height - side) / 2
                            val path = Path()
                            state.line.forEachIndexed { i, p ->
                                val x = left + p.x * side
                                val y = top + p.y * side
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            val stroke = Stroke(
                                width = (unit * LINE_UNITS).toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            )
                            drawPath(path, color = accent, style = stroke)
                        }
                    }
                }
                state.stats.chunked(STAT_COLUMNS).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = unit * PADDING_UNITS / 2)) {
                        row.forEach { stat ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(stat.labelRes()).uppercase(),
                                    fontSize = (unit * LABEL_UNITS).sp(),
                                    color = colors.textSecondary,
                                )
                                Text(
                                    text = stat.value(data.activity.totals, formatter),
                                    fontSize = (unit * VALUE_UNITS).sp(),
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                        repeat(STAT_COLUMNS - row.size) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
                Text(
                    text = if (data.newRecords > 0) {
                        pluralStringResource(R.plurals.share_footer_records, data.newRecords, data.newRecords)
                    } else {
                        stringResource(R.string.share_footer)
                    },
                    fontSize = (unit * LABEL_UNITS).sp(),
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                )
            }
        }
    }
}

@StringRes
private fun ShareStat.labelRes(): Int = when (this) {
    ShareStat.DISTANCE -> R.string.share_stat_distance
    ShareStat.TIME -> R.string.share_stat_time
    ShareStat.ASCENT -> R.string.share_stat_ascent
    ShareStat.AVG_SPEED -> R.string.share_stat_speed
    ShareStat.AVG_HEART_RATE -> R.string.share_stat_heart_rate
    ShareStat.AVG_POWER -> R.string.share_stat_power
}

private fun ShareStat.value(totals: RideTotals, formatter: UnitFormatter): String = when (this) {
    ShareStat.DISTANCE -> "${formatter.distance(totals.distanceM)} ${formatter.distanceUnit}"
    ShareStat.TIME -> formatter.duration(totals.movingS * Units.MS_PER_SECOND)
    ShareStat.ASCENT -> "${formatter.elevation(totals.ascentM)} ${formatter.elevationUnit}"
    ShareStat.AVG_SPEED -> "${formatter.speed(totals.avgSpeedMps)} ${formatter.speedUnit}"
    ShareStat.AVG_HEART_RATE -> "${formatter.integer(totals.avgHeartRateBpm)} $BPM"
    ShareStat.AVG_POWER -> "${formatter.integer(totals.avgPowerW)} $WATT"
}

/** The card is laid out on a grid of this many units across (sizes below are in units). */
private const val CARD_UNITS = 360f
private const val PADDING_UNITS = 24f
private const val DATE_UNITS = 13f
private const val NAME_UNITS = 26f
private const val LINE_UNITS = 5f
private const val LABEL_UNITS = 11f
private const val VALUE_UNITS = 28f
private const val STAT_COLUMNS = 2
private const val FORMAT_COLUMNS = 2
private const val BPM = "bpm"
private const val WATT = "W"
