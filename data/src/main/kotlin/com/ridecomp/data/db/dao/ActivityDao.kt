package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

/** Queries on `activities`. Lists are always paged (DATA-04); all calls are suspend/Flow (DATA-01). */
/** Row of [ActivityDao.observeTotalsSince]. */
data class PeriodTotalsRow(val rides: Int, val distanceM: Double, val ascentM: Double)

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM ${DbNames.TABLE_ACTIVITIES} WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntity?

    @Query("SELECT * FROM ${DbNames.TABLE_ACTIVITIES} WHERE id = :id")
    fun observeById(id: Long): Flow<ActivityEntity?>

    /** Rides interrupted by a crash/kill, offered for recovery on app start (03-ARCHITECTURE §4.5). */
    @Query(
        "SELECT * FROM ${DbNames.TABLE_ACTIVITIES} " +
            "WHERE status IN ('${DbNames.ActivityStatus.RECORDING}', '${DbNames.ActivityStatus.PAUSED}') " +
            "ORDER BY started_at DESC",
    )
    suspend fun getUnfinished(): List<ActivityEntity>

    /** One page of saved rides, newest first. */
    @Query(
        "SELECT * FROM ${DbNames.TABLE_ACTIVITIES} WHERE status = '${DbNames.ActivityStatus.FINISHED}' " +
            "ORDER BY started_at DESC LIMIT :limit OFFSET :offset",
    )
    fun observeFinishedPage(limit: Int, offset: Int): Flow<List<ActivityEntity>>

    @Query("SELECT COUNT(*) FROM ${DbNames.TABLE_ACTIVITIES} WHERE status = '${DbNames.ActivityStatus.FINISHED}'")
    fun observeFinishedCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) AS rides, COALESCE(SUM(distance_m), 0) AS distanceM, " +
            "COALESCE(SUM(ascent_m), 0) AS ascentM FROM ${DbNames.TABLE_ACTIVITIES} " +
            "WHERE status = '${DbNames.ActivityStatus.FINISHED}' AND started_at >= :sinceMs",
    )
    fun observeTotalsSince(sinceMs: Long): Flow<PeriodTotalsRow>

    /** Saved rides analyzed below [version], oldest first (D-67). */
    @Query(
        "SELECT * FROM ${DbNames.TABLE_ACTIVITIES} WHERE status = '${DbNames.ActivityStatus.FINISHED}' " +
            "AND analysis_version < :version ORDER BY started_at ASC LIMIT :limit",
    )
    suspend fun getPendingAnalysis(version: Int, limit: Int): List<ActivityEntity>

    @Query(
        "UPDATE ${DbNames.TABLE_ACTIVITIES} SET analysis_version = :version " +
            "WHERE id = :id AND analysis_version = :expectedVersion",
    )
    suspend fun setAnalysisVersion(id: Long, version: Int, expectedVersion: Int)

    @Query("UPDATE ${DbNames.TABLE_ACTIVITIES} SET analysis_version = :version WHERE analysis_version > :version")
    suspend fun lowerAnalysisVersion(version: Int)

    /** Deletes the ride; track points, events and laps follow through `ON DELETE CASCADE`. */
    @Query("DELETE FROM ${DbNames.TABLE_ACTIVITIES} WHERE id = :id")
    suspend fun deleteById(id: Long)
}
