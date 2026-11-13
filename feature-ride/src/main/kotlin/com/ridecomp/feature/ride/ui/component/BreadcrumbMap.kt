package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.feature.ride.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlin.math.cos
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Visible half-width of the breadcrumb view around the rider (follow mode, north-up). */
private const val VIEW_HALF_WIDTH_M = 500.0
private const val METERS_PER_DEGREE = 111_320.0
private const val DEGREES_TO_RADIANS = Math.PI / 180.0
private const val POSITION_RADIUS_FACTOR = 3f

/**
 * Map-less navigation (T3.8, N-07): the route on a dark background, the part already ridden in a second
 * colour, the rider as a dot; north is up and the view follows the rider. Drawn with Canvas only, so it
 * works on very weak phones and without any map data. Before the first fix the whole route is shown.
 */
@Composable
fun BreadcrumbMap(
    line: ImmutableList<RoutePoint>,
    distanceAlongM: Double?,
    latDeg: Double?,
    lonDeg: Double?,
    modifier: Modifier = Modifier,
) {
    val colors = RideCompTheme.colors
    val description = stringResource(R.string.breadcrumb_description)
    Box(modifier = modifier.background(colors.bg)) {
        Canvas(modifier = Modifier.matchParentSize().semantics { contentDescription = description }) {
            if (line.size < 2) return@Canvas
            val centerLat = latDeg ?: line.map { it.latDeg }.average()
            val centerLon = lonDeg ?: line.map { it.lonDeg }.average()
            val scaleX = METERS_PER_DEGREE * cos(centerLat * DEGREES_TO_RADIANS)
            val halfWidthM = if (latDeg != null) VIEW_HALF_WIDTH_M else line.extentM(centerLat, centerLon, scaleX)
            val pixelsPerMeter = (minOf(size.width, size.height) / 2f) / halfWidthM.toFloat()
            val toOffset = { lat: Double, lon: Double ->
                Offset(
                    x = size.width / 2f + ((lon - centerLon) * scaleX).toFloat() * pixelsPerMeter,
                    y = size.height / 2f - ((lat - centerLat) * METERS_PER_DEGREE).toFloat() * pixelsPerMeter,
                )
            }
            val stroke = Stroke(width = RideCompDimens.chartStroke.toPx() * 2, cap = StrokeCap.Round)
            val (ridden, ahead) = line.splitPaths(distanceAlongM, toOffset)
            drawPath(ahead, color = colors.accent, style = stroke)
            drawPath(ridden, color = colors.textSecondary, style = stroke)
            if (latDeg != null && lonDeg != null) {
                drawCircle(
                    colors.danger,
                    radius = stroke.width * POSITION_RADIUS_FACTOR,
                    center = toOffset(latDeg, lonDeg),
                )
            }
        }
        Text(
            text = stringResource(R.string.breadcrumb_north),
            style = RideCompTheme.typography.dataLabel,
            color = colors.textSecondary,
            modifier = Modifier.align(Alignment.TopStart).padding(RideCompDimens.spacingS),
        )
    }
}

/** Ridden part and the part ahead as two paths; the part ahead starts where the ridden one ends (no gap). */
private fun List<RoutePoint>.splitPaths(
    distanceAlongM: Double?,
    toOffset: (Double, Double) -> Offset,
): Pair<Path, Path> {
    val ridden = Path()
    val ahead = Path()
    var hasRidden = false
    var hasAhead = false
    var previous: Offset? = null
    forEach { point ->
        val offset = toOffset(point.latDeg, point.lonDeg)
        val isRidden = distanceAlongM != null && point.cumDistanceM <= distanceAlongM
        when {
            isRidden && hasRidden -> ridden.lineTo(offset.x, offset.y)
            isRidden -> ridden.moveTo(offset.x, offset.y)
            !hasAhead -> (previous ?: offset).let { ahead.moveTo(it.x, it.y) }
        }
        if (isRidden) hasRidden = true
        if (!isRidden) {
            ahead.lineTo(offset.x, offset.y)
            hasAhead = true
        }
        previous = offset
    }
    return ridden to ahead
}

/** Half of the larger side of the route bounding box, in meters (whole-route view). */
private fun List<RoutePoint>.extentM(centerLat: Double, centerLon: Double, scaleX: Double): Double = maxOf(
    maxOf { kotlin.math.abs(it.latDeg - centerLat) } * METERS_PER_DEGREE,
    maxOf { kotlin.math.abs(it.lonDeg - centerLon) } * scaleX,
).coerceAtLeast(VIEW_HALF_WIDTH_M)

@ThemePreviews
@Composable
private fun BreadcrumbPreview() {
    val line = (0..20).map { RoutePoint(it * 0.0005, kotlin.math.sin(it / 3.0) * 0.001, null, it * 55.0) }
    RideCompPreview {
        BreadcrumbMap(line = line.toImmutableList(), distanceAlongM = 500.0, latDeg = 0.0045, lonDeg = 0.0002)
    }
}
