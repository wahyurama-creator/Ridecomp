package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.ActivityProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityProfileDao {
    @Query("SELECT * FROM ${DbNames.TABLE_ACTIVITY_PROFILES} ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<ActivityProfileEntity>>

    @Query("SELECT * FROM ${DbNames.TABLE_ACTIVITY_PROFILES} WHERE id = :id")
    suspend fun get(id: Long): ActivityProfileEntity?

    @Insert
    suspend fun insert(profile: ActivityProfileEntity): Long

    @Update
    suspend fun update(profile: ActivityProfileEntity)

    @Query("DELETE FROM ${DbNames.TABLE_ACTIVITY_PROFILES} WHERE id = :id")
    suspend fun delete(id: Long)
}
