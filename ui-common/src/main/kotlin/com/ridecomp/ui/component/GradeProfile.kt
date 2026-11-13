package com.ridecomp.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Climb profile as one bar per equal-length section, coloured by grade band (12-FEATURE-SPECS §6.2).
 * Bands differ in brightness as well as hue, and each bar carries its grade number when it fits, so the
 * profile reads without colour vision. Canvas only, no chart library.
 */
@Composable
fun GradeProfile(
    title: String,
    gradesPercent: ImmutableList<Double>,
    /** Upper bounds of every band but the last, ascending (e.g. 3, 6, 9, 12). */
    bandUpperBoundsPercent: ImmutableList<Double>,
    summary: String,
    modifier: Modifier = Modifier,
    /** Position on the climb, 0..1; null for none. */
    markerFraction: Double? = null,
) {
    val colors = RideCompTheme.colors
    val labelStyle = RideCompTheme.typography.dataLabel.copy(color = colors.textPrimary)
    val measurer = rememberTextMeasurer()
    // Measured once per climb: the canvas redraws every second as the position marker moves.
    val labels = remember(gradesPercent, labelStyle, measurer) {
        gradesPercent.map { measurer.measure(it.roundToInt().toString(), labelStyle) }
    }
    val labelHeight = remember(labelStyle, measurer) {
        measurer.measure(SAMPLE_LABEL, labelStyle).size.height.toFloat()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = RideCompTheme.typography.dataLabel, color = colors.textSecondary)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RideCompDimens.chartHeight)
                .semantics { contentDescription = summary },
        ) {
            if (gradesPercent.isEmpty()) return@Canvas
            val maxGrade = gradesPercent.max().coerceAtLeast(MIN_SCALE_PERCENT)
            val barWidth = size.width / gradesPercent.size
            val plotHeight = size.height - labelHeight
            gradesPercent.forEachIndexed { index, grade ->
                val band = bandIndex(grade, bandUpperBoundsPercent)
                val height = (grade.coerceAtLeast(0.0) / maxGrade * plotHeight).toFloat()
                    .coerceAtLeast(RideCompDimens.chartStroke.toPx())
                val left = index * barWidth
                drawRect(
                    color = colors.gradeBands[band.coerceAtMost(colors.gradeBands.lastIndex)],
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth - RideCompDimens.chartStroke.toPx(), height),
                )
                val label = labels[index]
                if (label.size.width <= barWidth) {
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(
                            left + (barWidth - label.size.width) / 2,
                            size.height - height - label.size.height,
                        ),
                    )
                }
            }
            markerFraction?.let { fraction ->
                val x = (fraction.coerceIn(0.0, 1.0) * size.width).toFloat()
                // The ridden part is dimmed, the position is a dashed line with a dot (13-UI-SCREENS #8).
                drawRect(color = colors.bg.copy(alpha = PASSED_DIM_ALPHA), size = Size(x, size.height))
                val dash = RideCompDimens.chartStroke.toPx() * DASH_FACTOR
                drawLine(
                    color = colors.textPrimary,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = RideCompDimens.chartStroke.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                )
                drawCircle(
                    color = colors.textPrimary,
                    radius = RideCompDimens.markerDotRadius.toPx(),
                    center = Offset(x, size.height - RideCompDimens.markerDotRadius.toPx()),
                )
            }
        }
    }
}

/** Band of [grade]: the index of the first upper bound above it, or the last band. */
private fun bandIndex(grade: Double, bounds: List<Double>): Int =
    bounds.indexOfFirst { grade < it }.takeIf { it >= 0 } ?: bounds.size

/** Color of [gradePercent]'s band, e.g. for the current grade value (13-UI-SCREENS #8). */
@Composable
fun gradeBandColor(gradePercent: Double?, bandUpperBoundsPercent: List<Double>): Color? {
    val bands = RideCompTheme.colors.gradeBands
    return gradePercent?.let { bands[bandIndex(it, bandUpperBoundsPercent).coerceAtMost(bands.lastIndex)] }
}

private const val PASSED_DIM_ALPHA = 0.5f
private const val DASH_FACTOR = 2f

/** A flat climb still gets visible bars. */
private const val MIN_SCALE_PERCENT = 1.0
private const val SAMPLE_LABEL = "0"

@ThemePreviews
@Composable
private fun GradeProfilePreview() {
    RideCompPreview {
        GradeProfile(
            title = "CLIMB 2 OF 4",
            gradesPercent = persistentListOf(2.0, 4.5, 6.8, 9.2, 13.1, 7.4, 5.0),
            bandUpperBoundsPercent = persistentListOf(3.0, 6.0, 9.0, 12.0),
            summary = "Climb profile",
            markerFraction = 0.4,
        )
    }
}
