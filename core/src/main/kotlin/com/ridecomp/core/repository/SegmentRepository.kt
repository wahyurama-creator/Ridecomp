package com.ridecomp.core.repository

import com.ridecomp.core.engine.segments.Segment
import com.ridecomp.core.engine.segments.SegmentMatch
import kotlinx.coroutines.flow.Flow

/** One stored effort of a ride over a segment and whether it was the segment record when analyzed. */
public data class SegmentEffort(val segmentId: Long, val activityId: Long, val match: SegmentMatch, val isPr: Boolean)

/** An effort with the ride it belongs to (segment detail list). */
public data class SegmentEffortView(val effort: SegmentEffort, val activityName: String, val startedAtMs: Long)

/**
 * A ride's pass over a segment and its place among the passes of earlier rides: 1 = segment record when the
 * ride was done (T11.7).
 */
public data class RankedSegmentEffort(
    val segmentId: Long,
    val segmentName: String,
    val lengthM: Double,
    val match: SegmentMatch,
    val rank: Int,
)

/** A segment in the list: its best time and how many efforts it has. */
public data class SegmentSummary(val segment: Segment, val bestElapsedS: Double?, val effortCount: Int)

/** Personal segments and their efforts (05-DATA-MODEL, 12-FEATURE-SPECS §9). Implemented in :data. */
public interface SegmentRepository {
    /** Stores a segment with its points; returns its id. */
    public suspend fun create(segment: Segment): Long

    /** Deletes a segment and its efforts. */
    public suspend fun delete(id: Long)

    public fun observeSummaries(): Flow<List<SegmentSummary>>

    public fun observe(id: Long): Flow<Segment?>

    /** Efforts of a segment, fastest first. */
    public fun observeEfforts(segmentId: Long): Flow<List<SegmentEffortView>>

    /** Every segment with its points (matching after a ride, live segments). */
    public suspend fun getAll(): List<Segment>

    /** Fastest elapsed time over the segment among rides started before [beforeStartedAtMs]. */
    public suspend fun bestBefore(segmentId: Long, beforeStartedAtMs: Long): Double?

    /** Segment records a ride set (share card badge). */
    public suspend fun countRecords(activityId: Long): Int

    /** Replaces the segment efforts of a ride in one transaction (re-analysis is idempotent). */
    public suspend fun replaceForActivity(activityId: Long, efforts: List<SegmentEffort>)

    /** Segments a ride passed, in ride order, each ranked against earlier rides (T11.7). */
    public fun observeRanked(activityId: Long): Flow<List<RankedSegmentEffort>>
}
