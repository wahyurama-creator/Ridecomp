package com.ridecomp.feature.training.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.engine.share.ShareDetail
import com.ridecomp.core.engine.share.SharePalette
import com.ridecomp.core.engine.share.ShareStat
import com.ridecomp.core.engine.share.ShareTemplate
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.viewmodel.ShareCardUiState
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.ShareCardColors
import com.ridecomp.ui.theme.ThemeMode
import com.ridecomp.ui.theme.WindowWidthClass
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToLong

/** Colours one template draws with (text on a gradient or photo is white; classic follows its theme). */
internal data class CardInk(
    val text: Color,
    val soft: Color,
    val accent: Color,
    val line: Color,
    val hasShadow: Boolean,
)

/**
 * A share card (T4.5/T4.5b), laid out on a grid of [CARD_UNITS] across its width so the preview and the
 * 1080 px image look the same. No map tiles and no coordinates: only the route's shape (§11 privacy).
 */
@Composable
fun ShareCard(state: ShareCardUiState, modifier: Modifier = Modifier, maps: ShareMaps = ShareMaps()) {
    state.data ?: return
    if (state.template.isReferenceLayout()) {
        ReferenceCard(state, maps, modifier)
        return
    }
    RideCompTheme(
        themeMode = if (state.isDark || state.template != ShareTemplate.CLASSIC) ThemeMode.DARK else ThemeMode.LIGHT,
        // The exported image must be the same on every phone (16-PHASE13-PLAN T13.6).
        widthClass = WindowWidthClass.REGULAR,
    ) {
        val colors = RideCompTheme.colors
        val ink = when (state.template) {
            ShareTemplate.CLASSIC -> CardInk(
                colors.textPrimary,
                colors.textSecondary,
                colors.accent,
                colors.accent,
                false,
            )
            ShareTemplate.POSTER, ShareTemplate.PHOTO -> CardInk(
                ShareCardColors.PosterText,
                ShareCardColors.PosterSoftText,
                colors.accent,
                ShareCardColors.PosterText,
                false,
            )
            else -> stickerInk(colors.accent)
        }
        BoxWithConstraints(modifier = modifier.cardBackground(state, colors.bg)) {
            val unit = maxWidth / CARD_UNITS
            if (state.template == ShareTemplate.PHOTO) PhotoLayer(state)
            CardContent(state, ink, unit)
        }
    }
}

/** White text with a soft shadow and an accent line: readable on any photo (transparent cards). */
internal fun stickerInk(accent: Color) =
    CardInk(ShareCardColors.PosterText, ShareCardColors.PosterSoftText, accent, ShareCardColors.PosterText, true)

private fun Modifier.cardBackground(state: ShareCardUiState, themeBackground: Color): Modifier = when {
    state.isTransparent -> this
    state.template == ShareTemplate.POSTER -> background(Brush.verticalGradient(state.palette.colors()))
    state.template == ShareTemplate.PHOTO -> background(ShareCardColors.NightBottom)
    else -> background(themeBackground)
}

private fun SharePalette.colors(): List<Color> = when (this) {
    SharePalette.SUNSET -> listOf(ShareCardColors.SunsetTop, ShareCardColors.SunsetBottom)
    SharePalette.OCEAN -> listOf(ShareCardColors.OceanTop, ShareCardColors.OceanBottom)
    SharePalette.FOREST -> listOf(ShareCardColors.ForestTop, ShareCardColors.ForestBottom)
    SharePalette.NIGHT -> listOf(ShareCardColors.NightTop, ShareCardColors.NightBottom)
}

/** The rider's photo, cropped to the card, with a dark fade at the bottom so the numbers stay readable. */
@Composable
private fun PhotoLayer(state: ShareCardUiState) {
    val photo = state.photo ?: return
    Image(
        bitmap = photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Transparent, ShareCardColors.PhotoScrim))),
    )
}

