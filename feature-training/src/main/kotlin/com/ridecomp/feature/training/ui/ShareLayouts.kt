package com.ridecomp.feature.training.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.engine.share.ShareTemplate
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.viewmodel.ShareCardUiState
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.map.RouteSnapshot
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.ShareCardColors
import com.ridecomp.ui.theme.ThemeMode
import com.ridecomp.ui.theme.WindowWidthClass
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** The two map images of the map cards, rendered once per screen (T10.6); null while loading. */
@Immutable
data class ShareMaps(val full: RouteSnapshot? = null, val framed: RouteSnapshot? = null)

/** Pixel size of the framed map of [ShareTemplate.MAP_FRAMED] (4:5). */
const val FRAMED_MAP_WIDTH_PX: Int = 864
const val FRAMED_MAP_HEIGHT_PX: Int = 1_080

/** The layouts after the owner's references (T10.6); the four earlier styles are drawn by [ShareCard]. */
internal fun ShareTemplate.isReferenceLayout(): Boolean = when (this) {
    ShareTemplate.CLASSIC, ShareTemplate.POSTER, ShareTemplate.PHOTO, ShareTemplate.STICKER -> false
    ShareTemplate.ROUTE_SIDE_STATS, ShareTemplate.SIDE_STATS, ShareTemplate.DOTTED_LIST,
    ShareTemplate.ROUTE_DOTTED_LIST, ShareTemplate.CENTER_STACK, ShareTemplate.ROUTE_UNDER_STACK,
    ShareTemplate.PILLS, ShareTemplate.STAT_ROW, ShareTemplate.STAT_GRID, ShareTemplate.ROUTE_BESIDE_STATS,
    ShareTemplate.ROUTE_OVER_ROW, ShareTemplate.BIG_STATS, ShareTemplate.ROUTE_LOGO,
    ShareTemplate.STATS_SMALL_ROUTE, ShareTemplate.DATE_TICKET, ShareTemplate.MAP, ShareTemplate.MAP_FRAMED,
    -> true
}

/** Whether the card can be captured now: a map card waits for its map. */
internal fun ShareMaps.isReady(template: ShareTemplate): Boolean = when (template) {
    ShareTemplate.MAP -> full != null
    ShareTemplate.MAP_FRAMED -> framed != null
    else -> true
}

/** One statistic as printed. */
private data class CardStat(val label: String, val value: String)

/** What every layout draws with. */
private class CardParts(
    val state: ShareCardUiState,
    val stats: List<CardStat>,
    val ink: CardInk,
    val unit: Dp,
    val maps: ShareMaps,
) {
    val firstThree: List<CardStat> get() = stats.take(ShareCardConfig.ROW_STATS)
}

/**
 * A card of the reference gallery, on the same 360-unit grid as [ShareCard] so the tile, the preview and the
 * 1080 px image match. White type with a soft shadow, the route in accent and the RideComp mark.
 */
