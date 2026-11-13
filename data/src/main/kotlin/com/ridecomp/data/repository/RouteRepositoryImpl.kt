package com.ridecomp.data.repository

import com.ridecomp.core.engine.route.NewRoute
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.data.db.dao.RouteDao
import com.ridecomp.data.db.entity.RouteEntity
import com.ridecomp.data.db.entity.RoutePointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room implementation of [RouteRepository]. */
class RouteRepositoryImpl(private val dao: RouteDao) : RouteRepository {
    override fun observeAll(): Flow<List<Route>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Route?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun getPoints(id: Long): List<RoutePoint> =
        dao.getPoints(id).map { RoutePoint(it.lat, it.lon, it.ele, it.cumDistM) }

    override suspend fun save(route: NewRoute): Long = dao.insertWithPoints(
        RouteEntity(
            name = route.name,
            sourceFile = route.sourceLabel,
            distanceM = route.distanceM,
            ascentM = route.ascentM,
            createdAt = route.createdAtMs,
        ),
    ) { id ->
        route.points.mapIndexed { seq, p ->
            RoutePointEntity(id, seq, p.latDeg, p.lonDeg, p.elevationM, p.cumDistanceM)
        }
    }

    override suspend fun rename(id: Long, name: String) {
        dao.rename(id, name)
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    private fun RouteEntity.toDomain() = Route(id, name, sourceFile, distanceM, ascentM, createdAt)
}
