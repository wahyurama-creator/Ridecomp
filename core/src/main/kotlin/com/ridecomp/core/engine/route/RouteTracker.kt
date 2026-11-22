package com.ridecomp.core.engine.route

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.geo.AlongRoute
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideEventType
import kotlin.math.cos
import kotlin.math.hypot

/** Route following thresholds (T3.6, T3.7; decision D-46). Calibrate with field test L7. */
public object RouteTrackerConfig {
    /** Off route beyond this distance (or 1.5 × GPS accuracy when larger) … */
    public const val OFF_ROUTE_DISTANCE_M: Double = 50.0
    public const val ACCURACY_FACTOR: Double = 1.5

    /** … for this long, so one bad fix never alerts. */
    public const val OFF_ROUTE_DELAY_MS: Long = 10L * Units.MS_PER_SECOND

    /** Back on route below this distance for this long (hysteresis). */
    public const val BACK_ON_ROUTE_DISTANCE_M: Double = 30.0
    public const val BACK_ON_ROUTE_DELAY_MS: Long = 5L * Units.MS_PER_SECOND

    /** Matching looks this far behind / ahead of the last progress, so crossings and loops do not jump. */
    public const val SEARCH_BEHIND_M: Double = 200.0
    public const val SEARCH_AHEAD_M: Double = 2_000.0

    /** No ETA below this average speed (standing, walking the bike). */
    public const val MIN_ETA_SPEED_MPS: Double = 1.0

    /** A route needs two points to be followed. */
    public const val MIN_POINTS: Int = 2
}

/** Off/back-on route changes, logged as ride events and alerted (T3.7). */
public enum class RouteEvent { OFF_ROUTE, BACK_ON_ROUTE }

/** Where the rider is on the route (02-DESIGN §3.4 navigation page, N-05). */
public data class RouteProgress(
    val distanceAlongM: Double,
    val distanceRemainingM: Double,
    /** Distance from the route line to the rider. */
    val distanceFromRouteM: Double,
    val isOffRoute: Boolean,
    val etaMs: Long?,
    /** Climbing still ahead (hysteresis), null when the route has no elevation. */
    val remainingAscentM: Double?,
    val event: RouteEvent? = null,
    /** Next turn ahead, hidden while off route (12-FEATURE-SPECS §7.2). */
    val nextTurn: UpcomingTurn? = null,
    /** Climb being ridden, or the next one (§6.2). */
    val climb: ClimbStatus? = null,
    val nextClimb: NextClimb? = null,
    /** One-shot alert of this second (vibration + banner). */
    val alert: RouteAlert? = null,
)

/**
 * Projects GPS fixes onto a route (T3.6). Pure and confined to one coroutine (CONC-01). Uses the original
 * points so distances stay exact; a local flat projection is accurate at segment scale.
 */
public class RouteTracker(private val points: List<RoutePoint>) {
    private val total = points.lastOrNull()?.cumDistanceM ?: 0.0
    private val ascentAfter: DoubleArray? = remainingAscents(points)
    private var progressM = 0.0
    private var isOff = false
    private var farSinceMs: Long? = null
    private var nearSinceMs: Long? = null

    public fun update(
        latDeg: Double,
        lonDeg: Double,
        accuracyM: Double?,
        timeMs: Long,
        avgSpeedMps: Double,
    ): RouteProgress {
        val match = project(latDeg, lonDeg, windowed = !isOff) ?: project(latDeg, lonDeg, windowed = false)
        val distanceFromRoute = match?.distanceM ?: Double.MAX_VALUE
        if (match != null && (!isOff || distanceFromRoute < RouteTrackerConfig.BACK_ON_ROUTE_DISTANCE_M)) {
            progressM = match.alongM
        }
        val event = updateOffRoute(distanceFromRoute, accuracyM, timeMs)
        val remaining = (total - progressM).coerceAtLeast(0.0)
        return RouteProgress(
            distanceAlongM = progressM,
            distanceRemainingM = remaining,
            distanceFromRouteM = distanceFromRoute,
            isOffRoute = isOff,
            etaMs = avgSpeedMps.takeIf { it >= RouteTrackerConfig.MIN_ETA_SPEED_MPS }
                ?.let { (remaining / it * Units.MS_PER_SECOND).toLong() },
            remainingAscentM = ascentAfter?.let { it[indexAt(progressM)] },
            event = event,
        )
    }

