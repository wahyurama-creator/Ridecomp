package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.EventEntity

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM ${DbNames.TABLE_EVENTS} WHERE activity_id = :activityId ORDER BY t ASC, id ASC")
    suspend fun getForActivity(activityId: Long): List<EventEntity>
}
