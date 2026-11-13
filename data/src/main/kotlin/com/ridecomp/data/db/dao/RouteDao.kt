package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.RouteEntity
import com.ridecomp.data.db.entity.RoutePointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM ${DbNames.TABLE_ROUTES} ORDER BY created_at DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM ${DbNames.TABLE_ROUTES} WHERE id = :id")
    fun observe(id: Long): Flow<RouteEntity?>

    @Query("SELECT * FROM ${DbNames.TABLE_ROUTE_POINTS} WHERE route_id = :routeId ORDER BY seq ASC")
    suspend fun getPoints(routeId: Long): List<RoutePointEntity>

    @Insert
    suspend fun insertRoute(route: RouteEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RoutePointEntity>)

    /** Route and points together (DATA-03): never a route without its points. */
    @Transaction
    suspend fun insertWithPoints(route: RouteEntity, points: (Long) -> List<RoutePointEntity>): Long {
        val id = insertRoute(route)
        insertPoints(points(id))
        return id
    }

    @Query("UPDATE ${DbNames.TABLE_ROUTES} SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** Points, climbs and turns go with it (foreign keys with ON DELETE CASCADE). */
    @Query("DELETE FROM ${DbNames.TABLE_ROUTES} WHERE id = :id")
    suspend fun delete(id: Long)
}
