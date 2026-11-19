package com.ridecomp.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import java.util.Locale
import kotlin.math.sin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Labels and readout of a [RideChart]: units are shown once, at the axes. */
@Immutable
data class RideChartLabels(
    /** Unit of the vertical axis, printed above it ("km/h", "m", "bpm"). */
    val yUnit: String,
    /** Unit of the distance axis ("km", "mi"), printed after each distance label. */
    val xUnit: String,
    /** Hint shown above the chart until it is touched. */
    val hint: String,
    /** Spoken summary for accessibility ("Speed, average 20.9 km/h, max 50.4"). */
    val description: String,
)

/**
 * Ride chart of the activity detail (T11.1, 14-PHASE11-PLAN; layout after the owner's references): an area
 * over distance with **round axis numbers** (1-2-5 steps, [ChartScale]), light grid lines, the units at the
 * axes, an optional grey [background] series on its own scale (elevation behind speed), an optional dashed
 * [averageY] line, and touch or drag to read a value. Gaps (null) are drawn as breaks, never as zero.
 *
 * Canvas only, no chart library; callers pass reduced data (≤ 300 points, `LoadRideChartUseCase`). Colours
 * come from the theme tokens, so the same chart works in the dark and the light theme.
 */
@Composable
@Suppress("LongParameterList") // Data, labels, two formatters and the optional layers of one chart.
fun RideChart(
    values: ImmutableList<ChartValue>,
    labels: RideChartLabels,
    formatX: (Double) -> String,
    formatY: (Double) -> String,
    modifier: Modifier = Modifier,
    color: Color = RideCompTheme.colors.accent,
    yScale: ChartYScale = ChartYScale.FROM_ZERO,
    background: ImmutableList<ChartValue>? = null,
    averageY: Double? = null,
) {
    val colors = RideCompTheme.colors
    val locale: Locale = LocalConfiguration.current.locales[0]
    val labelStyle = RideCompTheme.typography.navLabel.copy(color = colors.textSecondary)
    val measurer = rememberTextMeasurer()
    val layout = remember(values, yScale, locale) { values.layoutOf(yScale, locale) }
    val backgroundRange = remember(background) { background?.yRange() }
    var inspected by remember(values) { mutableStateOf<ChartValue?>(null) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        Readout(inspected, formatX, formatY, labels.hint, color)
        val chart = layout ?: return@Column
        val gapPx = with(LocalDensity.current) { RideCompDimens.chartLabelGap.toPx() }
        val yLayouts = remember(chart, labelStyle) { chart.yLabels.map { measurer.measure(it, labelStyle) } }
        val unitLayout = remember(labels.yUnit, labelStyle) { measurer.measure(labels.yUnit, labelStyle) }
        val xLayouts = remember(chart, labels.xUnit, labelStyle) {
            chart.xTicks.indices.map { measurer.measure(chart.xLabel(it, labels.xUnit), labelStyle) }
        }
        // Same left edge on every tab: room for at least a 4-digit label, whatever this chart's numbers are.
        val minGutter = remember(labelStyle) { measurer.measure(GUTTER_SAMPLE, labelStyle).size.width }
        val gutterPx = maxOf(yLayouts.maxOfOrNull { it.size.width } ?: 0, unitLayout.size.width, minGutter) + gapPx
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.chartHeightLarge)
                .semantics { contentDescription = labels.description }
                .pointerInput(values) {
                    detectTapGestures { inspected = values.nearest(chart, plotFraction(it.x, gutterPx, size.width)) }
                }
                .pointerInput(values) {
                    detectHorizontalDragGestures { change, _ ->
                        inspected = values.nearest(chart, plotFraction(change.position.x, gutterPx, size.width))
                    }
                },
        ) {
            val textHeight = (xLayouts.firstOrNull() ?: unitLayout).size.height.toFloat()
            val plot = Plot(
                left = gutterPx,
                top = unitLayout.size.height + gapPx,
                right = size.width,
                bottom = size.height - textHeight - gapPx,
            )
            drawText(unitLayout, topLeft = Offset(0f, 0f))
            drawGrid(plot, chart, yLayouts, colors.divider)
            drawXLabels(plot, chart, xLayouts, gapPx, colors.divider)
            if (background != null && backgroundRange != null) {
                val shade = colors.textTertiary.copy(alpha = BACKGROUND_ALPHA)
                drawArea(background, plot, chart.minX, chart.width, backgroundRange, shade, null)
            }
            // Over a background the main area is more opaque, so it reads first (as in the reference).
            val fillAlpha = if (background != null) FILL_OVER_BACKGROUND_ALPHA else FILL_ALPHA
            val range = chart.axis.min..chart.axis.max
            drawArea(values, plot, chart.minX, chart.width, range, color.copy(alpha = fillAlpha), color)
            averageY?.let { drawAverage(plot, chart, it, colors.textPrimary) }
            inspected?.let { drawMarker(it, plot, chart, color, colors.textPrimary) }
        }
    }
}

