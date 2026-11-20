package com.ridecomp.core.usecase

import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortType
import com.ridecomp.core.engine.segments.SegmentConfig
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.RankedEffort
import com.ridecomp.core.repository.RankedSegmentEffort
import com.ridecomp.core.repository.SegmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The "Results" section of a ride (T11.7, 14-PHASE11-PLAN). */
public object ResultsConfig {
    /** Best efforts are listed when they were among the rider's 3 best at the time of the ride. */
    public const val TOP_RANKS: Int = 3
}

/**
 * What a ride achieved: its best efforts that ranked in the top 3 when it was done (record first) and every
 * segment it passed (top-3 passes first, then in ride order).
 */
public data class RideResults(val efforts: List<RankedEffort>, val segments: List<RankedSegmentEffort>) {
    public val isEmpty: Boolean get() = efforts.isEmpty() && segments.isEmpty()
}

/** Whether this rank earns a medal: 1st, 2nd or 3rd best at the time of the ride. */
public val Int.isTopRank: Boolean get() = this in 1..ResultsConfig.TOP_RANKS

/**
 * The time window of a best effort that can become a segment ("mark as sprint", T11.8): a distance or speed
 * effort with a valid segment length. Power efforts are about the rider, not the road (their length is not
 * stored); whole-ride records have no window.
 */
public val BestEffort.segmentWindowMs: LongRange?
    get() {
        val lengthM = when (key.type) {
            BestEffortType.DISTANCE -> key.target
            BestEffortType.SPEED -> value * key.target
            BestEffortType.POWER, BestEffortType.OTHER -> return null
        }
        val start = startMs ?: return null
        val end = endMs ?: return null
        return if (lengthM in SegmentConfig.MIN_LENGTH_M..SegmentConfig.MAX_LENGTH_M) start..end else null
    }

/** Results of a finished ride, updated when segments are added or removed (T11.7). */
public class ObserveRideResultsUseCase(
    private val efforts: BestEffortRepository,
    private val segments: SegmentRepository,
) {
    public operator fun invoke(activityId: Long): Flow<RideResults> =
        combine(efforts.observeRanked(activityId), segments.observeRanked(activityId)) { ranked, passed ->
            RideResults(
                efforts = ranked.filter { it.rank.isTopRank }.sortedWith(compareBy({ it.rank }, { it.effort.key })),
                // Stable sort: passes outside the top 3 keep their ride order.
                segments = passed.sortedBy { if (it.rank.isTopRank) it.rank else Int.MAX_VALUE },
            )
        }
}
