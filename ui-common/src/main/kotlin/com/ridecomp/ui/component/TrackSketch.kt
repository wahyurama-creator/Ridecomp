package com.ridecomp.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ridecomp.ui.map.MapPoint
import com.ridecomp.ui.map.splitAtGaps
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlin.math.cos
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A ride's line without map tiles (segment editor, share card preview): north-up, fitted to the box, the
 * points in [highlight] drawn in accent. Callers pass a downsampled line (≤ ~1000 points).
 */
@Composable
fun TrackSketch(
    points: ImmutableList<MapPoint>,
    description: String,
    modifier: Modifier = Modifier,
    highlight: IntRange? = null,
    lineColor: Color = RideCompTheme.colors.textTertiary,
    /** Start (green) and finish (accent) dots: a whole ride, not a part of it. */
    hasEndpoints: Boolean = false,
    /** Lines drawn without GPS (index i = point i to i + 1), dashed (D-149). */
    gaps: ImmutableList<Int> = persistentListOf(),
) {
    val colors = RideCompTheme.colors
    val projection = remember(points) { Projection.of(points) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(RideCompDimens.routeMapHeight)
            .clip(RoundedCornerShape(RideCompDimens.cornerRadiusCard))
            .background(colors.surface)
            .padding(RideCompDimens.spacingM)
            .semantics { contentDescription = description },
    ) {
        val fit = projection ?: return@Canvas
        val stroke = Stroke(width = RideCompDimens.chartStroke.toPx() * STROKE_SCALE, cap = StrokeCap.Round)
        val parts = points.splitAtGaps(gaps)
        parts.withFix.forEach { part -> drawPath(path(part, part.indices, fit), color = lineColor, style = stroke) }
        val dashed = Stroke(
            width = stroke.width,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke.width * GAP_DASH, stroke.width * GAP_SPACE)),
        )
        parts.guessed.forEach { gap -> drawPath(path(gap, gap.indices, fit), color = lineColor, style = dashed) }
        highlight?.let { range -> drawPath(path(points, range, fit), color = colors.accent, style = stroke) }
        if (hasEndpoints) {
            val radius = RideCompDimens.markerDotRadius.toPx()
            drawCircle(colors.surface, radius * DOT_RING_SCALE, offset(points.first(), fit))
            drawCircle(colors.ok, radius, offset(points.first(), fit))
            drawCircle(colors.surface, radius * DOT_RING_SCALE, offset(points.last(), fit))
            drawCircle(colors.accent, radius, offset(points.last(), fit))
        }
    }
}

/** Equirectangular projection fitted to the line's box (fine at ride scale). */
private class Projection(
    val minX: Double,
    val minY: Double,
    val spanX: Double,
    val spanY: Double,
    val lonScale: Double,
) {
    companion object {
        fun of(points: List<MapPoint>): Projection? {
            if (points.size < 2) return null
            val lonScale = cos(Math.toRadians(points.first().latDeg))
            val xs = points.map { it.lonDeg * lonScale }
            val ys = points.map { it.latDeg }
            val spanX = (xs.max() - xs.min()).takeIf { it > 0 } ?: MIN_SPAN_DEG
            val spanY = (ys.max() - ys.min()).takeIf { it > 0 } ?: MIN_SPAN_DEG
            return Projection(xs.min(), ys.min(), spanX, spanY, lonScale)
        }
    }
}

private fun DrawScope.path(points: List<MapPoint>, range: IntRange, fit: Projection): Path {
    val path = Path()
    range.filter { it in points.indices }.forEachIndexed { n, i ->
        val point = offset(points[i], fit)
        if (n == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    return path
}

private fun DrawScope.offset(point: MapPoint, fit: Projection): Offset {
    val scale = minOf(size.width / fit.spanX, size.height / fit.spanY)
    val offsetX = (size.width - fit.spanX * scale) / 2
    val offsetY = (size.height - fit.spanY * scale) / 2
    return Offset(
        x = (offsetX + (point.lonDeg * fit.lonScale - fit.minX) * scale).toFloat(),
        y = (size.height - offsetY - (point.latDeg - fit.minY) * scale).toFloat(),
    )
}

private const val STROKE_SCALE = 1.5f

/** Dash and space of a line without GPS, in line widths (as on the map). */
private const val GAP_DASH = 1f
private const val GAP_SPACE = 1.5f

/** The surface ring around an end dot, so it stands out on the line. */
private const val DOT_RING_SCALE = 1.6f

/** A line along a meridian or parallel still needs a non-zero span to be fitted. */
private const val MIN_SPAN_DEG = 1e-9

@ThemePreviews
@Composable
private fun TrackSketchPreview() {
    RideCompPreview {
        TrackSketch(
            points = (0..40).map { MapPoint(-7.78 + it * 0.0004, 110.37 + (it % 10) * 0.0003) }.toImmutableList(),
            description = "Track",
            highlight = 10..25,
        )
    }
}
