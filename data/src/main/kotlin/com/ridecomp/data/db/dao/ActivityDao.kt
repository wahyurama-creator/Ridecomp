package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

/** Queries on `activities`. Lists are always paged (DATA-04); all calls are suspend/Flow (DATA-01). */
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

    /** Deletes the ride; track points, events and laps follow through `ON DELETE CASCADE`. */
    @Query("DELETE FROM ${DbNames.TABLE_ACTIVITIES} WHERE id = :id")
    suspend fun deleteById(id: Long)
}
