package com.ridecomp.core.engine.route

import com.ridecomp.core.config.Units
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Climb detection thresholds (12-FEATURE-SPECS §6.1; RideComp defaults, not Garmin's algorithm). */
public object ClimbConfig {
    public const val RESAMPLE_M: Double = 20.0
    public const val SMOOTHING_WINDOW_M: Double = 100.0
    public const val MERGE_MAX_DROP_M: Double = 10.0
    public const val MERGE_MAX_GAP_M: Double = 200.0

    /** A small dip (< [MERGE_MAX_DROP_M]) still splits climbs when the flat part is longer than this (D-49). */
    public const val MERGE_MAX_FLAT_GAP_M: Double = 500.0
    public const val MIN_LENGTH_M: Double = 500.0
    public const val MIN_GAIN_M: Double = 20.0
    public const val MIN_AVG_GRADE_PERCENT: Double = 3.0
    public const val PROFILE_STEP_M: Double = 100.0

    /** At most this many bars on the climb page, so grade numbers fit (13-UI-SCREENS #8). */
    public const val MAX_PROFILE_BARS: Int = 12

    /** Warning before a climb starts (§6.2). */
    public const val WARNING_DISTANCE_M: Double = 500.0

    /** Grade bands for colouring the climb profile (§6.2), upper bounds in %. */
    public val GRADE_BANDS_PERCENT: List<Double> = listOf(3.0, 6.0, 9.0, 12.0)
}

/** A climb found at import (05-DATA-MODEL `route_climbs`). Grades in percent. */
public data class RouteClimb(
    val index: Int,
    val startDistanceM: Double,
    val endDistanceM: Double,
    val gainM: Double,
    val avgGradePercent: Double,
    val maxGradePercent: Double,
    /** Grade of each 100 m of the climb. */
    val gradeProfilePercent: List<Double>,
) {
    val lengthM: Double get() = endDistanceM - startDistanceM

    /**
     * The grade profile merged into at most [maxBars] bars of equal sections (100, 200, 300 m…), so each
     * bar is wide enough to carry its grade number (13-UI-SCREENS #8). Returns the section length too.
     */
    public fun displayProfile(maxBars: Int = ClimbConfig.MAX_PROFILE_BARS): DisplayProfile {
        if (gradeProfilePercent.isEmpty()) return DisplayProfile(emptyList(), ClimbConfig.PROFILE_STEP_M)
        val group = (gradeProfilePercent.size + maxBars - 1) / maxBars
        val bars = gradeProfilePercent.chunked(group) { it.average() }
        return DisplayProfile(bars, ClimbConfig.PROFILE_STEP_M * group)
    }
}

/** Bars of a climb profile and the length each bar covers. */
public data class DisplayProfile(val gradesPercent: List<Double>, val sectionM: Double)

/** Turn detection thresholds (12-FEATURE-SPECS §7.1). */
public object TurnConfig {
    public const val RESAMPLE_M: Double = 10.0
    public const val BEARING_SPAN_M: Double = 30.0
    public const val TURN_DEG: Double = 45.0
    public const val SHARP_DEG: Double = 100.0
    public const val UTURN_DEG: Double = 150.0
    public const val MERGE_M: Double = 30.0
    public const val IGNORE_ENDS_M: Double = 50.0

    /** Alerts ≈ 150 m and ≈ 30 m before the turn (§7.2) … */
    public const val FAR_ALERT_M: Double = 150.0
    public const val NEAR_ALERT_M: Double = 30.0

    /** … earlier above 30 km/h: the same lead time as at 30 km/h. */
    public val REFERENCE_SPEED_MPS: Double = Units.kmhToMps(30.0)
}

public enum class TurnDirection { LEFT, RIGHT, UTURN }

public enum class TurnSeverity { NORMAL, SHARP, UTURN }

/** A turn found at import (05-DATA-MODEL `route_turns`); positive angles turn right. */
public data class RouteTurn(
    val distanceM: Double,
    val angleDeg: Double,
    val direction: TurnDirection,
    val severity: TurnSeverity,
)

/** Elevation and position along the route at regular distances (linear interpolation). */
private fun List<RoutePoint>.resample(stepM: Double): List<RoutePoint> {
    if (size < 2) return this
    val total = last().cumDistanceM
    val result = ArrayList<RoutePoint>((total / stepM).toInt() + 2)
    var segment = 0
    var distance = 0.0
    while (distance <= total) {
        while (segment < lastIndex - 1 && this[segment + 1].cumDistanceM < distance) segment++
        val a = this[segment]
        val b = this[segment + 1]
        val span = b.cumDistanceM - a.cumDistanceM
        val t = if (span <= 0.0) 0.0 else ((distance - a.cumDistanceM) / span).coerceIn(0.0, 1.0)
        val elevation = if (a.elevationM != null &&
            b.elevationM != null
        ) {
            a.elevationM + t * (b.elevationM - a.elevationM)
        } else {
            null
        }
        result +=
            RoutePoint(a.latDeg + t * (b.latDeg - a.latDeg), a.lonDeg + t * (b.lonDeg - a.lonDeg), elevation, distance)
        distance += stepM
    }
    return result
}

/**
 * Some GPX files miss `<ele>` on a few points. Missing elevations are interpolated by distance between
 * the known neighbours (the nearest known one at the ends), so a gap never reads as a drop to 0 m.
 */
private fun List<RoutePoint>.withElevationGapsFilled(): List<RoutePoint> {
    if (all { it.elevationM != null }) return this
    val known = indices.filter { this[it].elevationM != null }
    var next = 0
    return mapIndexed { i, point ->
        if (point.elevationM != null) return@mapIndexed point
        while (next < known.size && known[next] < i) next++
        val before = known.getOrNull(next - 1)?.let { this[it] }
        val after = known.getOrNull(next)?.let { this[it] }
        val elevation = when {
            before?.elevationM == null -> after?.elevationM
            after?.elevationM == null -> before.elevationM
            else -> {
                val span = after.cumDistanceM - before.cumDistanceM
                val t = if (span <= 0.0) 0.0 else (point.cumDistanceM - before.cumDistanceM) / span
                before.elevationM + t * (after.elevationM - before.elevationM)
            }
        }
        point.copy(elevationM = elevation)
    }
}

public object ClimbDetector {
    /** Climbs of a route; empty when it has no elevation (the feature is then hidden, §6). */
    public fun detect(points: List<RoutePoint>): List<RouteClimb> {
        if (points.size < 2 || points.none { it.elevationM != null }) return emptyList()
        val samples = points.withElevationGapsFilled().resample(ClimbConfig.RESAMPLE_M)
        val elevations = smooth(samples.map { it.elevationM ?: 0.0 })
        val runs = mergedRuns(ascendingRuns(elevations), elevations)
        return runs.mapNotNull { (start, end) -> climbOf(samples, elevations, start, end) }
            .mapIndexed { index, climb -> climb.copy(index = index) }
    }

    private fun smooth(values: List<Double>): List<Double> {
        val half = (ClimbConfig.SMOOTHING_WINDOW_M / ClimbConfig.RESAMPLE_M / 2).toInt()
        return values.indices.map { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(values.lastIndex)
            values.subList(from, to + 1).average()
        }
    }

    /** Index ranges where the smoothed elevation keeps rising. */
    private fun ascendingRuns(elevations: List<Double>): List<Pair<Int, Int>> {
        val runs = ArrayList<Pair<Int, Int>>()
        var start = -1
        for (i in 1..elevations.lastIndex) {
            val isRising = elevations[i] > elevations[i - 1]
            if (isRising && start < 0) start = i - 1
            if (!isRising && start >= 0) {
                runs += start to i - 1
                start = -1
            }
        }
        if (start >= 0) runs += start to elevations.lastIndex
        return runs
    }

    /**
     * Joins runs separated by a short gap (≤ 200 m, any dip) or by a small dip (< 10 m) that is not a long
     * flat stretch (≤ 500 m). Read literally, "< 10 m or < 200 m" would join climbs across kilometres of
     * flat road (drop 0 m), which the spec does not intend (decision D-49).
     */
    private fun mergedRuns(runs: List<Pair<Int, Int>>, elevations: List<Double>): List<Pair<Int, Int>> {
        val merged = ArrayList<Pair<Int, Int>>()
        runs.forEach { run ->
            val previous = merged.lastOrNull()
            if (previous == null) {
                merged += run
                return@forEach
            }
            val drop = elevations[previous.second] - elevations.subList(previous.second, run.first + 1).min()
            val gapM = (run.first - previous.second) * ClimbConfig.RESAMPLE_M
            val isShortGap = gapM <= ClimbConfig.MERGE_MAX_GAP_M
            val isSmallDip = drop < ClimbConfig.MERGE_MAX_DROP_M && gapM <= ClimbConfig.MERGE_MAX_FLAT_GAP_M
            if (isShortGap || isSmallDip) {
                merged[merged.lastIndex] = previous.first to run.second
            } else {
                merged += run
            }
        }
        return merged
    }

    private fun climbOf(samples: List<RoutePoint>, elevations: List<Double>, start: Int, end: Int): RouteClimb? {
        val lengthM = samples[end].cumDistanceM - samples[start].cumDistanceM
        val gainM = elevations[end] - elevations[start]
        // Length is checked first, so the grade is never computed over a zero length.
        val isValid = lengthM >= ClimbConfig.MIN_LENGTH_M &&
            gainM >= ClimbConfig.MIN_GAIN_M &&
            gainM / lengthM * Units.PERCENT >= ClimbConfig.MIN_AVG_GRADE_PERCENT
        if (!isValid) return null
        val avgGrade = gainM / lengthM * Units.PERCENT
        val step = (ClimbConfig.PROFILE_STEP_M / ClimbConfig.RESAMPLE_M).toInt()
        val profile = (start until end step step).map { i ->
            val j = (i + step).coerceAtMost(end)
            val run = samples[j].cumDistanceM - samples[i].cumDistanceM
            if (run <= 0.0) 0.0 else (elevations[j] - elevations[i]) / run * Units.PERCENT
        }
        return RouteClimb(
            index = 0,
            startDistanceM = samples[start].cumDistanceM,
            endDistanceM = samples[end].cumDistanceM,
            gainM = gainM,
            avgGradePercent = avgGrade,
            maxGradePercent = profile.maxOrNull() ?: avgGrade,
            gradeProfilePercent = profile,
        )
    }
}

public object TurnDetector {
    private const val FULL_TURN_DEG = 360.0
    private const val HALF_TURN_DEG = 180.0

    /** Turns from the route geometry (§7.1): bearing change across ±30 m, merged within 30 m. */
    public fun detect(points: List<RoutePoint>): List<RouteTurn> {
        if (points.size < 2) return emptyList()
        val samples = PolylineSimplifier.simplify(points).resample(TurnConfig.RESAMPLE_M)
        val span = (TurnConfig.BEARING_SPAN_M / TurnConfig.RESAMPLE_M).toInt()
        val total = samples.last().cumDistanceM
        val candidates = (span until samples.size - span).mapNotNull { i ->
            val distance = samples[i].cumDistanceM
            if (distance < TurnConfig.IGNORE_ENDS_M ||
                distance > total - TurnConfig.IGNORE_ENDS_M
            ) {
                return@mapNotNull null
            }
            val angle = signedAngle(bearing(samples[i - span], samples[i]), bearing(samples[i], samples[i + span]))
            turnOf(distance, angle)
        }
        return merge(candidates)
    }

    private fun turnOf(distanceM: Double, angleDeg: Double): RouteTurn? {
        val magnitude = kotlin.math.abs(angleDeg)
        val severity = when {
            magnitude >= TurnConfig.UTURN_DEG -> TurnSeverity.UTURN
            magnitude >= TurnConfig.SHARP_DEG -> TurnSeverity.SHARP
            magnitude >= TurnConfig.TURN_DEG -> TurnSeverity.NORMAL
            else -> return null
        }
        val direction = when {
            severity == TurnSeverity.UTURN -> TurnDirection.UTURN
            angleDeg > 0 -> TurnDirection.RIGHT
            else -> TurnDirection.LEFT
        }
        return RouteTurn(distanceM, angleDeg, direction, severity)
    }

    /** Neighbouring detections of one corner (within 30 m) become one turn: the strongest. */
    private fun merge(candidates: List<RouteTurn>): List<RouteTurn> {
        val merged = ArrayList<RouteTurn>()
        candidates.forEach { turn ->
            val last = merged.lastOrNull()
            when {
                last == null || turn.distanceM - last.distanceM > TurnConfig.MERGE_M -> merged += turn
                kotlin.math.abs(turn.angleDeg) > kotlin.math.abs(last.angleDeg) -> merged[merged.lastIndex] = turn
            }
        }
        return merged
    }

    /** Initial bearing in degrees, clockwise from north. */
    private fun bearing(from: RoutePoint, to: RoutePoint): Double {
        val lat1 = Math.toRadians(from.latDeg)
        val lat2 = Math.toRadians(to.latDeg)
        val deltaLon = Math.toRadians(to.lonDeg - from.lonDeg)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return Math.toDegrees(atan2(y, x))
    }

    private fun signedAngle(fromDeg: Double, toDeg: Double): Double {
        var delta = (toDeg - fromDeg) % FULL_TURN_DEG
        if (delta > HALF_TURN_DEG) delta -= FULL_TURN_DEG
        if (delta < -HALF_TURN_DEG) delta += FULL_TURN_DEG
        return delta
    }
}

/** One-shot route alerts while riding (vibration + banner, 12-FEATURE-SPECS §6.2, §7.2). */
public enum class RouteAlert { TURN_FAR, TURN_NEAR, CLIMB_AHEAD }

public data class UpcomingTurn(
    val direction: TurnDirection,
    val severity: TurnSeverity,
    val distanceM: Double,
    /** Within the (speed-scaled) far alert distance: the turn banner is shown. */
    val isAnnounced: Boolean = false,
)

/** Climb the rider is on ("Climb 2 of 4"), with what is left of it. */
public data class ClimbStatus(
    val number: Int,
    val total: Int,
    val remainingDistanceM: Double,
    val remainingGainM: Double,
    val climb: RouteClimb,
) {
    /** Average grade of what is left of the climb. */
    val remainingAvgGradePercent: Double
        get() = if (remainingDistanceM > 0.0) remainingGainM / remainingDistanceM * Units.PERCENT else 0.0

    /** Position on the climb, 0..1. */
    val progressFraction: Double
        get() = if (climb.lengthM > 0.0) (1.0 - remainingDistanceM / climb.lengthM).coerceIn(0.0, 1.0) else 1.0
}

/** Next climb ahead ("Next climb in 3.4 km"). */
public data class NextClimb(
    val number: Int,
    val total: Int,
    val distanceToStartM: Double,
    val climb: RouteClimb,
    /** Within [ClimbConfig.WARNING_DISTANCE_M]: the climb banner is shown. */
    val isAnnounced: Boolean = false,
)

/**
 * Climb and turn information along the route (12-FEATURE-SPECS §6.2, §7.2). Each turn is announced once
 * at the far and once at the near distance; each climb once 500 m before it. Turns are silent while off
 * route (off route has priority).
 */
public class ClimbTurnTracker(private val climbs: List<RouteClimb>, private val turns: List<RouteTurn>) {
    private val announcedFar = HashSet<Int>()
    private val announcedNear = HashSet<Int>()
    private val announcedClimbs = HashSet<Int>()

    public data class Snapshot(
        val nextTurn: UpcomingTurn?,
        val climb: ClimbStatus?,
        val nextClimb: NextClimb?,
        val alert: RouteAlert?,
    )

    public fun update(distanceAlongM: Double, speedMps: Double, isOffRoute: Boolean): Snapshot {
        val turnIndex = turns.indexOfFirst { it.distanceM > distanceAlongM }
        val turn = turns.getOrNull(turnIndex)
        val upcoming = turn?.let {
            val distanceM = it.distanceM - distanceAlongM
            UpcomingTurn(
                it.direction,
                it.severity,
                distanceM,
                distanceM <= TurnConfig.FAR_ALERT_M * alertScale(speedMps),
            )
        }
        val climbIndex = climbs.indexOfFirst { distanceAlongM < it.endDistanceM }
        val current = climbs.getOrNull(climbIndex)?.takeIf { distanceAlongM >= it.startDistanceM }
        val next = climbs.getOrNull(climbIndex)?.takeIf { distanceAlongM < it.startDistanceM }
        val alert = if (isOffRoute) {
            null
        } else {
            turnAlert(turnIndex, upcoming, speedMps)
                ?: climbAlert(climbIndex, next, distanceAlongM)
        }
        return Snapshot(
            nextTurn = upcoming.takeIf { !isOffRoute },
            climb = current?.let {
                ClimbStatus(
                    climbIndex + 1,
                    climbs.size,
                    it.endDistanceM - distanceAlongM,
                    it.remainingGain(distanceAlongM),
                    it,
                )
            },
            nextClimb = next?.let {
                val distanceM = it.startDistanceM - distanceAlongM
                NextClimb(climbIndex + 1, climbs.size, distanceM, it, distanceM <= ClimbConfig.WARNING_DISTANCE_M)
            },
            alert = alert,
        )
    }

    /** Far/near alert distances grow with speed above 30 km/h (same lead time, §7.2). */
    private fun turnAlert(index: Int, turn: UpcomingTurn?, speedMps: Double): RouteAlert? {
        if (turn == null) return null
        val factor = alertScale(speedMps)
        return when {
            turn.distanceM <= TurnConfig.NEAR_ALERT_M * factor -> RouteAlert.TURN_NEAR.takeIf {
                announcedNear.add(index)
            }
            turn.distanceM <= TurnConfig.FAR_ALERT_M * factor -> RouteAlert.TURN_FAR.takeIf { announcedFar.add(index) }
            else -> null
        }
    }

    private fun alertScale(speedMps: Double): Double = (speedMps / TurnConfig.REFERENCE_SPEED_MPS).coerceAtLeast(1.0)

    private fun climbAlert(index: Int, next: RouteClimb?, distanceAlongM: Double): RouteAlert? =
        next?.takeIf { it.startDistanceM - distanceAlongM <= ClimbConfig.WARNING_DISTANCE_M }
            ?.let { RouteAlert.CLIMB_AHEAD.takeIf { announcedClimbs.add(index) } }

    /** Gain still ahead on this climb, from its 100 m grade profile. */
    private fun RouteClimb.remainingGain(distanceAlongM: Double): Double {
        val into = distanceAlongM - startDistanceM
        return gradeProfilePercent.withIndex().sumOf { (i, grade) ->
            val segmentStart = i * ClimbConfig.PROFILE_STEP_M
            val segmentEnd = minOf(segmentStart + ClimbConfig.PROFILE_STEP_M, lengthM)
            val left = (segmentEnd - maxOf(segmentStart, into)).coerceAtLeast(0.0)
            left * grade.coerceAtLeast(0.0) / Units.PERCENT
        }
    }
}
