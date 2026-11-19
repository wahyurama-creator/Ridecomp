package com.ridecomp.core.repository

import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.model.ActivityKind
import kotlinx.coroutines.flow.Flow

/** One effort of a ride and whether it was a personal record when the ride was analyzed. */
public data class RideEffort(val effort: BestEffort, val isPr: Boolean)

/**
 * One effort of a ride and its place among the same record of earlier rides of its kind: 1 = record when the
 * ride was done, 2 = one earlier ride was as good or better (T11.7).
 */
public data class RankedEffort(val effort: BestEffort, val rank: Int)

/** The current best of a record for one profile kind, with the ride that set it (Records tab). */
public data class PersonalRecord(
    val key: BestEffortKey,
    val value: Double,
    val activityId: Long,
    val activityName: String,
    val startedAtMs: Long,
)

/**
 * Best efforts per ride (05-DATA-MODEL `best_efforts`, 12-FEATURE-SPECS §8). Records are kept per
 * [ActivityKind] so indoor rides never mix with outdoor ones. Implemented in :data (ARCH-03).
 */
public interface BestEffortRepository {
    /** Best value of each record among rides of [kind] started before [beforeStartedAtMs]. */
    public suspend fun bestsBefore(kind: ActivityKind, beforeStartedAtMs: Long): Map<BestEffortKey, Double>

    /** Replaces the efforts of a ride in one transaction (re-analysis is idempotent). */
    public suspend fun replace(activityId: Long, kind: ActivityKind, efforts: List<RideEffort>)

    /** Current personal records of [kind], one per record key. */
    public fun observeRecords(kind: ActivityKind): Flow<List<PersonalRecord>>

    /** Profile kinds that have at least one record. */
    public fun observeKinds(): Flow<List<ActivityKind>>

    /** Records a ride set when it was analyzed (share card "2 PR BARU"). */
    public suspend fun countRecords(activityId: Long): Int

    /** Best value of [key] over rides started since [sinceStartedAtMs], any kind (FTP estimate, T11.6). */
    public suspend fun bestSince(key: BestEffortKey, sinceStartedAtMs: Long): Double?

    /** Efforts of a ride, each ranked against rides of its kind started before it (T11.7). */
    public fun observeRanked(activityId: Long): Flow<List<RankedEffort>>
}