@Composable
internal fun ReferenceCard(state: ShareCardUiState, maps: ShareMaps, modifier: Modifier = Modifier) {
    val totals = state.data?.activity?.totals ?: return
    val formatter = remember(state.unitSystem) { UnitFormatter(state.unitSystem) }
    val stats = state.stats.map { CardStat(stringResource(it.labelRes()), it.value(totals, formatter)) }
    RideCompTheme(themeMode = ThemeMode.DARK, widthClass = WindowWidthClass.REGULAR) {
        val ink = stickerInk(RideCompTheme.colors.accent)
        BoxWithConstraints(modifier = modifier) {
            val parts = CardParts(state, stats, ink, maxWidth / CARD_UNITS, maps)
            parts.Layout()
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod") // One branch per card style, exhaustive so no style is missed.
private fun CardParts.Layout() {
    when (state.template) {
        ShareTemplate.ROUTE_SIDE_STATS -> SideStats(withRoute = true)
        ShareTemplate.SIDE_STATS -> SideStats(withRoute = false)
        ShareTemplate.DOTTED_LIST -> DottedList(withRoute = false)
        ShareTemplate.ROUTE_DOTTED_LIST -> DottedList(withRoute = true)
        ShareTemplate.CENTER_STACK -> CenterStack()
        ShareTemplate.ROUTE_UNDER_STACK -> Box(modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS)) {
            Route(Modifier.fillMaxSize(), strokeUnits = WIDE_STROKE_UNITS)
            CenterStack()
        }
        ShareTemplate.PILLS -> Pills()
        ShareTemplate.STAT_ROW -> StatRows(rows = listOf(firstThree))
        ShareTemplate.STAT_GRID -> StatRows(rows = stats.chunked(ShareCardConfig.ROW_STATS))
        ShareTemplate.ROUTE_BESIDE_STATS -> RouteBesideStats()
        ShareTemplate.ROUTE_OVER_ROW -> RouteOverRow()
        ShareTemplate.BIG_STATS -> BigStats()
        ShareTemplate.ROUTE_LOGO -> RouteLogo()
        ShareTemplate.STATS_SMALL_ROUTE -> StatsSmallRoute()
        ShareTemplate.DATE_TICKET -> DateTicket()
        ShareTemplate.MAP -> MapCard()
        ShareTemplate.MAP_FRAMED -> FramedMapCard()
        ShareTemplate.CLASSIC, ShareTemplate.POSTER, ShareTemplate.PHOTO, ShareTemplate.STICKER -> Unit
    }
}

// ----- Layouts (one per reference) -----

/** Route (optional) on the left, the statistics written down the right edge. */
@Composable
private fun CardParts.SideStats(withRoute: Boolean) {
    Column(modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS)) {
        Wordmark()
        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = unit * GAP_UNITS * 2)) {
            if (withRoute) {
                Route(Modifier.weight(1f).fillMaxHeight().padding(end = unit * GAP_UNITS * 2))
            } else {
                Spacer(Modifier.weight(1f))
            }
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.End,
            ) {
                stats.forEach { stat ->
                    Row(modifier = Modifier.vertical(), verticalAlignment = Alignment.Bottom) {
                        CardText(stat.label, unit * SMALL_LABEL_UNITS, ink.soft, ink)
                        Spacer(Modifier.width(unit * GAP_UNITS))
                        CardText(stat.value, unit * SIDE_VALUE_UNITS, ink.text, ink, weight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Logo and a dotted list at the bottom; with the route above it. */
@Composable
private fun CardParts.DottedList(withRoute: Boolean) {
    Column(modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS), verticalArrangement = Arrangement.Bottom) {
        if (withRoute) Route(Modifier.fillMaxWidth().weight(1f).padding(bottom = unit * GAP_UNITS * 2))
        Wordmark(modifier = Modifier.padding(bottom = unit * GAP_UNITS))
        stats.forEach { stat -> DottedRow(stat) }
    }
}

/** Logo and the statistics stacked in the middle. */
@Composable
private fun CardParts.CenterStack() {
    Column(
        modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS),
        verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Wordmark()
        stats.forEach { stat -> StatBlock(stat, Alignment.CenterHorizontally) }
    }
}

/** Logo, date, ride icon and the statistics as outlined pills at the bottom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardParts.Pills() {
    val date = rememberDates(state).full.uppercase()
    Box(modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS), contentAlignment = Alignment.BottomStart) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(unit * PILL_GAP_UNITS),
            verticalArrangement = Arrangement.spacedBy(unit * PILL_GAP_UNITS),
        ) {
            Pill { Wordmark(sizeUnits = PILL_UNITS) }
            Pill { CardText(date, unit * PILL_UNITS, ink.text, ink, weight = FontWeight.Bold) }
            Pill { RideIcon(sizeUnits = PILL_UNITS * ICON_SCALE, color = ink.text) }
            stats.forEach { stat ->
                Pill {
                    CardText(
                        "${stat.label} ${stat.value}".uppercase(),
                        unit * PILL_UNITS,
                        ink.text,
                        ink,
                        weight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Ride icon and logo, then rows of three statistics, in the middle. */
@Composable
private fun CardParts.StatRows(rows: List<List<CardStat>>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS),
        verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RideIcon(sizeUnits = LOGO_UNITS * ICON_SCALE, color = ink.text)
            Spacer(Modifier.width(unit * GAP_UNITS))
            Wordmark()
        }
        rows.forEach { row -> StatsRow(row) }
    }
}

