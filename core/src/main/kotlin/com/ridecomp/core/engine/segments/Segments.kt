package com.ridecomp.core.engine.segments

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.geo.GeoMath
import com.ridecomp.core.engine.map.MapBounds
import com.ridecomp.core.engine.route.PolylineSimplifier
import com.ridecomp.core.engine.route.RouteBuilder
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess

/** Personal segment rules (12-FEATURE-SPECS §9). */
public object SegmentConfig {
    public const val MIN_LENGTH_M: Double = 200.0
    public const val MAX_LENGTH_M: Double = 50_000.0
    public const val MAX_NAME_LENGTH: Int = 60

    /** The track must pass this close to the start and to the end (§9.2 step 1). */
    public const val ENDPOINT_RADIUS_M: Double = 25.0

    /** Segment points closer than this to the ridden track count as followed (§9.2 step 2). */
    public const val PATH_RADIUS_M: Double = 30.0

    /** Share of segment points that must be followed. */
    public const val PATH_MATCH_RATIO: Double = 0.9

    /**
     * The stored line is simplified (a straight road keeps only its two ends), so the path is checked on
     * points placed this far apart along it.
     */
    public const val PATH_CHECK_SPACING_M: Double = 20.0

    /** The end only counts after the rider covered this share of the segment (loops end near their start). */
    public const val MIN_TRACK_LENGTH_RATIO: Double = 0.5

    /** The search for the end stops once the rider covered this many segment lengths since the start. */
    public const val MAX_TRACK_LENGTH_RATIO: Double = 2.0

    /** Bounding boxes are widened by about this much before comparing (≈ 55 m of latitude). */
    public const val BOUNDS_MARGIN_DEG: Double = 0.0005
}

/** Where a segment was made from (05-DATA-MODEL `segments.created_from`; stored, never rename). */
public enum class SegmentSource(public val storageValue: String) {
    ACTIVITY("activity"),
    CLIMB("climb"),
    ;

    public companion object {
        public fun fromStorage(value: String): SegmentSource =
            entries.firstOrNull { it.storageValue == value } ?: ACTIVITY
    }
}

/** One point of a segment's simplified line, with its distance from the segment start. */
public data class SegmentPoint(val latDeg: Double, val lonDeg: Double, val cumDistanceM: Double)

/** A personal segment (05-DATA-MODEL `segments` + `segment_points`). */
public data class Segment(
    val id: Long,
    val name: String,
    val lengthM: Double,
    val gainM: Double,
    val avgGradePercent: Double,
    val points: List<SegmentPoint>,
    val bounds: MapBounds,
    val source: SegmentSource,
    val createdAtMs: Long,
) {
    val start: SegmentPoint get() = points.first()
    val end: SegmentPoint get() = points.last()
}

/** Why a segment could not be made. */
public enum class SegmentError { TOO_SHORT, TOO_LONG, NO_POSITION }

/** Builds a segment from part of a ride or a route climb (§9.1): simplified line, length, gain, grade. */
public object SegmentBuilder {
    public fun build(
        name: String,
        points: List<RoutePoint>,
        source: SegmentSource,
        nowMs: Long,
    ): Outcome<Segment, SegmentError> {
        val located = points.filter { GeoMath.isValidCoordinate(it.latDeg, it.lonDeg) }
        if (located.size < 2) return SegmentError.NO_POSITION.asFailure()
        val origin = located.first().cumDistanceM
        val line = located.map { it.copy(cumDistanceM = it.cumDistanceM - origin) }
        val lengthM = line.last().cumDistanceM
        return when {
            lengthM < SegmentConfig.MIN_LENGTH_M -> SegmentError.TOO_SHORT.asFailure()
            lengthM > SegmentConfig.MAX_LENGTH_M -> SegmentError.TOO_LONG.asFailure()
            else -> segment(name, line, lengthM, source, nowMs).asSuccess()
        }
    }

    private fun segment(
        name: String,
        line: List<RoutePoint>,
        lengthM: Double,
        source: SegmentSource,
        nowMs: Long,
    ): Segment {
        val elevations = line.mapNotNull { it.elevationM }
        val net = if (elevations.size >= 2) elevations.last() - elevations.first() else 0.0
        val simplified = PolylineSimplifier.simplify(line)
        return Segment(
            id = 0,
            name = name.trim().take(SegmentConfig.MAX_NAME_LENGTH),
            lengthM = lengthM,
            gainM = RouteBuilder.ascent(elevations),
            avgGradePercent = net / lengthM * Units.PERCENT,
            points = simplified.map { SegmentPoint(it.latDeg, it.lonDeg, it.cumDistanceM) },
            bounds = MapBounds(
                minLon = simplified.minOf { it.lonDeg },
                minLat = simplified.minOf { it.latDeg },
                maxLon = simplified.maxOf { it.lonDeg },
                maxLat = simplified.maxOf { it.latDeg },
            ),
            source = source,
            createdAtMs = nowMs,
        )
    }
}

/** One ride over a segment (§9.2 step 4). Time is elapsed (stops included), as on other ride platforms. */
public data class SegmentMatch(
    val startMs: Long,
    val endMs: Long,
    val elapsedS: Double,
    val avgSpeedMps: Double,
    val avgHeartRateBpm: Int?,
    val avgPowerW: Int?,
)

/**
 * Finds every effort of a ride over a segment (12-FEATURE-SPECS §9.2): the track passes ≤ 25 m from the
 * start, then ≤ 25 m from the end, and follows ≥ 90 % of the segment points in order within 30 m. Going the
 * other way never matches because the points must be met in order. The start and end are the closest
 * samples to the segment endpoints.
 */
