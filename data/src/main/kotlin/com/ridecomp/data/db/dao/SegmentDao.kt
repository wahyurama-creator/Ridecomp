package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.SegmentEffortEntity
import com.ridecomp.data.db.entity.SegmentEntity
import com.ridecomp.data.db.entity.SegmentPointEntity
import kotlinx.coroutines.flow.Flow

/** A segment with its best time and effort count (segment list). */
data class SegmentSummaryRow(@Embedded val segment: SegmentEntity, val bestElapsedS: Double?, val effortCount: Int)

/** An effort with its ride's name and start (segment detail). */
data class SegmentEffortRow(@Embedded val effort: SegmentEffortEntity, val activityName: String, val startedAt: Long)

/** A ride's pass over a segment with the segment's name and length and its rank (T11.7). */
data class RankedSegmentEffortRow(
    @Embedded val effort: SegmentEffortEntity,
    val segmentName: String,
    val lengthM: Double,
    val rank: Int,
)

@Dao
interface SegmentDao {
    @Insert
    suspend fun insert(segment: SegmentEntity): Long

    @Insert
    suspend fun insertPoints(points: List<SegmentPointEntity>)

    /** A segment and its points in one transaction. */
    @Transaction
    suspend fun insertWithPoints(segment: SegmentEntity, points: (Long) -> List<SegmentPointEntity>): Long {
        val id = insert(segment)
        insertPoints(points(id))
        return id
    }

    /** Points and efforts follow through `ON DELETE CASCADE`. */
    @Query("DELETE FROM ${DbNames.TABLE_SEGMENTS} WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT s.*, MIN(e.elapsed_s) AS bestElapsedS, COUNT(e.id) AS effortCount " +
            "FROM ${DbNames.TABLE_SEGMENTS} s LEFT JOIN ${DbNames.TABLE_SEGMENT_EFFORTS} e ON e.segment_id = s.id " +
            "GROUP BY s.id ORDER BY s.created_at DESC",
    )
    fun observeSummaries(): Flow<List<SegmentSummaryRow>>

    @Query("SELECT * FROM ${DbNames.TABLE_SEGMENTS} WHERE id = :id")
    fun observe(id: Long): Flow<SegmentEntity?>

    @Query("SELECT * FROM ${DbNames.TABLE_SEGMENTS}")
    suspend fun getAll(): List<SegmentEntity>

    @Query("SELECT * FROM ${DbNames.TABLE_SEGMENT_POINTS} WHERE segment_id = :segmentId ORDER BY seq")
    suspend fun getPoints(segmentId: Long): List<SegmentPointEntity>

    @Query(
        "SELECT e.*, a.name AS activityName, a.started_at AS startedAt FROM ${DbNames.TABLE_SEGMENT_EFFORTS} e " +
            "JOIN ${DbNames.TABLE_ACTIVITIES} a ON a.id = e.activity_id WHERE e.segment_id = :segmentId " +
            "ORDER BY e.elapsed_s ASC, a.started_at ASC",
    )
    fun observeEfforts(segmentId: Long): Flow<List<SegmentEffortRow>>

    @Query(
        "SELECT MIN(e.elapsed_s) FROM ${DbNames.TABLE_SEGMENT_EFFORTS} e " +
            "JOIN ${DbNames.TABLE_ACTIVITIES} a ON a.id = e.activity_id " +
            "WHERE e.segment_id = :segmentId AND a.started_at < :beforeStartedAt",
    )
    suspend fun bestBefore(segmentId: Long, beforeStartedAt: Long): Double?

    @Query("SELECT COUNT(*) FROM ${DbNames.TABLE_SEGMENT_EFFORTS} WHERE activity_id = :activityId AND is_pr = 1")
    suspend fun countRecords(activityId: Long): Int

    /** Passes of a ride in ride order; rank = 1 + passes of earlier rides as fast or faster. */
    @Query(
        "SELECT e.*, s.name AS segmentName, s.length_m AS lengthM, 1 + (" +
            "SELECT COUNT(*) FROM ${DbNames.TABLE_SEGMENT_EFFORTS} o " +
            "JOIN ${DbNames.TABLE_ACTIVITIES} oa ON oa.id = o.activity_id " +
            "WHERE o.segment_id = e.segment_id AND oa.started_at < a.started_at AND o.elapsed_s <= e.elapsed_s" +
            ") AS `rank` FROM ${DbNames.TABLE_SEGMENT_EFFORTS} e " +
            "JOIN ${DbNames.TABLE_SEGMENTS} s ON s.id = e.segment_id " +
            "JOIN ${DbNames.TABLE_ACTIVITIES} a ON a.id = e.activity_id " +
            "WHERE e.activity_id = :activityId ORDER BY e.start_t",
    )
    fun observeRanked(activityId: Long): Flow<List<RankedSegmentEffortRow>>

    @Query("DELETE FROM ${DbNames.TABLE_SEGMENT_EFFORTS} WHERE activity_id = :activityId")
    suspend fun deleteEffortsForActivity(activityId: Long)

    @Insert
    suspend fun insertEfforts(efforts: List<SegmentEffortEntity>)

    @Transaction
    suspend fun replaceEffortsForActivity(activityId: Long, efforts: List<SegmentEffortEntity>) {
        deleteEffortsForActivity(activityId)
        insertEfforts(efforts)
    }
}
