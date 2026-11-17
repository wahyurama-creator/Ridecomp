package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.BikeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BikeDao {
    @Query("SELECT * FROM ${DbNames.TABLE_BIKES} ORDER BY id ASC")
    fun observeAll(): Flow<List<BikeEntity>>

    @Query("SELECT * FROM ${DbNames.TABLE_BIKES} WHERE id = :id")
    suspend fun get(id: Long): BikeEntity?

    @Insert
    suspend fun insert(bike: BikeEntity): Long

    @Update
    suspend fun update(bike: BikeEntity)

    @Query("DELETE FROM ${DbNames.TABLE_BIKES} WHERE id = :id")
    suspend fun delete(id: Long)
}
