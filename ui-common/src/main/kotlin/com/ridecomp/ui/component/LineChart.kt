package com.ridecomp.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One chart point; y = null leaves a gap (missing data). */
@Immutable
data class ChartValue(val x: Double, val y: Double?)

/**
 * Lightweight line chart drawn with Canvas — no chart library (03-ARCHITECTURE §1). Callers pass data
 * already downsampled (≤ ~1000 points, 02-DESIGN §6); the path is built once per data change.
 */
@Composable
fun LineChart(
    title: String,
    values: ImmutableList<ChartValue>,
    modifier: Modifier = Modifier,
    color: Color = RideCompTheme.colors.accent,
    summary: String? = null,
    /** Vertical marker at this x (current position on a route profile, T3.9); null for none. */
    markerX: Double? = null,
    /** Shaded x range (part chosen for a new segment, T4.2); null for none. */
    rangeX: ClosedFloatingPointRange<Double>? = null,
) {
    val markerColor = RideCompTheme.colors.textPrimary
    val rangeColor = RideCompTheme.colors.accentTint
    val bounds = remember(values) { values.bounds() }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = RideCompTheme.typography.dataLabel, color = RideCompTheme.colors.textSecondary)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.chartHeight)
                .semantics { contentDescription = summary ?: title },
        ) {
            val box = bounds ?: return@Canvas
            rangeX?.let { range ->
                val from = ((range.start - box.minX) / box.width * size.width).toFloat().coerceIn(0f, size.width)
                val to = ((range.endInclusive - box.minX) / box.width * size.width).toFloat().coerceIn(0f, size.width)
                drawRect(color = rangeColor, topLeft = Offset(from, 0f), size = Size(to - from, size.height))
            }
            val path = Path()
            var isDrawing = false
            values.forEach { point ->
                val y = point.y
                if (y == null) {
                    isDrawing = false
                } else {
                    val offset = Offset(
                        x = ((point.x - box.minX) / box.width * size.width).toFloat(),
                        y = (size.height - (y - box.minY) / box.height * size.height).toFloat(),
                    )
                    if (isDrawing) path.lineTo(offset.x, offset.y) else path.moveTo(offset.x, offset.y)
                    isDrawing = true
                }
            }
            drawPath(path, color = color, style = Stroke(width = RideCompDimens.chartStroke.toPx()))
            markerX?.let { x ->
                val markerOffset = ((x - box.minX) / box.width * size.width).toFloat().coerceIn(0f, size.width)
                drawLine(
                    color = markerColor,
                    start = Offset(markerOffset, 0f),
                    end = Offset(markerOffset, size.height),
                    strokeWidth = RideCompDimens.chartStroke.toPx(),
                )
            }
        }
    }
}

internal data class ChartBounds(val minX: Double, val width: Double, val minY: Double, val height: Double)

internal fun List<ChartValue>.bounds(): ChartBounds? {
    val ys = mapNotNull { it.y }
    if (size < 2 || ys.isEmpty()) return null
    val minX = first().x
    val maxX = last().x
    val minY = ys.min()
    val maxY = ys.max()
    // A flat line still needs a non-zero range to be drawn in the middle.
    val heightRange = (maxY - minY).takeIf { it > 0 } ?: 1.0
    val widthRange = (maxX - minX).takeIf { it > 0 } ?: 1.0
    return ChartBounds(minX, widthRange, minY, heightRange)
}

@ThemePreviews
@Composable
private fun LineChartPreview() {
    RideCompPreview {
        LineChart(
            title = "SPEED",
            values = persistentListOf(
                ChartValue(0.0, 20.0),
                ChartValue(1.0, 28.0),
                ChartValue(2.0, null),
                ChartValue(3.0, 25.0),
                ChartValue(4.0, 31.0),
            ),
        )
    }
}
