package com.ridecomp.core.engine.route

import com.ridecomp.core.config.ElevationConfig
import com.ridecomp.core.engine.geo.GeoMath
import kotlin.math.cos
import kotlin.math.hypot

/** Route constants (04-INTEGRATIONS §C.1). */
public object RouteConfig {
    /** Douglas-Peucker tolerance for drawing; distances always use the original points. */
    public const val SIMPLIFY_TOLERANCE_M: Double = 5.0

    /** Planned routes are smooth (DEM or planner data): the barometer hysteresis fits them. */
    public const val ASCENT_HYSTERESIS_M: Double = ElevationConfig.HYSTERESIS_BAROMETER_M
    public const val MAX_NAME_LENGTH: Int = GpxLimits.MAX_NAME_LENGTH
}

/** One stored route point with its distance from the start. */
public data class RoutePoint(val latDeg: Double, val lonDeg: Double, val elevationM: Double?, val cumDistanceM: Double)

/** A saved route (05-DATA-MODEL `routes`); [sourceLabel] is the original file name, text only (SEC-IN-04). */
public data class Route(
    val id: Long,
    val name: String,
    val sourceLabel: String?,
    val distanceM: Double,
    val ascentM: Double,
    val createdAtMs: Long,
)

public data class NewRoute(
    val name: String,
    val sourceLabel: String?,
    val points: List<RoutePoint>,
    val distanceM: Double,
    val ascentM: Double,
    val createdAtMs: Long,
    val climbs: List<RouteClimb> = emptyList(),
    val turns: List<RouteTurn> = emptyList(),
) {
    public val hasElevation: Boolean get() = points.any { it.elevationM != null }

    /** Climbs and turns detected from the points (done once at import, T3.11/T3.12). */
    public fun withDetections(): NewRoute =
        copy(climbs = ClimbDetector.detect(points), turns = TurnDetector.detect(points))
}

public object RouteBuilder {
    /** Cumulative distance and total ascent (hysteresis, 05-DATA-MODEL §3.5) from parsed points. */
    public fun build(points: List<GpxPoint>, name: String, sourceLabel: String?, nowMs: Long): NewRoute {
        var distance = 0.0
        val routePoints = points.mapIndexed { index, point ->
            if (index > 0) {
                val previous = points[index - 1]
                distance += GeoMath.distanceM(previous.latDeg, previous.lonDeg, point.latDeg, point.lonDeg)
            }
            RoutePoint(point.latDeg, point.lonDeg, point.elevationM, distance)
        }
        return NewRoute(
            name = name.trim().take(RouteConfig.MAX_NAME_LENGTH),
            sourceLabel = sourceLabel?.take(RouteConfig.MAX_NAME_LENGTH),
            points = routePoints,
            distanceM = distance,
            ascentM = ascent(points.mapNotNull { it.elevationM }),
            createdAtMs = nowMs,
        )
    }

    /** Points in reverse order with distances recomputed ("balik arah", T3.2). */
    public fun reversed(points: List<RoutePoint>): List<RoutePoint> {
        val total = points.lastOrNull()?.cumDistanceM ?: return emptyList()
        return points.asReversed().map { it.copy(cumDistanceM = total - it.cumDistanceM) }
    }

    /** Climb with hysteresis: small ups and downs within the threshold do not count. */
    public fun ascent(elevations: List<Double>): Double {
        var anchor = elevations.firstOrNull() ?: return 0.0
        var total = 0.0
        elevations.forEach { elevation ->
            when {
                elevation - anchor >= RouteConfig.ASCENT_HYSTERESIS_M -> {
                    total += elevation - anchor
                    anchor = elevation
                }
                anchor - elevation >= RouteConfig.ASCENT_HYSTERESIS_M -> anchor = elevation
            }
        }
        return total
    }
}

/**
 * Douglas-Peucker on a local flat projection (fine for the few km between neighbouring points). Iterative,
 * so 50 000 points cannot overflow the stack.
 */
public object PolylineSimplifier {
    private const val METERS_PER_DEGREE = 111_320.0
    private const val DEGREES_TO_RADIANS = Math.PI / 180.0

    public fun simplify(
        points: List<RoutePoint>,
        toleranceM: Double = RouteConfig.SIMPLIFY_TOLERANCE_M,
    ): List<RoutePoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size).apply {
            this[0] = true
            this[lastIndex] = true
        }
        val stack = ArrayDeque<Pair<Int, Int>>().apply { addLast(0 to points.lastIndex) }
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            var farthest = -1
            var maxDistance = toleranceM
            for (i in first + 1 until last) {
                val d = distanceToSegmentM(points[i], points[first], points[last])
                if (d > maxDistance) {
                    maxDistance = d
                    farthest = i
                }
            }
            if (farthest >= 0) {
                keep[farthest] = true
                stack.addLast(first to farthest)
                stack.addLast(farthest to last)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun distanceToSegmentM(p: RoutePoint, a: RoutePoint, b: RoutePoint): Double {
        val scaleX = METERS_PER_DEGREE * cos(a.latDeg * DEGREES_TO_RADIANS)
        val bx = (b.lonDeg - a.lonDeg) * scaleX
        val by = (b.latDeg - a.latDeg) * METERS_PER_DEGREE
        val px = (p.lonDeg - a.lonDeg) * scaleX
        val py = (p.latDeg - a.latDeg) * METERS_PER_DEGREE
        val lengthSq = bx * bx + by * by
        // Point A is the origin of the local frame.
        val t = if (lengthSq == 0.0) 0.0 else ((px * bx + py * by) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(px - t * bx, py - t * by)
    }
}

/**
 * The part of a (simplified) route already ridden, ending exactly at [distanceAlongM] on the route, so the
 * ridden line meets the position dot even when the next vertex is far away (T3.5).
 */
public fun List<RoutePoint>.riddenUntil(distanceAlongM: Double): List<RoutePoint> {
    val next = indexOfFirst { it.cumDistanceM > distanceAlongM }
    return when (next) {
        -1 -> this
        0 -> emptyList()
        else -> subList(0, next) + pointBetween(this[next - 1], this[next], distanceAlongM)
    }
}

/** The point at [distanceAlongM] on the straight segment [from]–[to] (linear in degrees: short segments). */
private fun pointBetween(from: RoutePoint, to: RoutePoint, distanceAlongM: Double): RoutePoint {
    val fraction = (distanceAlongM - from.cumDistanceM) / (to.cumDistanceM - from.cumDistanceM)
    return RoutePoint(
        latDeg = from.latDeg + (to.latDeg - from.latDeg) * fraction,
        lonDeg = from.lonDeg + (to.lonDeg - from.lonDeg) * fraction,
        elevationM = null,
        cumDistanceM = distanceAlongM,
    )
}
