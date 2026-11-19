package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.BestEffortEntity
import kotlinx.coroutines.flow.Flow

/** Lowest and highest value of one record key (the better one depends on the key). */
data class EffortRangeRow(val key: String, val minValue: Double, val maxValue: Double)

/** A record row with the ride that set it. */
data class RecordRow(
    val key: String,
    val value: Double,
    val activityId: Long,
    val activityName: String,
    val startedAt: Long,
)

/** An effort of a ride with its rank among earlier rides of the same kind (T11.7). */
data class RankedEffortRow(val key: String, val value: Double, val startT: Long?, val endT: Long?, val rank: Int)

@Dao
interface BestEffortDao {
    @Insert
    suspend fun insertAll(efforts: List<BestEffortEntity>)

    @Query("DELETE FROM ${DbNames.TABLE_BEST_EFFORTS} WHERE activity_id = :activityId")
    suspend fun deleteForActivity(activityId: Long)

    /** Re-analysis replaces a ride's rows atomically. */
    @Transaction
    suspend fun replace(activityId: Long, efforts: List<BestEffortEntity>) {
        deleteForActivity(activityId)
        insertAll(efforts)
    }

    @Query(
        "SELECT b.`key` AS `key`, MIN(b.value) AS minValue, MAX(b.value) AS maxValue " +
            "FROM ${DbNames.TABLE_BEST_EFFORTS} b JOIN ${DbNames.TABLE_ACTIVITIES} a ON a.id = b.activity_id " +
            "WHERE b.profile_kind = :kind AND a.started_at < :beforeStartedAt GROUP BY b.`key`",
    )
    suspend fun rangesBefore(kind: String, beforeStartedAt: Long): List<EffortRangeRow>

    /**
     * Rows holding the best value of their key for [kind]: the minimum for [lowerIsBetterType] (time over a
     * distance), the maximum otherwise. Ties return several rows; the caller keeps the earliest ride.
     */
    @Query(
        "SELECT b.`key` AS `key`, b.value AS value, b.activity_id AS activityId, a.name AS activityName, " +
            "a.started_at AS startedAt FROM ${DbNames.TABLE_BEST_EFFORTS} b " +
            "JOIN ${DbNames.TABLE_ACTIVITIES} a ON a.id = b.activity_id " +
            "WHERE b.profile_kind = :kind AND b.value = (" +
            "SELECT CASE WHEN b.type = :lowerIsBetterType THEN MIN(o.value) ELSE MAX(o.value) END " +
            "FROM ${DbNames.TABLE_BEST_EFFORTS} o WHERE o.profile_kind = b.profile_kind AND o.`key` = b.`key`) " +
            "ORDER BY a.started_at ASC",
    )
    fun observeRecords(kind: String, lowerIsBetterType: String): Flow<List<RecordRow>>

    @Query("SELECT DISTINCT profile_kind FROM ${DbNames.TABLE_BEST_EFFORTS}")
    fun observeKinds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM ${DbNames.TABLE_BEST_EFFORTS} WHERE activity_id = :activityId AND is_pr = 1")
    suspend fun countRecords(activityId: Long): Int

    /** Highest value of [key] over rides started since [sinceStartedAt] (power keys: higher is better). */
    @Query(
        "SELECT MAX(b.value) FROM ${DbNames.TABLE_BEST_EFFORTS} b " +
            "JOIN ${DbNames.TABLE_ACTIVITIES} a ON a.id = b.activity_id " +
            "WHERE b.`key` = :key AND a.started_at >= :sinceStartedAt",
    )
    suspend fun maxSince(key: String, sinceStartedAt: Long): Double?

    /**
     * Efforts of a ride with their rank: 1 + earlier rides of the same kind as good or better (lower for
     * [lowerIsBetterType], higher otherwise), so rank 1 matches `is_pr` and a tie is not a record.
     */
    @Query(
        "SELECT b.`key` AS `key`, b.value AS value, b.start_t AS startT, b.end_t AS endT, 1 + (" +
            "SELECT COUNT(*) FROM ${DbNames.TABLE_BEST_EFFORTS} o " +
            "JOIN ${DbNames.TABLE_ACTIVITIES} oa ON oa.id = o.activity_id " +
            "WHERE o.profile_kind = b.profile_kind AND o.`key` = b.`key` AND oa.started_at < a.started_at " +
            "AND CASE WHEN b.type = :lowerIsBetterType THEN o.value <= b.value ELSE o.value >= b.value END" +
            ") AS `rank` FROM ${DbNames.TABLE_BEST_EFFORTS} b " +
            "JOIN ${DbNames.TABLE_ACTIVITIES} a ON a.id = b.activity_id WHERE b.activity_id = :activityId",
    )
    fun observeRanked(activityId: Long, lowerIsBetterType: String): Flow<List<RankedEffortRow>>
}
