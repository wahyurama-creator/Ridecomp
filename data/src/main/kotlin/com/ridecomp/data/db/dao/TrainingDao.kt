package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.DailyTrainingEntity
import kotlinx.coroutines.flow.Flow

data class DatedLoadRow(val startedAt: Long, val load: Double)

data class TrainingTotalsRow(val rides: Int, val distanceM: Double, val movingS: Long, val load: Double)

/** Ride loads on `activities` and the `daily_training` trend (T4.4). */
@Dao
interface TrainingDao {
    @Query(
        "UPDATE ${DbNames.TABLE_ACTIVITIES} SET training_load = :load, load_method = :method, " +
            "intensity_factor = :intensityFactor WHERE id = :activityId",
    )
    suspend fun setRideLoad(activityId: Long, load: Double?, method: String?, intensityFactor: Double?)

    @Query(
        "SELECT started_at AS startedAt, training_load AS load FROM ${DbNames.TABLE_ACTIVITIES} " +
            "WHERE status = '${DbNames.ActivityStatus.FINISHED}' AND training_load IS NOT NULL " +
            "AND started_at >= :sinceMs",
    )
    suspend fun loadsSince(sinceMs: Long): List<DatedLoadRow>

    @Query("SELECT * FROM ${DbNames.TABLE_DAILY_TRAINING} WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun latestDayBefore(date: String): DailyTrainingEntity?

    @Query("DELETE FROM ${DbNames.TABLE_DAILY_TRAINING} WHERE date >= :from")
    suspend fun deleteFrom(from: String)

    @Insert
    suspend fun insertAll(days: List<DailyTrainingEntity>)

    /** Dates are `yyyy-MM-dd`, so text order is date order. */
    @Transaction
    suspend fun replaceFrom(from: String, days: List<DailyTrainingEntity>) {
        deleteFrom(from)
        insertAll(days)
    }

    @Query("SELECT * FROM ${DbNames.TABLE_DAILY_TRAINING} WHERE date >= :from ORDER BY date ASC")
    fun observeDays(from: String): Flow<List<DailyTrainingEntity>>

    @Query(
        "SELECT COUNT(*) AS rides, COALESCE(SUM(distance_m), 0) AS distanceM, " +
            "COALESCE(SUM(moving_s), 0) AS movingS, COALESCE(SUM(training_load), 0) AS load " +
            "FROM ${DbNames.TABLE_ACTIVITIES} " +
            "WHERE status = '${DbNames.ActivityStatus.FINISHED}' AND started_at >= :sinceMs",
    )
    fun observeTotalsSince(sinceMs: Long): Flow<TrainingTotalsRow>

    @Query(
        "SELECT DISTINCT load_method FROM ${DbNames.TABLE_ACTIVITIES} " +
            "WHERE status = '${DbNames.ActivityStatus.FINISHED}' AND load_method IS NOT NULL " +
            "AND started_at >= :sinceMs",
    )
    fun observeMethodsSince(sinceMs: Long): Flow<List<String>>

    @Query(
        "SELECT id FROM ${DbNames.TABLE_ACTIVITIES} " +
            "WHERE status = '${DbNames.ActivityStatus.FINISHED}' AND started_at >= :sinceMs ORDER BY started_at",
    )
    suspend fun rideIdsSince(sinceMs: Long): List<Long>
}
