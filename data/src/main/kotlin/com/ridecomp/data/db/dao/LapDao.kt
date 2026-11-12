package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.LapEntity

@Dao
interface LapDao {
    @Upsert
    suspend fun upsert(lap: LapEntity)

    @Query("SELECT * FROM ${DbNames.TABLE_LAPS} WHERE activity_id = :activityId ORDER BY lap_index ASC")
    suspend fun getForActivity(activityId: Long): List<LapEntity>
}