@Composable
private fun CardContent(state: ShareCardUiState, ink: CardInk, unit: Dp) {
    val data = state.data ?: return
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    val isBig = state.template != ShareTemplate.CLASSIC
    Column(modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS)) {
        CardText(
            DateFormat.getDateInstance(DateFormat.FULL).format(Date(data.activity.startedAtMs)),
            unit * DATE_UNITS,
            ink.soft,
            ink,
        )
        CardText(data.activity.name, unit * NAME_UNITS, ink.text, ink, weight = FontWeight.Bold, maxLines = 2)
        if (isBig) HeroDistance(data.activity.totals, formatter, ink, unit)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = unit * PADDING_UNITS / 2)) {
            if (state.isTrackShown && state.line.size >= 2) RouteLine(state, ink, unit, isGlowing = isBig)
        }
        DetailsBlock(state, ink, unit, formatter)
        StatsGrid(state, ink, unit, formatter, skipDistance = isBig)
        Footer(data.details.records, data.details.segmentRecords, state, ink, unit)
    }
}

/** Poster, photo and sticker: the distance in very large type. */
@Composable
private fun HeroDistance(totals: RideTotals, formatter: UnitFormatter, ink: CardInk, unit: Dp) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = unit * PADDING_UNITS / 2)) {
        CardText(formatter.distance(totals.distanceM), unit * HERO_UNITS, ink.text, ink, weight = FontWeight.Black)
        CardText(
            " " + formatter.distanceUnit,
            unit * UNIT_UNITS,
            ink.soft,
            ink,
            weight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = unit * HERO_BASELINE_UNITS),
        )
    }
}

/** The route shape centred in its box; on the big templates a soft glow is drawn under the line. */
@Composable
private fun RouteLine(state: ShareCardUiState, ink: CardInk, unit: Dp, isGlowing: Boolean) {
    val line = state.line
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = minOf(size.width, size.height)
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val path = Path()
        line.forEachIndexed { i, p ->
            val x = left + p.x * side
            val y = top + p.y * side
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val width = (unit * LINE_UNITS).toPx()
        if (isGlowing) {
            GLOW_LAYERS.forEach { (scale, alpha) ->
                drawRoute(path, ink.accent.copy(alpha = alpha), width * scale)
            }
        }
        drawRoute(path, if (isGlowing) ink.line else ink.accent, width)
        if (isGlowing) {
            drawCircle(
                ink.accent,
                radius = width * DOT_SCALE,
                center = Offset(
                    left + line.last().x * side,
                    top + line.last().y * side,
                ),
            )
        }
    }
}