    private fun updateOffRoute(distanceM: Double, accuracyM: Double?, timeMs: Long): RouteEvent? {
        val offLimit = maxOf(
            RouteTrackerConfig.OFF_ROUTE_DISTANCE_M,
            (accuracyM ?: 0.0) * RouteTrackerConfig.ACCURACY_FACTOR,
        )
        val isChanged = if (isOff) {
            farSinceMs = null
            nearSinceMs = sinceWhile(distanceM < RouteTrackerConfig.BACK_ON_ROUTE_DISTANCE_M, nearSinceMs, timeMs)
            nearSinceMs.hasLasted(timeMs, RouteTrackerConfig.BACK_ON_ROUTE_DELAY_MS)
        } else {
            nearSinceMs = null
            farSinceMs = sinceWhile(distanceM > offLimit, farSinceMs, timeMs)
            farSinceMs.hasLasted(timeMs, RouteTrackerConfig.OFF_ROUTE_DELAY_MS)
        }
        if (!isChanged) return null
        isOff = !isOff
        farSinceMs = null
        nearSinceMs = null
        return if (isOff) RouteEvent.OFF_ROUTE else RouteEvent.BACK_ON_ROUTE
    }

    /** Start time of a condition that still holds, or null once it stops holding. */
    private fun sinceWhile(isHolding: Boolean, since: Long?, timeMs: Long): Long? =
        if (isHolding) since ?: timeMs else null

    private fun Long?.hasLasted(timeMs: Long, durationMs: Long): Boolean = this != null && timeMs - this >= durationMs

    /**
     * Meters along the route from one position to a later one (D-150): both must be within the off-route
     * distance and the second not behind the first. Searches the whole route (only after a GPS gap).
     */
    public fun alongRouteM(fromLatDeg: Double, fromLonDeg: Double, toLatDeg: Double, toLonDeg: Double): Double? {
        val from = project(fromLatDeg, fromLonDeg, windowed = false)?.takeIf(::isOnRoute)
        val to = project(toLatDeg, toLonDeg, windowed = false)?.takeIf(::isOnRoute)
        return if (from == null || to == null) null else (to.alongM - from.alongM).takeIf { it >= 0.0 }
    }

    private fun isOnRoute(match: Match): Boolean = match.distanceM <= RouteTrackerConfig.OFF_ROUTE_DISTANCE_M

    private class Match(val alongM: Double, val distanceM: Double)

    /** Nearest point on the route; within the search window around the last progress when [windowed]. */
    private fun project(latDeg: Double, lonDeg: Double, windowed: Boolean): Match? {
        if (points.size < 2) return null
        val from = if (windowed) indexAt(progressM - RouteTrackerConfig.SEARCH_BEHIND_M) else 0
        val to = if (windowed) indexAt(progressM + RouteTrackerConfig.SEARCH_AHEAD_M) + 1 else points.lastIndex
        var best: Match? = null
        var bestCost = Double.MAX_VALUE
        val scaleX = METERS_PER_DEGREE * cos(latDeg * DEGREES_TO_RADIANS)
        for (i in from until to.coerceAtMost(points.lastIndex)) {
            val a = points[i]
            val b = points[i + 1]
            val bx = (b.lonDeg - a.lonDeg) * scaleX
            val by = (b.latDeg - a.latDeg) * METERS_PER_DEGREE
            val px = (lonDeg - a.lonDeg) * scaleX
            val py = (latDeg - a.latDeg) * METERS_PER_DEGREE
            val lengthSq = bx * bx + by * by
            val t = if (lengthSq == 0.0) 0.0 else ((px * bx + py * by) / lengthSq).coerceIn(0.0, 1.0)
            val distance = hypot(px - t * bx, py - t * by)
            val along = a.cumDistanceM + t * (b.cumDistanceM - a.cumDistanceM)
            // Riding goes forward: where two legs overlap (out-and-back, crossings) the forward one wins.
            val cost = if (windowed) distance + (progressM - along).coerceAtLeast(0.0) else distance
            if (cost < bestCost) {
                bestCost = cost
                best = Match(along, distance)
            }
        }
        return best?.takeIf { !windowed || it.distanceM <= RouteTrackerConfig.OFF_ROUTE_DISTANCE_M }
    }

