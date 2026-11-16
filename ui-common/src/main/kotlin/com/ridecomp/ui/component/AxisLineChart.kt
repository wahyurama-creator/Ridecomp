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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Line chart with numbers (ride detail): max/middle/min on the left, start/middle/end below, a light fill
 * under the line, and touch or drag to read the value at any point (shown above the chart until the next
 * touch). Drawn with Canvas, no chart library (03-ARCHITECTURE §1); callers pass downsampled data.
 */
@Composable
fun AxisLineChart(
    values: ImmutableList<ChartValue>,
    formatX: (Double) -> String,
    formatY: (Double) -> String,
    hint: String,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = RideCompTheme.colors.accent,
    /** Left axis labels, usually the number without its unit; [formatY] (with unit) is for the readout. */
    formatYAxis: (Double) -> String = formatY,
) {
    val colors = RideCompTheme.colors
    val labelStyle = RideCompTheme.typography.navLabel.copy(color = colors.textSecondary)
    val measurer = rememberTextMeasurer()
    val bounds = remember(values) { values.bounds() }
    var inspected by remember(values) { mutableStateOf<ChartValue?>(null) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        Readout(inspected, formatX, formatY, hint, color)
        val box = bounds ?: return@Column
        val yLabels = remember(box, formatYAxis) {
            listOf(box.minY + box.height, box.minY + box.height / 2, box.minY).map(formatYAxis)
        }
        val xLabels = remember(box, formatX) {
            listOf(box.minX, box.minX + box.width / 2, box.minX + box.width).map(formatX)
        }
        val gapPx = with(LocalDensity.current) { RideCompDimens.chartLabelGap.toPx() }
        val gutterPx = remember(yLabels, labelStyle, gapPx) {
            yLabels.maxOf { measurer.measure(it, labelStyle).size.width } + gapPx
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.chartHeightLarge)
                .semantics { contentDescription = description }
                .pointerInput(values) {
                    detectTapGestures { inspected = values.nearest(box, plotFraction(it.x, gutterPx, size.width)) }
                }
                .pointerInput(values) {
                    detectHorizontalDragGestures { change, _ ->
                        inspected = values.nearest(box, plotFraction(change.position.x, gutterPx, size.width))
                    }
                },
        ) {
            val labelHeight = measurer.measure(xLabels.first(), labelStyle).size.height
            val plot = Plot(
                left = gutterPx,
                top = labelHeight / 2f,
                right = size.width,
                bottom = size.height - labelHeight - gapPx,
            )
            drawGrid(plot, yLabels, measurer, labelStyle, colors.divider)
            drawXLabels(plot, xLabels, measurer, labelStyle, gapPx)
            drawSeries(values, box, plot, color)
            inspected?.let { point -> drawMarker(point, box, plot, color, colors.textPrimary) }
        }
    }
}

/** "0,82 km · 28,4 km/h" while inspecting, otherwise the hint. */
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

/** The drawing area inside the axis labels. */
private data class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun x(value: Double, box: ChartBounds): Float = left + ((value - box.minX) / box.width * width).toFloat()

    fun y(value: Double, box: ChartBounds): Float = bottom - ((value - box.minY) / box.height * height).toFloat()
}

private fun DrawScope.drawGrid(
    plot: Plot,
    labels: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    lineColor: Color,
) {
    labels.forEachIndexed { index, label ->
        val y = plot.top + plot.height * index / (labels.size - 1)
        drawLine(lineColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = RideCompDimens.borderThin.toPx())
        val layout = measurer.measure(label, style)
        drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawXLabels(
    plot: Plot,
    labels: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    gap: Float,
) {
    labels.forEachIndexed { index, label ->
        val layout = measurer.measure(label, style)
        val center = plot.left + plot.width * index / (labels.size - 1)
        // Keep the first and last labels inside the chart.
        val left = (center - layout.size.width / 2f).coerceIn(plot.left, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(left, plot.bottom + gap))
    }
}

private fun DrawScope.drawSeries(values: List<ChartValue>, box: ChartBounds, plot: Plot, color: Color) {
    val line = Path()
    val fill = Path()
    var runStartX: Float? = null
    var lastX = 0f
    fun closeRun() {
        runStartX?.let { startX ->
            fill.lineTo(lastX, plot.bottom)
            fill.lineTo(startX, plot.bottom)
            fill.close()
        }
        runStartX = null
    }
    values.forEach { point ->
        val y = point.y
        if (y == null) {
            closeRun()
        } else {
            val x = plot.x(point.x, box)
            val py = plot.y(y, box)
            if (runStartX == null) {
                line.moveTo(x, py)
                fill.moveTo(x, py)
                runStartX = x
            } else {
                line.lineTo(x, py)
                fill.lineTo(x, py)
            }
            lastX = x
        }
    }
    closeRun()
    drawPath(fill, color = color.copy(alpha = FILL_ALPHA))
    drawPath(line, color = color, style = Stroke(width = RideCompDimens.chartStroke.toPx()))
}

private fun DrawScope.drawMarker(point: ChartValue, box: ChartBounds, plot: Plot, color: Color, lineColor: Color) {
    val x = plot.x(point.x, box)
    drawLine(lineColor, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = RideCompDimens.borderThin.toPx())
    point.y?.let { y -> drawCircle(color, RideCompDimens.markerDotRadius.toPx(), Offset(x, plot.y(y, box))) }
}

/** Where a touch falls along the plotted x range, 0..1 (left of the plot counts as its start). */
private fun plotFraction(touchX: Float, plotLeft: Float, width: Int): Double =
    ((touchX - plotLeft) / (width - plotLeft)).toDouble().coerceIn(0.0, 1.0)

/** The point closest to [fraction] of the x range (values are sorted by x). */
private fun List<ChartValue>.nearest(box: ChartBounds, fraction: Double): ChartValue? {
    if (isEmpty()) return null
    val target = box.minX + box.width * fraction
    val index = binarySearch { it.x.compareTo(target) }.let { if (it >= 0) it else (-it - 1).coerceAtMost(lastIndex) }
    val before = (index - 1).coerceAtLeast(0)
    return if (target - this[before].x < this[index].x - target) this[before] else this[index]
}

private const val FILL_ALPHA = 0.18f
private const val NO_VALUE = "--"

@ThemePreviews
@Composable
private fun AxisLineChartPreview() {
    RideCompPreview {
        AxisLineChart(
            values = (0..40).map { ChartValue(it * 0.1, 20.0 + it % 7) }.toImmutableList(),
            formatX = { String.format(Locale.ROOT, "%.1f km", it) },
            formatY = { String.format(Locale.ROOT, "%.0f", it) },
            hint = "Touch the chart to read a value",
            description = "Speed",
        )
    }
}