private fun DrawScope.drawRoute(path: Path, color: Color, width: Float) {
    drawPath(path, color = color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Elevation strip, heart-rate zones and kilometre splits, as chosen in the editor. */
@Composable
private fun ColumnScope.DetailsBlock(state: ShareCardUiState, ink: CardInk, unit: Dp, formatter: UnitFormatter) {
    val details = state.data?.details ?: return
    val chosen = state.details
    if (ShareDetail.ELEVATION in chosen && details.elevationM.isNotEmpty()) {
        DetailLabel(R.string.share_detail_elevation, ink, unit)
        ElevationStrip(details.elevationM, ink, unit)
    }
    if (ShareDetail.SPLITS in chosen && details.splitsS.isNotEmpty()) {
        val fastest = details.splitsS.min()
        DetailLabel(
            R.string.share_detail_splits,
            ink,
            unit,
            formatter.duration((fastest * Units.MS_PER_SECOND).roundToLong()),
        )
        SplitBars(details.splitsS, ink, unit)
    }
    if (ShareDetail.HEART_RATE_ZONES in chosen && details.zonesS.isNotEmpty()) {
        DetailLabel(R.string.share_detail_zones, ink, unit)
        ZoneBar(details.zonesS, unit)
    }
}

@Composable
private fun DetailLabel(@StringRes label: Int, ink: CardInk, unit: Dp, value: String? = null) {
    val text = stringResource(label).uppercase() + (value?.let { " · $it" } ?: "")
    CardText(text, unit * LABEL_UNITS, ink.soft, ink, modifier = Modifier.padding(top = unit * GAP_UNITS))
}

/** Filled elevation profile, scaled to its own range. */
@Composable
private fun ElevationStrip(values: List<Double?>, ink: CardInk, unit: Dp) {
    Canvas(modifier = Modifier.fillMaxWidth().height(unit * STRIP_UNITS)) {
        val known = values.filterNotNull()
        val min = known.min()
        val range = (known.max() - min).takeIf { it > 0 } ?: 1.0
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path().apply { moveTo(0f, size.height) }
        values.forEachIndexed { i, v ->
            val y = size.height - ((v ?: min) - min).toFloat() / range.toFloat() * size.height
            path.lineTo(i * step, y)
        }
        path.lineTo(size.width, size.height)
        path.close()
        drawPath(path, color = ink.accent.copy(alpha = FILL_ALPHA))
    }
}

/** One bar per kilometre; faster kilometres are taller, the fastest is drawn in accent. */
@Composable
private fun SplitBars(splits: List<Double>, ink: CardInk, unit: Dp) {
    Canvas(modifier = Modifier.fillMaxWidth().height(unit * STRIP_UNITS)) {
        val fastest = splits.min()
        val gap = size.width * BAR_GAP_SHARE / splits.size
        val width = (size.width - gap * (splits.size - 1)) / splits.size
        splits.forEachIndexed { i, s ->
            val height = (fastest / s).toFloat() * size.height
            drawRect(
                color = if (s == fastest) ink.accent else ink.soft.copy(alpha = BAR_ALPHA),
                topLeft = Offset(i * (width + gap), size.height - height),
                size = androidx.compose.ui.geometry.Size(width, height),
            )
        }
    }
}

/** Time in zones Z1..Z5 as one stacked bar in the grade band colours. */
@Composable
private fun ZoneBar(zonesS: List<Long>, unit: Dp) {
    val bands = RideCompTheme.colors.gradeBands
    val total = zonesS.sum().coerceAtLeast(1)
    Row(modifier = Modifier.fillMaxWidth().height(unit * ZONE_UNITS).clip(RoundedCornerShape(unit * ZONE_UNITS))) {
        zonesS.forEachIndexed { i, s ->
            if (s >
                0
            ) {
                Box(
                    modifier = Modifier.weight(s.toFloat() / total).fillMaxSize().background(
                        bands.getOrElse(i) {
                            bands.last()
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(
    state: ShareCardUiState,
    ink: CardInk,
    unit: Dp,
    formatter: UnitFormatter,
    skipDistance: Boolean,
) {
    val totals = state.data?.activity?.totals ?: return
    val stats = state.stats.take(ShareCardConfig.GRID_STATS).filterNot { skipDistance && it == ShareStat.DISTANCE }
    stats.chunked(STAT_COLUMNS).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth().padding(top = unit * GAP_UNITS)) {
            row.forEach { stat ->
                Column(modifier = Modifier.weight(1f)) {
                    CardText(stringResource(stat.labelRes()).uppercase(), unit * LABEL_UNITS, ink.soft, ink)
                    CardText(stat.value(totals, formatter), unit * VALUE_UNITS, ink.text, ink, weight = FontWeight.Bold)
                }
            }
            repeat(STAT_COLUMNS - row.size) { Box(modifier = Modifier.weight(1f)) }
        }
    }
}

/** "2 PR BARU · 1 SEGMEN · RideComp", or just the name. */
@Composable
private fun Footer(records: Int, segmentRecords: Int, state: ShareCardUiState, ink: CardInk, unit: Dp) {
    val showsBadges = ShareDetail.BADGES in state.details
    val parts = buildList {
        if (showsBadges && records > 0) add(pluralStringResource(R.plurals.share_badge_records, records, records))
        if (showsBadges &&
            segmentRecords > 0
        ) {
            add(pluralStringResource(R.plurals.share_badge_segments, segmentRecords, segmentRecords))
        }
        add(stringResource(R.string.share_footer))
    }
    CardText(
        parts.joinToString(SEPARATOR),
        unit * LABEL_UNITS,
        // On a gradient or photo white reads better than the accent.
        if (state.template == ShareTemplate.CLASSIC) ink.accent else ink.text,
        ink,
        weight = FontWeight.Bold,
        modifier = Modifier.padding(top = unit * GAP_UNITS),
    )
}

@Composable
internal fun CardText(
    text: String,
    size: Dp,
    color: Color,
    ink: CardInk,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    val density = LocalDensity.current
    val fontSize: TextUnit = with(density) { size.toSp() }
    val shadow = if (ink.hasShadow) {
        Shadow(
            ShareCardColors.TextShadow,
            offset = Offset.Zero,
            blurRadius =
            with(density) { size.toPx() } * SHADOW_SHARE,
        )
    } else {
        null
    }
    Text(
        text = text,
        style = TextStyle(fontSize = fontSize, fontWeight = weight, color = color, shadow = shadow),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@StringRes
internal fun ShareStat.labelRes(): Int = when (this) {
    ShareStat.DISTANCE -> R.string.share_stat_distance
    ShareStat.TIME -> R.string.share_stat_time
    ShareStat.ASCENT -> R.string.share_stat_ascent
    ShareStat.AVG_SPEED -> R.string.share_stat_speed
    ShareStat.AVG_HEART_RATE -> R.string.share_stat_heart_rate
    ShareStat.AVG_POWER -> R.string.share_stat_power
    ShareStat.CALORIES -> R.string.share_stat_calories
}

internal fun ShareStat.value(totals: RideTotals, formatter: UnitFormatter): String = when (this) {
    ShareStat.DISTANCE -> "${formatter.distance(totals.distanceM)} ${formatter.distanceUnit}"
    ShareStat.TIME -> formatter.duration(totals.movingS * Units.MS_PER_SECOND)
    ShareStat.ASCENT -> "${formatter.elevation(totals.ascentM)} ${formatter.elevationUnit}"
    ShareStat.AVG_SPEED -> "${formatter.speed(totals.avgSpeedMps)} ${formatter.speedUnit}"
    ShareStat.AVG_HEART_RATE -> "${formatter.integer(totals.avgHeartRateBpm)} $BPM"
    ShareStat.AVG_POWER -> "${formatter.integer(totals.avgPowerW)} $WATT"
    ShareStat.CALORIES -> "${formatter.integer(totals.calories)} $KCAL"
}

/** The card is laid out on a grid of this many units across (sizes below are in units). */
internal const val CARD_UNITS = 360f
private const val PADDING_UNITS = 24f
private const val DATE_UNITS = 13f
private const val NAME_UNITS = 24f
private const val HERO_UNITS = 72f
private const val UNIT_UNITS = 22f
private const val HERO_BASELINE_UNITS = 14f
private const val LINE_UNITS = 5f
private const val LABEL_UNITS = 11f
private const val VALUE_UNITS = 26f
private const val GAP_UNITS = 8f
private const val STRIP_UNITS = 34f
private const val ZONE_UNITS = 8f
private const val STAT_COLUMNS = 2
private const val FILL_ALPHA = 0.55f
private const val BAR_ALPHA = 0.5f
private const val BAR_GAP_SHARE = 0.25f
private const val DOT_SCALE = 1.4f
private const val SHADOW_SHARE = 0.35f

/** Glow under the route on the big templates: (stroke width multiple, alpha). */
private val GLOW_LAYERS = listOf(4f to 0.12f, 2.5f to 0.25f)
private const val SEPARATOR = " · "
private const val BPM = "bpm"
private const val WATT = "W"
private const val KCAL = "kcal"
