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

    @Upsert
    suspend fun upsert(setting: SettingEntity)

    @Query("DELETE FROM ${DbNames.TABLE_SETTINGS} WHERE `key` = :key")
    suspend fun delete(key: String)
}
