package com.ridecomp.core.usecase

import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.engine.route.ClimbDetector
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.segments.LiveSegment
import com.ridecomp.core.engine.segments.Segment
import com.ridecomp.core.engine.segments.SegmentBuilder
import com.ridecomp.core.engine.segments.SegmentError
import com.ridecomp.core.engine.segments.SegmentMatcher
import com.ridecomp.core.engine.segments.SegmentSource
import com.ridecomp.core.engine.segments.TrackProfile
import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.Activity
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.core.repository.SegmentEffort
import com.ridecomp.core.repository.SegmentEffortView
import com.ridecomp.core.repository.SegmentRepository
import com.ridecomp.core.repository.SegmentSummary
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import kotlin.math.ceil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Analysis step 2 (T4.2, 12-FEATURE-SPECS §9.2): efforts of the ride over every segment whose box it touches.
 * A ride riding a segment twice gets two efforts; the record flag compares with earlier rides and laps.
 */
public class SegmentsStep(private val segments: SegmentRepository, private val dispatchers: DispatcherProvider) :
    AnalysisStep {
    override val version: Int = AnalysisConfig.SEGMENTS_VERSION

    override suspend fun analyze(activity: Activity, track: RideTrack) {
        val all = segments.getAll()
        val matches = withContext(dispatchers.default) {
            val rideBounds = SegmentMatcher.boundsOf(track)
            all.filter { SegmentMatcher.mayOverlap(it, rideBounds) }
                .associate { it.id to SegmentMatcher.match(it, track) }
                .filterValues { it.isNotEmpty() }
        }
        val efforts = matches.flatMap { (segmentId, rideMatches) ->
            var best = segments.bestBefore(segmentId, activity.startedAtMs)
            rideMatches.map { match ->
                val isPr = best?.let { match.elapsedS < it } ?: true
                if (isPr) best = match.elapsedS
                SegmentEffort(segmentId, activity.id, match, isPr)
            }
        }
        segments.replaceForActivity(activity.id, efforts)
    }
}

/** Creates a segment from part of a saved ride (§9.1); old rides are matched against it afterwards. */
public class CreateSegmentFromActivityUseCase(
    private val readTrack: ReadRideTrackUseCase,
    private val segments: SegmentRepository,
    private val reanalyze: RequestReanalysisUseCase,
    private val clock: Clock,
) {
    public suspend operator fun invoke(
        activityId: Long,
        startDistanceM: Double,
        endDistanceM: Double,
        name: String,
    ): Outcome<Long, SegmentError> {
        val track = readTrack(activityId)
        val points = (0 until track.size)
            .filter { track.distanceM(it) in startDistanceM..endDistanceM && track.hasPosition(it) }
            .map { i ->
                val altitude = track.altitudeM(i).takeUnless(Double::isNaN)
                RoutePoint(track.latDeg(i), track.lonDeg(i), altitude, track.distanceM(i))
            }
        return SegmentBuilder.build(name, points, SegmentSource.ACTIVITY, clock.nowMs()).store(segments, reanalyze)
    }
}

/** "Jadikan segmen" on a route climb (§9.1). */
public class CreateSegmentFromClimbUseCase(
    private val routes: RouteRepository,
    private val segments: SegmentRepository,
    private val reanalyze: RequestReanalysisUseCase,
    private val clock: Clock,
) {
    /** [climb] as shown on the route detail (it may be detected on read for old routes, D-52). */
    public suspend operator fun invoke(routeId: Long, climb: RouteClimb, name: String): Outcome<Long, SegmentError> {
        val points = routes.getPoints(routeId).filter { it.cumDistanceM in climb.startDistanceM..climb.endDistanceM }
        return SegmentBuilder.build(name, points, SegmentSource.CLIMB, clock.nowMs()).store(segments, reanalyze)
    }
}

/** Saves a built segment and asks for old rides to be matched against it. */
private suspend fun Outcome<Segment, SegmentError>.store(
    segments: SegmentRepository,
    reanalyze: RequestReanalysisUseCase,
): Outcome<Long, SegmentError> = when (this) {
    is Outcome.Failure -> error.asFailure()
    is Outcome.Success -> segments.create(value).also { reanalyze(AnalysisConfig.SEGMENTS_VERSION) }.asSuccess()
}