/** Large route on the left, logo and three statistics on the right. */
@Composable
private fun CardParts.RouteBesideStats() {
    Row(
        modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Route(Modifier.weight(BESIDE_ROUTE_WEIGHT).aspectRatio(1f))
        Column(
            modifier = Modifier.weight(1f).padding(start = unit * GAP_UNITS),
            verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS),
            horizontalAlignment = Alignment.End,
        ) {
            Wordmark()
            firstThree.forEach { stat -> StatBlock(stat, Alignment.End) }
        }
    }
}

/** Route on top, then logo, three statistics in a row and the ride icon. */
@Composable
private fun CardParts.RouteOverRow() {
    Column(
        modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS),
    ) {
        Route(Modifier.fillMaxWidth().weight(1f))
        Wordmark(sizeUnits = BIG_LOGO_UNITS)
        StatsRow(firstThree, Alignment.CenterHorizontally)
        RideIcon(sizeUnits = LOGO_UNITS * ICON_SCALE, color = ink.text)
    }
}

/** Three statistics in very large type. */
@Composable
private fun CardParts.BigStats() {
    Column(
        modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS),
        verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS * 2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        firstThree.forEach { stat ->
            StatBlock(stat, Alignment.CenterHorizontally, labelUnits = BIG_LABEL_UNITS, valueUnits = BIG_VALUE_UNITS)
        }
    }
}

/** The route and the logo only. */
@Composable
private fun CardParts.RouteLogo() {
    Column(
        modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS * 2),
    ) {
        Route(Modifier.fillMaxWidth().weight(1f), strokeUnits = WIDE_STROKE_UNITS)
        Wordmark(sizeUnits = BIG_LOGO_UNITS)
    }
}

/** Three statistics, a small route and the logo, centred. */
@Composable
private fun CardParts.StatsSmallRoute() {
    Column(
        modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS),
        verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        firstThree.forEach { stat -> StatBlock(stat, Alignment.CenterHorizontally, valueUnits = LARGE_VALUE_UNITS) }
        Route(Modifier.size(unit * SMALL_ROUTE_UNITS))
        Wordmark(sizeUnits = BIG_LOGO_UNITS)
    }
}

/** A ticket with a torn bottom edge: logo, ride icon, weekday and date. */
@Composable
private fun CardParts.DateTicket() {
    val dates = rememberDates(state)
    Box(modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(unit * TICKET_WIDTH_UNITS)
                .drawBehind { drawTicket(ink.text, (unit * TICKET_STROKE_UNITS).toPx(), (unit * TEETH_UNITS).toPx()) }
                .padding(
                    start = unit * GAP_UNITS * 2,
                    end = unit * GAP_UNITS * 2,
                    top = unit * GAP_UNITS * 2,
                    bottom = unit * (GAP_UNITS * 2 + TEETH_UNITS),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS),
        ) {
            Wordmark(sizeUnits = PILL_UNITS * ICON_SCALE)
            RideIcon(sizeUnits = TICKET_ICON_UNITS, color = ink.accent)
            CardText(dates.weekday, unit * TICKET_DAY_UNITS, ink.text, ink, weight = FontWeight.Bold)
            CardText(dates.dayMonth, unit * SMALL_LABEL_UNITS, ink.soft, ink)
        }
    }
}

/** The route on a light map, the ride name and three statistics on a dark fade at the bottom. */
@Composable
private fun CardParts.MapCard() {
    Box(modifier = Modifier.fillMaxSize()) {
        MapImage(maps.full, Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(mapFade()),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(unit * PADDING_UNITS),
            verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RideIcon(sizeUnits = LOGO_UNITS, color = ink.text)
                Spacer(Modifier.width(unit * GAP_UNITS))
                CardText(
                    state.data?.activity?.name.orEmpty(),
                    unit * NAME_UNITS,
                    ink.text,
                    ink,
                    weight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Wordmark(sizeUnits = PILL_UNITS * ICON_SCALE)
            }
            StatsRow(firstThree)
            Attribution()
        }
    }
}