public object SegmentMatcher {
    public fun match(segment: Segment, track: RideTrack): List<SegmentMatch> {
        val matches = mutableListOf<SegmentMatch>()
        var from = 0
        while (from < track.size) {
            val start = closestNear(track, from, segment.start) ?: break
            val end = findEnd(track, start, segment)
            if (end != null && followsPath(track, start, end, segment)) {
                matches += effort(track, start, end, segment)
                from = end + 1
            } else {
                from = start + 1
            }
        }
        return matches
    }

    /** True when the ride's box (from its positions) touches the segment's, so matching can be skipped early. */
    public fun mayOverlap(segment: Segment, rideBounds: MapBounds?): Boolean {
        val ride = rideBounds ?: return false
        val margin = SegmentConfig.BOUNDS_MARGIN_DEG
        return segment.bounds.minLat - margin <= ride.maxLat &&
            segment.bounds.maxLat + margin >= ride.minLat &&
            segment.bounds.minLon - margin <= ride.maxLon &&
            segment.bounds.maxLon + margin >= ride.minLon
    }

    /** Bounding box of the ride's positions, or null without any. */
    public fun boundsOf(track: RideTrack): MapBounds? {
        var bounds: MapBounds? = null
        for (i in 0 until track.size) {
            if (!track.hasPosition(i)) continue
            val lat = track.latDeg(i)
            val lon = track.lonDeg(i)
            bounds = bounds?.let {
                MapBounds(minOf(it.minLon, lon), minOf(it.minLat, lat), maxOf(it.maxLon, lon), maxOf(it.maxLat, lat))
            } ?: MapBounds(lon, lat, lon, lat)
        }
        return bounds
    }

    /** First run of samples within the endpoint radius from [from]; returns its closest sample. */
    private fun closestNear(track: RideTrack, from: Int, point: SegmentPoint): Int? {
        var best: Int? = null
        var bestDistance = Double.MAX_VALUE
        for (i in from until track.size) {
            val distance = distanceTo(track, i, point)
            if (distance <= SegmentConfig.ENDPOINT_RADIUS_M) {
                if (distance < bestDistance) {
                    best = i
                    bestDistance = distance
                }
            } else if (best != null) {
                break
            }
        }
        return best
    }

    private fun findEnd(track: RideTrack, start: Int, segment: Segment): Int? {
        val minRidden = segment.lengthM * SegmentConfig.MIN_TRACK_LENGTH_RATIO
        val maxRidden = segment.lengthM * SegmentConfig.MAX_TRACK_LENGTH_RATIO
        val startDistance = track.distanceM(start)
        var i = start + 1
        while (i < track.size && track.distanceM(i) - startDistance <= maxRidden) {
            val isFarEnough = track.distanceM(i) - startDistance >= minRidden
            if (isFarEnough && distanceTo(track, i, segment.end) <= SegmentConfig.ENDPOINT_RADIUS_M) {
                return closestNear(track, i, segment.end)
            }
            i++
        }
        return null
    }

    /** The check points are met in order within the path radius, for at least the required share. */
    private fun followsPath(track: RideTrack, start: Int, end: Int, segment: Segment): Boolean {
        val checks = checkPoints(segment)
        var cursor = start
        var followed = 0
        checks.forEach { point ->
            val hit = (cursor..end).firstOrNull { distanceTo(track, it, point) <= SegmentConfig.PATH_RADIUS_M }
            if (hit != null) {
                followed++
                cursor = hit
            }
        }
        return followed >= checks.size * SegmentConfig.PATH_MATCH_RATIO
    }

    /** Points every [SegmentConfig.PATH_CHECK_SPACING_M] along the simplified line (linear in degrees). */
    internal fun checkPoints(segment: Segment): List<SegmentPoint> {
        val checks = mutableListOf(segment.start)
        segment.points.zipWithNext().forEach { (from, to) ->
            val span = to.cumDistanceM - from.cumDistanceM
            val steps = (span / SegmentConfig.PATH_CHECK_SPACING_M).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val f = step.toDouble() / steps
                checks += SegmentPoint(
                    latDeg = from.latDeg + (to.latDeg - from.latDeg) * f,
                    lonDeg = from.lonDeg + (to.lonDeg - from.lonDeg) * f,
                    cumDistanceM = from.cumDistanceM + span * f,
                )
            }
        }
        return checks
    }

    private fun effort(track: RideTrack, start: Int, end: Int, segment: Segment): SegmentMatch {
        val elapsedS = (track.timeMs(end) - track.timeMs(start)).toDouble() / Units.MS_PER_SECOND
        val heartRates = (start..end).mapNotNull { track.heartRateBpm(it) }
        return SegmentMatch(
            startMs = track.timeMs(start),
            endMs = track.timeMs(end),
            elapsedS = elapsedS,
            avgSpeedMps = if (elapsedS > 0) segment.lengthM / elapsedS else 0.0,
            avgHeartRateBpm = heartRates.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            avgPowerW = if (track.hasPower) (start..end).map { track.powerW(it) }.average().toInt() else null,
        )
    }

    private fun distanceTo(track: RideTrack, index: Int, point: SegmentPoint): Double = if (track.hasPosition(index)) {
        GeoMath.distanceM(track.latDeg(index), track.lonDeg(index), point.latDeg, point.lonDeg)
    } else {
        Double.MAX_VALUE
    }
}
