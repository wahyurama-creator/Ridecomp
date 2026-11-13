package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.RouteClimbEntity
import com.ridecomp.data.db.entity.RouteEntity
import com.ridecomp.data.db.entity.RoutePointEntity
import com.ridecomp.data.db.entity.RouteTurnEntity
import kotlinx.coroutines.flow.Flow

/** Rows that belong to one route, created once its id is known. */
class RouteChildren(
    val points: List<RoutePointEntity>,
    val climbs: List<RouteClimbEntity>,
    val turns: List<RouteTurnEntity>,
)

@Dao
interface RouteDao {
    @Query("SELECT * FROM ${DbNames.TABLE_ROUTES} ORDER BY created_at DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM ${DbNames.TABLE_ROUTES} WHERE id = :id")
    fun observe(id: Long): Flow<RouteEntity?>

    @Query("SELECT * FROM ${DbNames.TABLE_ROUTE_POINTS} WHERE route_id = :routeId ORDER BY seq ASC")
    suspend fun getPoints(routeId: Long): List<RoutePointEntity>

    @Query("SELECT * FROM ${DbNames.TABLE_ROUTE_CLIMBS} WHERE route_id = :routeId ORDER BY idx ASC")
    suspend fun getClimbs(routeId: Long): List<RouteClimbEntity>

    @Query("SELECT * FROM ${DbNames.TABLE_ROUTE_TURNS} WHERE route_id = :routeId ORDER BY dist_m ASC")
    suspend fun getTurns(routeId: Long): List<RouteTurnEntity>

    @Insert
    suspend fun insertRoute(route: RouteEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RoutePointEntity>)

    @Insert
    suspend fun insertClimbs(climbs: List<RouteClimbEntity>)

    @Insert
    suspend fun insertTurns(turns: List<RouteTurnEntity>)

    /** Route, points, climbs and turns together (DATA-03): never a route without its points. */
    @Transaction
    suspend fun insertWithChildren(route: RouteEntity, children: (Long) -> RouteChildren): Long {
        val id = insertRoute(route)
        val rows = children(id)
        insertPoints(rows.points)
        insertClimbs(rows.climbs)
        insertTurns(rows.turns)
        return id
    }

    @Query("UPDATE ${DbNames.TABLE_ROUTES} SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** Points, climbs and turns go with it (foreign keys with ON DELETE CASCADE). */
    @Query("DELETE FROM ${DbNames.TABLE_ROUTES} WHERE id = :id")
    suspend fun delete(id: Long)
}
