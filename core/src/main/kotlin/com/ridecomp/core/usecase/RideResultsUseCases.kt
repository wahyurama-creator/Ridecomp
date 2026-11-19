package com.ridecomp.core.usecase

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
