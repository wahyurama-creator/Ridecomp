package com.ridecomp.core.engine.segments

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.geo.GeoMath
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.RouteTracker

/** Live segment thresholds (12-FEATURE-SPECS §9.3, D-71). */
public object LiveSegmentConfig {
    /** "Segmen: … · mulai" shows within this distance of the start. */
    public const val APPROACH_RADIUS_M: Double = 50.0

    /** The direction is checked against the segment point this far after the start. */
    public const val DIRECTION_PROBE_M: Double = 50.0

    /** Leaving the segment line by more than this for [OFF_PATH_LIMIT_MS] abandons the live effort. */
    public const val OFF_PATH_M: Double = 50.0
    public const val OFF_PATH_LIMIT_MS: Long = 15L * Units.MS_PER_SECOND

    /** How long the result banner stays. */
    public const val RESULT_SHOWN_MS: Long = 10L * Units.MS_PER_SECOND

    /** Boxes are widened by about this much so the approach is seen before entering them (≈ 110 m). */
    public const val BOX_MARGIN_DEG: Double = 0.001
}

/** A segment the ride may cross, with the rider's record over it (null before the first effort). */
public data class LiveSegment(val segment: Segment, val prElapsedS: Double?)

/** Riding a segment now (13-UI-SCREENS #17). */
public data class SegmentProgress(
    val name: String,
    val elapsedMs: Long,
    val lengthM: Double,
    val remainingM: Double,
    val prElapsedS: Double?,
    /** Elapsed minus the record's time at the same progress: negative = ahead of the record. */
    val deltaVsPrS: Double?,
) {
    val fraction: Double get() = if (lengthM > 0) ((lengthM - remainingM) / lengthM).coerceIn(0.0, 1.0) else 0.0
}

/** A finished live effort; the saved effort is matched again after the ride (§9.2). */
public data class SegmentResult(val name: String, val elapsedS: Double, val isPr: Boolean, val deltaVsPrS: Double?)

/** One-shot cue of this second (vibration). */
public enum class SegmentAlert { STARTED, FINISHED, NEW_RECORD }

public data class LiveSegmentState(
    /** Name of a segment whose start is near (approach banner), while not riding one. */
    val approaching: String? = null,
    val active: SegmentProgress? = null,
    val result: SegmentResult? = null,
    val alert: SegmentAlert? = null,
)

/**
 * Live segments (12-FEATURE-SPECS §9.3). Only segments whose box holds the position are looked at. A segment
 * starts once the rider passed closest to its start (≤ 25 m) heading along it; progress comes from a
 * [RouteTracker] on the segment line; it ends at the closest pass by the end after half its length, or is
 * abandoned after leaving the line. Pure and confined to one coroutine (CONC-01).
 */
public class LiveSegmentTracker(private val segments: List<LiveSegment>) {
    private val approaches = mutableMapOf<Long, Pass>()
    private var current: Running? = null
    private var shownResult: Pair<SegmentResult, Long>? = null

    /** The closest pass by a point so far: where and when. */
    private data class Pass(val distanceM: Double, val timeMs: Long)

    private class Running(val live: LiveSegment, val startMs: Long) {
        val tracker = RouteTracker(live.segment.points.map { RoutePoint(it.latDeg, it.lonDeg, null, it.cumDistanceM) })
        var progress: SegmentProgress? = null
        var offSinceMs: Long? = null
        var endPass: Pass? = null
    }

    public fun update(latDeg: Double, lonDeg: Double, timeMs: Long): LiveSegmentState {
        val running = current
        var alert: SegmentAlert? = null
        if (running == null) {
            startIfPassed(latDeg, lonDeg, timeMs)?.let {
                current = it
                alert = SegmentAlert.STARTED
            }
        } else {
            when (val outcome = advance(running, latDeg, lonDeg, timeMs)) {
                is Step.Finished -> {
                    current = null
                    shownResult = outcome.result to timeMs
                    alert = if (outcome.result.isPr) SegmentAlert.NEW_RECORD else SegmentAlert.FINISHED
                }
                Step.Abandoned -> current = null
                Step.Riding -> Unit
            }
        }
        val result = shownResult?.takeIf { (_, at) -> timeMs - at < LiveSegmentConfig.RESULT_SHOWN_MS }?.first
        return LiveSegmentState(
            approaching = if (current == null) approachingName(latDeg, lonDeg) else null,
            active = current?.progress,
            result = result,
            alert = alert,
        )
    }

    private fun nearby(latDeg: Double, lonDeg: Double): List<LiveSegment> = segments.filter {
        val box = it.segment.bounds
        val margin = LiveSegmentConfig.BOX_MARGIN_DEG
        latDeg in (box.minLat - margin)..(box.maxLat + margin) && lonDeg in (box.minLon - margin)..(box.maxLon + margin)
    }

