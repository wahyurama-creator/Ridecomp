package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.EmergencyContactEntity
import com.ridecomp.data.db.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM ${DbNames.TABLE_EMERGENCY_CONTACTS} ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM ${DbNames.TABLE_EMERGENCY_CONTACTS} ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<EmergencyContactEntity>

    @Insert
    suspend fun insert(contact: EmergencyContactEntity): Long

    @Query("DELETE FROM ${DbNames.TABLE_EMERGENCY_CONTACTS} WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface IncidentDao {
    @Insert
    suspend fun insert(incident: IncidentEntity): Long

    @Upsert
    suspend fun upsert(incident: IncidentEntity)

    @Query("SELECT * FROM ${DbNames.TABLE_INCIDENTS} WHERE activity_id = :activityId ORDER BY detected_t ASC")
    suspend fun getForActivity(activityId: Long): List<IncidentEntity>
}