/** A dark card with two accent bands, a framed map, the ride name and "recorded with RideComp". */
@Composable
private fun CardParts.FramedMapCard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShareCardColors.NightBottom)
            .drawBehind { drawBands(ink.accent, (unit * BAND_UNITS).toPx()) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(unit * PADDING_UNITS),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(unit * GAP_UNITS, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(
                        FRAMED_MAP_WIDTH_PX.toFloat() / FRAMED_MAP_HEIGHT_PX,
                        matchHeightConstraintsFirst = true,
                    )
                    .clip(RoundedCornerShape(unit * FRAME_RADIUS_UNITS)),
            ) {
                MapImage(maps.framed, Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(mapFade()),
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(unit * GAP_UNITS * 2)) {
                    StatsRow(firstThree)
                    Attribution()
                }
            }
            CardText(
                state.data?.activity?.name.orEmpty(),
                unit * NAME_UNITS,
                ink.text,
                ink,
                weight = FontWeight.Bold,
                modifier = Modifier.padding(top = unit * GAP_UNITS),
            )
            CardText(stringResource(R.string.share_recorded_with), unit * SMALL_LABEL_UNITS, ink.soft, ink)
            Wordmark(sizeUnits = BIG_LOGO_UNITS)
        }
    }
}

// ----- Pieces -----

@Composable
private fun CardParts.Wordmark(modifier: Modifier = Modifier, sizeUnits: Float = LOGO_UNITS) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(UiR.drawable.ic_ridecomp_mark),
            contentDescription = null,
            modifier = Modifier.size(unit * sizeUnits * ICON_SCALE),
        )
        Spacer(Modifier.width(unit * sizeUnits * MARK_GAP_SHARE))
        CardText(stringResource(R.string.share_footer), unit * sizeUnits, ink.text, ink, weight = FontWeight.Black)
    }
}

@Composable
private fun CardParts.RideIcon(sizeUnits: Float, color: Color) {
    Icon(
        painter = painterResource(UiR.drawable.ic_nav_ride),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(unit * sizeUnits),
    )
}

@Composable
private fun CardParts.StatBlock(
    stat: CardStat,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    labelUnits: Float = SMALL_LABEL_UNITS,
    valueUnits: Float = VALUE_UNITS,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        CardText(stat.label, unit * labelUnits, ink.soft, ink)
        CardText(stat.value, unit * valueUnits, ink.text, ink, weight = FontWeight.Bold)
    }
}

/** Up to three statistics side by side. */
@Composable
private fun CardParts.StatsRow(row: List<CardStat>, alignment: Alignment.Horizontal = Alignment.Start) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(unit * GAP_UNITS, alignment),
    ) {
        row.forEach { stat ->
            val weight = if (alignment == Alignment.Start) Modifier.weight(1f) else Modifier
            StatBlock(stat, if (alignment == Alignment.Start) Alignment.Start else Alignment.CenterHorizontally, weight)
        }
    }
}

/** "Distance ········ 10.99 km". */
@Composable
private fun CardParts.DottedRow(stat: CardStat) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        CardText(stat.label, unit * LIST_UNITS, ink.text, ink)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(unit * LIST_UNITS)
                .padding(horizontal = unit * GAP_UNITS / 2),
        ) {
            val y = size.height * DOTS_BASELINE_SHARE
            drawLine(
                color = ink.soft,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = (unit * DOT_UNITS).toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, (unit * DOT_SPACING_UNITS).toPx())),
            )
        }
        CardText(stat.value, unit * LIST_UNITS, ink.text, ink, weight = FontWeight.Bold)
    }
}