    /** Index of the last point at or before [distanceM] (binary search on cumulative distance). */
    private fun indexAt(distanceM: Double): Int {
        var low = 0
        var high = points.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (points[mid].cumDistanceM <= distanceM) low = mid else high = mid - 1
        }
        return low
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_320.0
        const val DEGREES_TO_RADIANS = Math.PI / 180.0

        /**
         * Climbing still ahead of each point, O(n): each climb counted by the hysteresis walk (same rule as the
         * route total) is attributed to the point where it completes; a suffix sum gives the rest.
         */
        fun remainingAscents(points: List<RoutePoint>): DoubleArray? {
            if (points.none { it.elevationM != null }) return null
            val contribution = DoubleArray(points.size)
            var anchor: Double? = null
            points.forEachIndexed { i, point ->
                val elevation = point.elevationM ?: return@forEachIndexed
                val base = anchor
                when {
                    base == null -> anchor = elevation
                    elevation - base >= RouteConfig.ASCENT_HYSTERESIS_M -> {
                        contribution[i] = elevation - base
                        anchor = elevation
                    }
                    base - elevation >= RouteConfig.ASCENT_HYSTERESIS_M -> anchor = elevation
                }
            }
            val result = DoubleArray(points.size)
            for (i in points.lastIndex - 1 downTo 0) result[i] = result[i + 1] + contribution[i + 1]
            return result
        }
    }
}

/**
 * Route following inside a ride (T3.6/T3.7): tracks progress while riding (auto pause included) and
 * reports off-route / back-on-route as ride events. Without a route it does nothing.
 */
public class RouteFollower(
    points: List<RoutePoint>,
    climbs: List<RouteClimb> = emptyList(),
    turns: List<RouteTurn> = emptyList(),
) {
    private val climbsAndTurns = ClimbTurnTracker(climbs, turns)
    private val tracker = points.takeIf { it.size >= RouteTrackerConfig.MIN_POINTS }?.let(::RouteTracker)

    /** Distance along this route, for a stretch ridden without GPS (D-150); null without a route. */
    public val alongRoute: AlongRoute? = tracker?.let { AlongRoute(it::alongRouteM) }

    /** Last known progress; kept while the fix is stale or the ride is paused. */
    public var progress: RouteProgress? = null
        private set

    /** Returns the event to log, if any. [fix] must be fresh. */
    public fun onSecond(
        isRiding: Boolean,
        fix: LocationFix?,
        nowMs: Long,
        speedMps: Double,
        avgSpeedMps: Double,
    ): RideEventType? {
        // One-shot: an alert or event never repeats on a second without a new fix.
        progress = progress?.copy(alert = null, event = null)
        val active = tracker?.takeIf { isRiding }
        if (active == null || fix == null) return null
        val update = active.update(fix.latDeg, fix.lonDeg, fix.accuracyM, nowMs, avgSpeedMps)
        val extras = climbsAndTurns.update(update.distanceAlongM, speedMps, update.isOffRoute)
        progress = update.copy(
            nextTurn = extras.nextTurn,
            climb = extras.climb,
            nextClimb = extras.nextClimb,
            alert = extras.alert,
        )
        return when (update.event) {
            RouteEvent.OFF_ROUTE -> RideEventType.OFF_ROUTE
            RouteEvent.BACK_ON_ROUTE -> RideEventType.BACK_ON_ROUTE
            null -> null
        }
    }
}