public class DeleteSegmentUseCase(private val segments: SegmentRepository) {
    public suspend operator fun invoke(id: Long) {
        segments.delete(id)
    }
}

public class ObserveSegmentsUseCase(private val segments: SegmentRepository) {
    public operator fun invoke(): Flow<List<SegmentSummary>> = segments.observeSummaries()
}

public class ObserveSegmentUseCase(private val segments: SegmentRepository) {
    public operator fun invoke(id: Long): Flow<Segment?> = segments.observe(id)
}

public class ObserveSegmentEffortsUseCase(private val segments: SegmentRepository) {
    public operator fun invoke(segmentId: Long): Flow<List<SegmentEffortView>> = segments.observeEfforts(segmentId)
}

/** A downsampled point of a ride for the segment editor: position (when known), distance and altitude. */
public data class OutlinePoint(val latDeg: Double?, val lonDeg: Double?, val distanceM: Double, val altitudeM: Double?)

/**
 * A ride's line and profile for the segment editor. [windowM] is the distance range of the time window asked
 * for ("make the best effort a segment", T11.8); null when none was asked or it is not on the track.
 */
public data class TrackOutline(
    val points: List<OutlinePoint>,
    val windowM: ClosedFloatingPointRange<Double>?,
    /** Full-resolution figures for the chosen part (T11.15). */
    val profile: TrackProfile = TrackProfile(DoubleArray(0), LongArray(0), DoubleArray(0)),
    /** Climbs of the ride, offered as quick picks (T11.15); empty without altitude. */
    val climbs: List<RouteClimb> = emptyList(),
)

/** A ride's outline, at most [ActivityConfig.CHART_MAX_POINTS] points, and the distances of [windowMs]. */
public class LoadTrackOutlineUseCase(
    private val readTrack: ReadRideTrackUseCase,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(activityId: Long, windowMs: LongRange? = null): TrackOutline {
        val track = readTrack(activityId)
        return withContext(dispatchers.default) {
            val stride = ceil(track.size.toDouble() / ActivityConfig.CHART_MAX_POINTS).toInt().coerceAtLeast(1)
            val points = (0 until track.size step stride).map { i ->
                val hasPosition = track.hasPosition(i)
                OutlinePoint(
                    latDeg = track.latDeg(i).takeIf { hasPosition },
                    lonDeg = track.lonDeg(i).takeIf { hasPosition },
                    distanceM = track.distanceM(i),
                    altitudeM = track.altitudeM(i).takeUnless(Double::isNaN),
                )
            }
            TrackOutline(points, windowMs?.let { track.distanceRange(it) }, track.profile(), track.climbs())
        }
    }

    private fun RideTrack.profile() = TrackProfile(
        DoubleArray(size) { distanceM(it) },
        LongArray(size) { timeMs(it) },
        DoubleArray(size) { altitudeM(it) },
    )

    /** The route climb detector over the positioned samples (same thresholds as routes, D-52). */
    private fun RideTrack.climbs(): List<RouteClimb> = ClimbDetector.detect(
        (0 until size).filter { hasPosition(it) }.map { i ->
            RoutePoint(latDeg(i), lonDeg(i), altitudeM(i).takeUnless(Double::isNaN), distanceM(i))
        },
    )

    /** Distances of the first and last samples inside [windowMs]. */
    private fun RideTrack.distanceRange(windowMs: LongRange): ClosedFloatingPointRange<Double>? {
        val start = (0 until size).firstOrNull { timeMs(it) in windowMs } ?: return null
        val end = (0 until size).last { timeMs(it) in windowMs }
        return if (distanceM(end) > distanceM(start)) distanceM(start)..distanceM(end) else null
    }
}

/** Every segment with the rider's record over it, loaded once when a ride starts (live segments, T4.3). */
public class GetLiveSegmentsUseCase(private val segments: SegmentRepository) {
    public suspend operator fun invoke(): List<LiveSegment> = segments.getAll().map { segment ->
        LiveSegment(segment, segments.bestBefore(segment.id, Long.MAX_VALUE))
    }
}
