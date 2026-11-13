package com.ridecomp.data.repository

import com.ridecomp.core.engine.route.NewRoute
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.RouteTurn
import com.ridecomp.core.engine.route.TurnDirection
import com.ridecomp.core.engine.route.TurnSeverity
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.data.db.dao.RouteChildren
import com.ridecomp.data.db.dao.RouteDao
import com.ridecomp.data.db.entity.RouteClimbEntity
import com.ridecomp.data.db.entity.RouteEntity
import com.ridecomp.data.db.entity.RoutePointEntity
import com.ridecomp.data.db.entity.RouteTurnEntity
import com.ridecomp.data.mapper.toDbValue
import com.ridecomp.data.mapper.toEnumOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException

/** Room implementation of [RouteRepository]. */
class RouteRepositoryImpl(private val dao: RouteDao) : RouteRepository {
    override fun observeAll(): Flow<List<Route>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Route?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun getPoints(id: Long): List<RoutePoint> =
        dao.getPoints(id).map { RoutePoint(it.lat, it.lon, it.ele, it.cumDistM) }

    override suspend fun getClimbs(id: Long): List<RouteClimb> = dao.getClimbs(id).map { it.toDomain() }

    /** Unknown direction/severity values (newer app version) are skipped, never guessed. */
    override suspend fun getTurns(id: Long): List<RouteTurn> = dao.getTurns(id).mapNotNull { it.toDomain() }

    override suspend fun save(route: NewRoute): Long = dao.insertWithChildren(
        RouteEntity(
            name = route.name,
            sourceFile = route.sourceLabel,
            distanceM = route.distanceM,
            ascentM = route.ascentM,
            createdAt = route.createdAtMs,
        ),
    ) { id ->
        RouteChildren(
            points = route.points.mapIndexed { seq, p ->
                RoutePointEntity(id, seq, p.latDeg, p.lonDeg, p.elevationM, p.cumDistanceM)
            },
            climbs = route.climbs.map { it.toEntity(id) },
            turns = route.turns.map { it.toEntity(id) },
        )
    }

    override suspend fun rename(id: Long, name: String) {
        dao.rename(id, name)
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    private fun RouteEntity.toDomain() = Route(id, name, sourceFile, distanceM, ascentM, createdAt)

    private fun RouteClimb.toEntity(routeId: Long) = RouteClimbEntity(
        routeId = routeId,
        idx = index,
        startDistM = startDistanceM,
        endDistM = endDistanceM,
        lengthM = lengthM,
        gainM = gainM,
        avgGrade = avgGradePercent,
        maxGrade = maxGradePercent,
        gradeProfileJson = JSONArray(gradeProfilePercent).toString(),
    )

    private fun RouteClimbEntity.toDomain() = RouteClimb(
        index = idx,
        startDistanceM = startDistM,
        endDistanceM = endDistM,
        gainM = gainM,
        avgGradePercent = avgGrade,
        maxGradePercent = maxGrade,
        gradeProfilePercent = gradeProfileJson.toGradeProfile(),
    )

    private fun RouteTurn.toEntity(routeId: Long) = RouteTurnEntity(
        routeId = routeId,
        distM = distanceM,
        angleDeg = angleDeg,
        direction = direction.toDbValue(),
        severity = severity.toDbValue(),
        source = SOURCE_GEOMETRY,
    )

    private fun RouteTurnEntity.toDomain(): RouteTurn? {
        val turnDirection = direction.toEnumOrNull<TurnDirection>()
        val turnSeverity = severity.toEnumOrNull<TurnSeverity>()
        return if (turnDirection == null ||
            turnSeverity == null
        ) {
            null
        } else {
            RouteTurn(distM, angleDeg, turnDirection, turnSeverity)
        }
    }

    /** A damaged profile only loses the coloured profile, not the climb. */
    private fun String.toGradeProfile(): List<Double> = try {
        val array = JSONArray(this)
        List(array.length()) { array.getDouble(it) }
    } catch (_: JSONException) {
        emptyList()
    }

    private companion object {
        /** `route_turns.source`: detected from geometry (§7.1); GPX cue waypoints are not used yet. */
        const val SOURCE_GEOMETRY = "geometry"
    }
}