/** "2,4 km · 28,4 km/h" while inspecting, otherwise the hint. */
@Composable
private fun Readout(
    inspected: ChartValue?,
    formatX: (Double) -> String,
    formatY: (Double) -> String,
    hint: String,
    color: Color,
) {
    val colors = RideCompTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        if (inspected == null) {
            Text(text = hint, style = RideCompTheme.typography.caption, color = colors.textTertiary)
        } else {
            Text(text = formatX(inspected.x), style = RideCompTheme.typography.caption, color = colors.textSecondary)
            Text(
                text = inspected.y?.let(formatY) ?: NO_VALUE,
                style = RideCompTheme.typography.bodyStrong,
                color = color,
            )
        }
    }
}

/** Everything a chart needs to draw, computed once per data change (not per frame). */
@Immutable
private class ChartLayout(
    val minX: Double,
    val width: Double,
    val axis: ChartAxis,
    /** Tick numbers of the vertical axis, top to bottom, as drawn. */
    val yLabels: List<String>,
    val xTicks: List<Double>,
    /** Tick numbers of the distance axis, without the unit. */
    private val xNumbers: List<String>,
) {
    fun xLabel(index: Int, unit: String): String = "${xNumbers[index]} $unit"
}

private fun List<ChartValue>.layoutOf(scale: ChartYScale, locale: Locale): ChartLayout? {
    val range = yRange()
    val minX = firstOrNull()?.x ?: 0.0
    val width = (lastOrNull()?.x ?: 0.0) - minX
    if (range == null || size < 2 || width <= 0) return null
    val axis = ChartScale.yAxis(range.start, range.endInclusive, scale)
    val xTicks = ChartScale.xTicks(minX, minX + width)
    val xDecimals = ChartScale.decimalsFor(if (xTicks.size >= 2) xTicks[1] - xTicks[0] else width)
    return ChartLayout(
        minX = minX,
        width = width,
        axis = axis,
        yLabels = axis.ticks.reversed().map { format(it, axis.decimals, locale) },
        xTicks = xTicks,
        xNumbers = xTicks.map { format(it, xDecimals, locale) },
    )
}

private fun List<ChartValue>.yRange(): ClosedFloatingPointRange<Double>? {
    val ys = mapNotNull { it.y }.filter { it.isFinite() }
    return if (ys.isEmpty()) null else ys.min()..ys.max()
}

/** The drawing area inside the axis labels. */
private data class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun x(value: Double, minX: Double, rangeX: Double): Float = left + ((value - minX) / rangeX * width).toFloat()

    fun y(value: Double, range: ClosedFloatingPointRange<Double>): Float {
        val span = (range.endInclusive - range.start).takeIf { it > 0 } ?: 1.0
        return bottom - ((value - range.start) / span * height).toFloat()
    }
}

/** Horizontal grid lines at the round ticks, their numbers on the left. */
private fun DrawScope.drawGrid(plot: Plot, chart: ChartLayout, labels: List<TextLayoutResult>, lineColor: Color) {
    val ticks = chart.axis.ticks.reversed()
    val range = chart.axis.min..chart.axis.max
    ticks.forEachIndexed { index, tick ->
        val y = plot.y(tick, range)
        drawLine(lineColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = RideCompDimens.borderThin.toPx())
        val label = labels[index]
        drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
    }
}

/** Round distance labels ("2 km · 4 km …") with a thin vertical grid line at each. */
private fun DrawScope.drawXLabels(
    plot: Plot,
    chart: ChartLayout,
    labels: List<TextLayoutResult>,
    gap: Float,
    lineColor: Color,
) {
    chart.xTicks.forEachIndexed { index, tick ->
        val center = plot.x(tick, chart.minX, chart.width)
        if (center > plot.right + 1f) return@forEachIndexed
        drawLine(
            lineColor,
            Offset(center, plot.top),
            Offset(center, plot.bottom),
            strokeWidth = RideCompDimens.borderThin.toPx(),
        )
        val label = labels[index]
        // Keep the last label inside the chart.
        val left = (center - label.size.width / 2f).coerceIn(plot.left, size.width - label.size.width)
        drawText(label, topLeft = Offset(left, plot.bottom + gap))
    }
}