@Composable
private fun CardParts.Pill(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .border(unit * PILL_BORDER_UNITS, ink.text, RoundedCornerShape(percent = HALF))
            .padding(horizontal = unit * PILL_PADDING_H_UNITS, vertical = unit * PILL_PADDING_V_UNITS),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The route shape fitted in [modifier]'s box, keeping its proportions (no coordinates, §11). */
@Composable
private fun CardParts.Route(modifier: Modifier, strokeUnits: Float = STROKE_UNITS) {
    val line = state.line
    Canvas(modifier = modifier) {
        if (line.size < 2) return@Canvas
        val side = minOf(size.width, size.height)
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val path = Path()
        line.forEachIndexed { i, p ->
            if (i ==
                0
            ) {
                path.moveTo(left + p.x * side, top + p.y * side)
            } else {
                path.lineTo(left + p.x * side, top + p.y * side)
            }
        }
        drawLine(path, ink.accent, (unit * strokeUnits).toPx())
    }
}

/** The map image with the route drawn where it lies; a dark box with "loading" until the map is ready. */
@Composable
private fun CardParts.MapImage(snapshot: RouteSnapshot?, modifier: Modifier) {
    if (snapshot == null) {
        Box(modifier = modifier.background(ShareCardColors.NightTop), contentAlignment = Alignment.Center) {
            CardText(stringResource(R.string.share_map_loading), unit * SMALL_LABEL_UNITS, ink.soft, ink)
        }
        return
    }
    Box(modifier = modifier) {
        Image(
            bitmap = snapshot.image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path()
            snapshot.line.forEachIndexed { i, p ->
                if (i ==
                    0
                ) {
                    path.moveTo(p.x * size.width, p.y * size.height)
                } else {
                    path.lineTo(
                        p.x * size.width,
                        p.y * size.height,
                    )
                }
            }
            drawLine(path, ink.accent, (unit * STROKE_UNITS).toPx())
        }
    }
}

/** Map data credit, required on any image of the map (OpenStreetMap / OpenFreeMap licences). */
@Composable
private fun CardParts.Attribution() {
    val credit = if (state.isMapOnline) UiR.string.map_attribution_online else UiR.string.map_attribution
    CardText(stringResource(credit), unit * CREDIT_UNITS, ink.soft, ink)
}

private fun DrawScope.drawLine(path: Path, color: Color, width: Float) {
    drawPath(path, color = color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Outline of a ticket whose bottom edge is a row of teeth. */
private fun DrawScope.drawTicket(color: Color, stroke: Float, tooth: Float) {
    val bottom = size.height - tooth
    val teeth = (size.width / (tooth * 2)).toInt().coerceAtLeast(1)
    val step = size.width / teeth
    val path = Path().apply {
        moveTo(0f, bottom)
        lineTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, bottom)
        for (i in teeth downTo 1) {
            lineTo(size.width - (teeth - i) * step - step / 2, size.height)
            lineTo(size.width - (teeth - i + 1) * step, bottom)
        }
    }
    drawPath(path, color = color, style = Stroke(width = stroke, join = StrokeJoin.Miter))
}

/** Two thick accent bands across opposite corners. */
private fun DrawScope.drawBands(color: Color, width: Float) {
    val stroke = Stroke(width = width, cap = StrokeCap.Butt)
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * BAND_START, -width)
            lineTo(w + width, h * BAND_END)
        },
        color,
        style = stroke,
    )
    drawPath(
        Path().apply {
            moveTo(-width, h * (1 - BAND_END))
            lineTo(w * (1 - BAND_START), h + width)
        },
        color,
        style = stroke,
    )
}

/** Clear at the top, dark at the bottom of a map. */
private fun mapFade(): Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    FADE_START to Color.Transparent,
    1f to ShareCardColors.PhotoScrim.copy(alpha = FADE_ALPHA),
)

/** Lays the content out turned a quarter clockwise (text reading top to bottom along the edge). */
private fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = QUARTER_TURN }
    }
}

/** The ride's date as the cards print it, in the app's language. */
private class CardDates(val full: String, val weekday: String, val dayMonth: String)

