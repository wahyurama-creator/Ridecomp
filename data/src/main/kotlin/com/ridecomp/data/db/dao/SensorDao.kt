package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.SensorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    @Query("SELECT * FROM ${DbNames.TABLE_SENSORS} ORDER BY created_at ASC")
    fun observeAll(): Flow<List<SensorEntity>>

    @Query("SELECT * FROM ${DbNames.TABLE_SENSORS} WHERE auto_connect = 1 ORDER BY created_at ASC")
    suspend fun getAutoConnect(): List<SensorEntity>

    @Query("SELECT * FROM ${DbNames.TABLE_SENSORS} WHERE remote_id = :remoteId")
    suspend fun getByAddress(remoteId: String): SensorEntity?

    @Upsert
    suspend fun upsert(sensor: SensorEntity)

    @Query("UPDATE ${DbNames.TABLE_SENSORS} SET last_battery = :percent WHERE remote_id = :remoteId")
    suspend fun updateBattery(remoteId: String, percent: Int)

    @Query("DELETE FROM ${DbNames.TABLE_SENSORS} WHERE remote_id = :remoteId")
    suspend fun delete(remoteId: String)
}
