package com.ridecomp.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Line style of a [ChartSeries]; series differ by style too, not only color (accessibility). */
enum class LineStyle { SOLID, DASHED, DOTTED }

/** One line of a [TrendChart]: a value per x step (null = gap). */
@Immutable
data class ChartSeries(val label: String, val values: ImmutableList<Double?>, val color: Color, val style: LineStyle)

/**
 * Several lines over the same x steps with a legend (13-UI-SCREENS #20 fitness/fatigue/form), drawn with
 * Canvas — no chart library. All series share one y scale; a zero line is drawn when the range crosses zero.
 */
@Composable
fun TrendChart(series: ImmutableList<ChartSeries>, summary: String, modifier: Modifier = Modifier) {
    val zeroColor = RideCompTheme.colors.divider
    val range = remember(series) {
        series.flatMap { it.values }.filterNotNull().takeIf { it.isNotEmpty() }?.let { it.min()..it.max() }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.chartHeight)
                .semantics { contentDescription = summary },
        ) {
            val yRange = range ?: return@Canvas
            val height = (yRange.endInclusive - yRange.start).takeIf { it > 0 } ?: 1.0
            fun y(value: Double) = (size.height - (value - yRange.start) / height * size.height).toFloat()
            if (yRange.start < 0 && yRange.endInclusive > 0) {
                val zero = y(0.0)
                drawLine(zeroColor, Offset(0f, zero), Offset(size.width, zero), RideCompDimens.dividerThickness.toPx())
            }
            series.forEach { line ->
                val steps = (line.values.size - 1).coerceAtLeast(1)
                val path = Path()
                var isDrawing = false
                line.values.forEachIndexed { index, value ->
                    if (value == null) {
                        isDrawing = false
                    } else {
                        val x = index.toFloat() / steps * size.width
                        if (isDrawing) path.lineTo(x, y(value)) else path.moveTo(x, y(value))
                        isDrawing = true
                    }
                }
                val stroke = RideCompDimens.chartStroke.toPx()
                val effect = line.style.effect(stroke)
                drawPath(path, color = line.color, style = Stroke(width = stroke, pathEffect = effect))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM)) {
            series.forEach { line -> LegendItem(line) }
        }
    }
}

@Composable
private fun LegendItem(line: ChartSeries) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
    ) {
        Canvas(modifier = Modifier.width(RideCompDimens.iconSize).height(RideCompDimens.iconSize)) {
            val stroke = RideCompDimens.chartStroke.toPx()
            val middle = size.height / 2
            val effect = line.style.effect(stroke)
            drawLine(line.color, Offset(0f, middle), Offset(size.width, middle), stroke, pathEffect = effect)
        }
        Text(text = line.label, style = RideCompTheme.typography.caption, color = RideCompTheme.colors.textSecondary)
    }
}

private fun LineStyle.effect(strokePx: Float): PathEffect? = when (this) {
    LineStyle.SOLID -> null
    LineStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(strokePx * DASH, strokePx * DASH))
    LineStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(strokePx, strokePx * DOT_GAP))
}

private const val DASH = 3f
private const val DOT_GAP = 2f

@ThemePreviews
@Composable
private fun TrendChartPreview() {
    RideCompPreview {
        Box(modifier = Modifier.size(RideCompDimens.routeMapHeight * 2, RideCompDimens.routeMapHeight)) {
            TrendChart(
                series = persistentListOf(
                    ChartSeries("Fitness", persistentListOf(10.0, 15.0), RideCompTheme.colors.track, LineStyle.SOLID),
                    ChartSeries("Fatigue", persistentListOf(20.0, 25.0), RideCompTheme.colors.accent, LineStyle.DASHED),
                    ChartSeries("Form", persistentListOf(-5.0, -9.0), RideCompTheme.colors.divider, LineStyle.DOTTED),
                ),
                summary = "Trend",
            )
        }
    }
}