@Composable
private fun rememberDates(state: ShareCardUiState): CardDates {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val startedAtMs = state.data?.activity?.startedAtMs ?: 0L
    return remember(startedAtMs, locale) {
        val date = Instant.ofEpochMilli(startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        CardDates(
            full = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
            weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                .replaceFirstChar { it.titlecase(locale) },
            dayMonth = date.format(DateTimeFormatter.ofPattern(DAY_MONTH_PATTERN, locale)),
        )
    }
}

@StringRes
@Suppress("CyclomaticComplexMethod") // One name per card style, exhaustive on purpose.
internal fun ShareTemplate.labelRes(): Int = when (this) {
    ShareTemplate.CLASSIC -> R.string.share_template_classic
    ShareTemplate.POSTER -> R.string.share_template_poster
    ShareTemplate.PHOTO -> R.string.share_template_photo
    ShareTemplate.STICKER -> R.string.share_template_sticker
    ShareTemplate.ROUTE_SIDE_STATS -> R.string.share_template_route_side
    ShareTemplate.SIDE_STATS -> R.string.share_template_side
    ShareTemplate.DOTTED_LIST -> R.string.share_template_list
    ShareTemplate.ROUTE_DOTTED_LIST -> R.string.share_template_route_list
    ShareTemplate.CENTER_STACK -> R.string.share_template_stack
    ShareTemplate.ROUTE_UNDER_STACK -> R.string.share_template_route_stack
    ShareTemplate.PILLS -> R.string.share_template_pills
    ShareTemplate.STAT_ROW -> R.string.share_template_row
    ShareTemplate.STAT_GRID -> R.string.share_template_grid
    ShareTemplate.ROUTE_BESIDE_STATS -> R.string.share_template_route_beside
    ShareTemplate.ROUTE_OVER_ROW -> R.string.share_template_route_row
    ShareTemplate.BIG_STATS -> R.string.share_template_big
    ShareTemplate.ROUTE_LOGO -> R.string.share_template_route_only
    ShareTemplate.STATS_SMALL_ROUTE -> R.string.share_template_small_route
    ShareTemplate.DATE_TICKET -> R.string.share_template_ticket
    ShareTemplate.MAP -> R.string.share_template_map
    ShareTemplate.MAP_FRAMED -> R.string.share_template_map_framed
}

// Sizes in card units (360 across, as ShareCard).
private const val PADDING_UNITS = 24f
private const val GAP_UNITS = 6f
private const val LOGO_UNITS = 16f
private const val BIG_LOGO_UNITS = 22f
private const val ICON_SCALE = 1.25f
private const val MARK_GAP_SHARE = 0.3f
private const val SMALL_LABEL_UNITS = 10f
private const val VALUE_UNITS = 18f
private const val LARGE_VALUE_UNITS = 26f
private const val BIG_LABEL_UNITS = 14f
private const val BIG_VALUE_UNITS = 38f
private const val SIDE_VALUE_UNITS = 13f
private const val LIST_UNITS = 11f
private const val NAME_UNITS = 18f
private const val CREDIT_UNITS = 6f
private const val STROKE_UNITS = 5f
private const val WIDE_STROKE_UNITS = 8f
private const val SMALL_ROUTE_UNITS = 96f
private const val BESIDE_ROUTE_WEIGHT = 1.3f
private const val PILL_UNITS = 9f
private const val PILL_GAP_UNITS = 5f
private const val PILL_BORDER_UNITS = 1.2f
private const val PILL_PADDING_H_UNITS = 8f
private const val PILL_PADDING_V_UNITS = 5f
private const val DOT_UNITS = 1.4f
private const val DOT_SPACING_UNITS = 4f
private const val DOTS_BASELINE_SHARE = 0.8f
private const val TICKET_WIDTH_UNITS = 210f
private const val TICKET_STROKE_UNITS = 1.4f
private const val TEETH_UNITS = 6f
private const val TICKET_ICON_UNITS = 30f
private const val TICKET_DAY_UNITS = 40f
private const val FRAME_RADIUS_UNITS = 14f
private const val BAND_UNITS = 34f
private const val BAND_START = 0.55f
private const val BAND_END = 0.22f
private const val QUARTER_TURN = 90f
private const val HALF = 50
private const val DAY_MONTH_PATTERN = "d MMM"

/** Dark fade over the lower part of a map so white text stays readable. */
private const val FADE_START = 0.55f
private const val FADE_ALPHA = 0.85f
