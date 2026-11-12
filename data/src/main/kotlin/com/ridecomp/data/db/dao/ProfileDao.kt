package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM ${DbNames.TABLE_PROFILE} WHERE id = ${DbNames.SINGLE_PROFILE_ID}")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM ${DbNames.TABLE_PROFILE} WHERE id = ${DbNames.SINGLE_PROFILE_ID}")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}
