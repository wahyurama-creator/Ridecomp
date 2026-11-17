package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.BikeComponentEntity
import com.ridecomp.data.db.entity.ComponentWearRow
import kotlinx.coroutines.flow.Flow

@Dao
interface BikeComponentDao {
    /** Re-emitted when components or rides change (Room tracks both tables). */
    @Query(
        "SELECT c.*, COALESCE((SELECT SUM(a.distance_m) FROM ${DbNames.TABLE_ACTIVITIES} a " +
            "WHERE a.bike_id = c.bike_id AND a.status = '${DbNames.ActivityStatus.FINISHED}' " +
            "AND a.started_at >= c.installed_at), 0) AS ridden_m " +
            "FROM ${DbNames.TABLE_BIKE_COMPONENTS} c ORDER BY c.bike_id, c.id",
    )
    fun observeWear(): Flow<List<ComponentWearRow>>

    @Query("SELECT * FROM ${DbNames.TABLE_BIKE_COMPONENTS} WHERE id = :id")
    suspend fun get(id: Long): BikeComponentEntity?

    @Insert
    suspend fun insert(component: BikeComponentEntity): Long

    @Update
    suspend fun update(component: BikeComponentEntity)

    @Query("DELETE FROM ${DbNames.TABLE_BIKE_COMPONENTS} WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT COALESCE(SUM(distance_m), 0) FROM ${DbNames.TABLE_ACTIVITIES} " +
            "WHERE bike_id = :bikeId AND status = '${DbNames.ActivityStatus.FINISHED}'",
    )
    fun observeBikeDistanceM(bikeId: Long): Flow<Double>
}