/** A filled area for each run of values (gaps break it); [line] adds the outline on top. */
@Suppress("LongParameterList") // One draw call: data, frame, x range, y range and the two colours.
private fun DrawScope.drawArea(
    values: List<ChartValue>,
    plot: Plot,
    minX: Double,
    rangeX: Double,
    range: ClosedFloatingPointRange<Double>,
    fill: Color,
    line: Color?,
) {
    val outline = Path()
    val area = Path()
    var runStartX: Float? = null
    var lastX = 0f
    fun closeRun() {
        runStartX?.let { startX ->
            area.lineTo(lastX, plot.bottom)
            area.lineTo(startX, plot.bottom)
            area.close()
        }
        runStartX = null
    }
    values.forEach { point ->
        val y = point.y?.takeIf { it.isFinite() }
        if (y == null) {
            closeRun()
        } else {
            val x = plot.x(point.x, minX, rangeX)
            val py = plot.y(y.coerceIn(range.start, range.endInclusive), range)
            if (runStartX == null) {
                outline.moveTo(x, py)
                area.moveTo(x, py)
                runStartX = x
            } else {
                outline.lineTo(x, py)
                area.lineTo(x, py)
            }
            lastX = x
        }
    }
    closeRun()
    drawPath(area, color = fill)
    line?.let { drawPath(outline, color = it, style = Stroke(width = RideCompDimens.chartStroke.toPx())) }
}

/** Dashed line at the average value. */
private fun DrawScope.drawAverage(plot: Plot, chart: ChartLayout, average: Double, color: Color) {
    val y = plot.y(average.coerceIn(chart.axis.min, chart.axis.max), chart.axis.min..chart.axis.max)
    val dash = RideCompDimens.chartStroke.toPx() * DASH_SCALE
    drawLine(
        color = color.copy(alpha = AVERAGE_ALPHA),
        start = Offset(plot.left, y),
        end = Offset(plot.right, y),
        strokeWidth = RideCompDimens.borderThin.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
    )
}

private fun DrawScope.drawMarker(point: ChartValue, plot: Plot, chart: ChartLayout, color: Color, lineColor: Color) {
    val x = plot.x(point.x, chart.minX, chart.width)
    drawLine(lineColor, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = RideCompDimens.borderThin.toPx())
    point.y?.let { y ->
        drawCircle(color, RideCompDimens.markerDotRadius.toPx(), Offset(x, plot.y(y, chart.axis.min..chart.axis.max)))
    }
}

/** Where a touch falls along the plotted x range, 0..1 (left of the plot counts as its start). */
private fun plotFraction(touchX: Float, plotLeft: Float, width: Int): Double =
    ((touchX - plotLeft) / (width - plotLeft)).toDouble().coerceIn(0.0, 1.0)

/** The point closest to [fraction] of the x range (values are sorted by x). */
private fun List<ChartValue>.nearest(chart: ChartLayout, fraction: Double): ChartValue? {
    if (isEmpty()) return null
    val target = chart.minX + chart.width * fraction
    val index = binarySearch { it.x.compareTo(target) }.let { if (it >= 0) it else (-it - 1).coerceAtMost(lastIndex) }
    val before = (index - 1).coerceAtLeast(0)
    return if (target - this[before].x < this[index].x - target) this[before] else this[index]
}

private fun format(value: Double, decimals: Int, locale: Locale): String =
    String.format(locale, "%.${decimals}f", value)

private const val FILL_ALPHA = 0.28f
private const val FILL_OVER_BACKGROUND_ALPHA = 0.55f
private const val BACKGROUND_ALPHA = 0.16f

/** Widest axis label every chart leaves room for, so the plot does not shift between tabs. */
private const val GUTTER_SAMPLE = "8888"
private const val AVERAGE_ALPHA = 0.6f
private const val DASH_SCALE = 2f
private const val NO_VALUE = "--"

@ThemePreviews
@Composable
private fun RideChartPreview() {
    RideCompPreview {
        val distances = (0..120).map { it * 0.09 }
        RideChart(
            values = distances.map { ChartValue(it, 21.0 + 9 * sin(it * 1.7) + if (it > 7.5) 8 else 0) }
                .toImmutableList(),
            background = distances.map { ChartValue(it, 9.0 + 3 * sin(it * 0.6)) }.toImmutableList(),
            labels = RideChartLabels(
                yUnit = "km/h",
                xUnit = "km",
                hint = "Touch the chart to read a value",
                description = "Speed",
            ),
            formatX = { String.format(Locale.ROOT, "%.1f km", it) },
            formatY = { String.format(Locale.ROOT, "%.1f km/h", it) },
            averageY = 24.0,
        )
    }
}
