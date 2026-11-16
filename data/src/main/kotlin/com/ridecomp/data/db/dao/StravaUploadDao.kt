package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ridecomp.data.db.entity.StravaUploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StravaUploadDao {
    @Query("SELECT * FROM strava_uploads WHERE activity_id = :activityId")
    fun observe(activityId: Long): Flow<StravaUploadEntity?>

    @Query("SELECT * FROM strava_uploads WHERE activity_id = :activityId")
    suspend fun get(activityId: Long): StravaUploadEntity?

    @Upsert
    suspend fun upsert(entity: StravaUploadEntity)

    @Query("SELECT activity_id FROM strava_uploads WHERE state IN (:states)")
    fun observeIdsInStates(states: List<String>): Flow<List<Long>>

    @Query("SELECT * FROM strava_uploads WHERE state IN (:states) ORDER BY updated_at")
    suspend fun getInStates(states: List<String>): List<StravaUploadEntity>
}