    private fun approachingName(latDeg: Double, lonDeg: Double): String? = nearby(latDeg, lonDeg)
        .map { it to distance(latDeg, lonDeg, it.segment.start) }
        .filter { (_, d) -> d <= LiveSegmentConfig.APPROACH_RADIUS_M }
        .minByOrNull { (_, d) -> d }
        ?.first?.segment?.name

    /** Starts the nearby segment whose start the rider just passed closest to, heading along it. */
    private fun startIfPassed(latDeg: Double, lonDeg: Double, timeMs: Long): Running? {
        nearby(latDeg, lonDeg).forEach { live ->
            val segment = live.segment
            val d = distance(latDeg, lonDeg, segment.start)
            val closest = approaches[segment.id]
            when {
                d > LiveSegmentConfig.APPROACH_RADIUS_M -> approaches.remove(segment.id)
                closest == null || d <= closest.distanceM -> approaches[segment.id] = Pass(d, timeMs)
                closest.distanceM <= SegmentConfig.ENDPOINT_RADIUS_M && isHeadingAlong(segment, latDeg, lonDeg) -> {
                    approaches.clear()
                    return Running(live, closest.timeMs)
                }
            }
        }
        return null
    }

    /** Closer to a point a little way along the segment than the start is: moving the segment's way. */
    private fun isHeadingAlong(segment: Segment, latDeg: Double, lonDeg: Double): Boolean {
        val probe = segment.points.firstOrNull { it.cumDistanceM >= LiveSegmentConfig.DIRECTION_PROBE_M } ?: segment.end
        return distance(latDeg, lonDeg, probe) < probe.cumDistanceM
    }

    private sealed interface Step {
        data object Riding : Step

        data object Abandoned : Step

        data class Finished(val result: SegmentResult) : Step
    }

    private fun advance(running: Running, latDeg: Double, lonDeg: Double, timeMs: Long): Step {
        val segment = running.live.segment
        val pr = running.live.prElapsedS
        val match = running.tracker.update(latDeg, lonDeg, null, timeMs, 0.0)
        val elapsedMs = timeMs - running.startMs
        val alongM = match.distanceAlongM
        val expectedS = pr?.let { it * alongM / segment.lengthM }
        running.progress = SegmentProgress(
            name = segment.name,
            elapsedMs = elapsedMs,
            lengthM = segment.lengthM,
            remainingM = match.distanceRemainingM,
            prElapsedS = pr,
            deltaVsPrS = expectedS?.let { elapsedMs.toDouble() / Units.MS_PER_SECOND - it },
        )
        if (match.distanceFromRouteM > LiveSegmentConfig.OFF_PATH_M) {
            val since = running.offSinceMs ?: timeMs.also { running.offSinceMs = it }
            if (timeMs - since >= LiveSegmentConfig.OFF_PATH_LIMIT_MS) return Step.Abandoned
        } else {
            running.offSinceMs = null
        }
        return finishIfPassed(running, latDeg, lonDeg, timeMs, alongM)
    }

    private fun finishIfPassed(running: Running, latDeg: Double, lonDeg: Double, timeMs: Long, alongM: Double): Step {
        val segment = running.live.segment
        if (alongM < segment.lengthM * SegmentConfig.MIN_TRACK_LENGTH_RATIO) return Step.Riding
        val d = distance(latDeg, lonDeg, segment.end)
        val pass = running.endPass
        return when {
            pass == null || d <= pass.distanceM -> {
                if (d <= LiveSegmentConfig.APPROACH_RADIUS_M) running.endPass = Pass(d, timeMs)
                Step.Riding
            }
            pass.distanceM <= SegmentConfig.ENDPOINT_RADIUS_M -> {
                val elapsedS = (pass.timeMs - running.startMs).toDouble() / Units.MS_PER_SECOND
                val pr = running.live.prElapsedS
                val isPr = pr == null || elapsedS < pr
                Step.Finished(SegmentResult(segment.name, elapsedS, isPr, pr?.let { elapsedS - it }))
            }
            else -> Step.Riding
        }
    }

    private fun distance(latDeg: Double, lonDeg: Double, point: SegmentPoint): Double =
        GeoMath.distanceM(latDeg, lonDeg, point.latDeg, point.lonDeg)
}

/**
 * Feeds the [LiveSegmentTracker] once a second inside the recorder, like the route follower: only while riding
 * with a fresh fix; paused or without a fix the last state stays (without a new cue).
 */
public class LiveSegmentFollower(segments: List<LiveSegment>) {
    private val tracker = segments.takeIf { it.isNotEmpty() }?.let(::LiveSegmentTracker)

    public var state: LiveSegmentState? = null
        private set

    public fun onSecond(isRiding: Boolean, latDeg: Double?, lonDeg: Double?, nowMs: Long) {
        state = if (isRiding && latDeg != null && lonDeg != null) {
            tracker?.update(latDeg, lonDeg, nowMs)
        } else {
            state?.copy(alert = null)
        }
    }
}
