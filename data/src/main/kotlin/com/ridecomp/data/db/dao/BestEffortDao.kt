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
}
