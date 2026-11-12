package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT value FROM ${DbNames.TABLE_SETTINGS} WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM ${DbNames.TABLE_SETTINGS} WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM ${DbNames.TABLE_SETTINGS}")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM ${DbNames.TABLE_SETTINGS}")
    suspend fun getAll(): List<SettingEntity>

    @Upsert
    suspend fun upsert(setting: SettingEntity)

    @Upsert
    suspend fun upsertAll(settings: List<SettingEntity>)

    @Query("DELETE FROM ${DbNames.TABLE_SETTINGS} WHERE `key` = :key")
    suspend fun delete(key: String)
}
