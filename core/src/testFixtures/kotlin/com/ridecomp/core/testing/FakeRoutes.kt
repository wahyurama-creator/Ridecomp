package com.ridecomp.core.testing

import com.ridecomp.core.engine.route.NewRoute
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.RouteTurn
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.core.source.GpxDocumentSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

public class FakeRouteRepository : RouteRepository {
    private val routes = MutableStateFlow<Map<Long, Pair<Route, List<RoutePoint>>>>(emptyMap())
    private val details = HashMap<Long, NewRoute>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<Route>> = routes.map { all ->
        all.values.map { it.first }.sortedByDescending { it.createdAtMs }
    }

    override fun observe(id: Long): Flow<Route?> = routes.map { it[id]?.first }

    override suspend fun getPoints(id: Long): List<RoutePoint> = routes.value[id]?.second.orEmpty()

    override suspend fun save(route: NewRoute): Long {
        val id = nextId++
        val saved = Route(id, route.name, route.sourceLabel, route.distanceM, route.ascentM, route.createdAtMs)
        routes.value = routes.value + (id to (saved to route.points))
        details[id] = route
        return id
    }

    override suspend fun getClimbs(id: Long): List<RouteClimb> = details[id]?.climbs.orEmpty()

    override suspend fun getTurns(id: Long): List<RouteTurn> = details[id]?.turns.orEmpty()

    override suspend fun rename(id: Long, name: String) {
        val (route, points) = routes.value[id] ?: return
        routes.value = routes.value + (id to (route.copy(name = name) to points))
    }

    override suspend fun delete(id: Long) {
        routes.value = routes.value - id
    }
}

/** Documents are strings keyed by uri. */
public class FakeGpxDocumentSource(public val documents: MutableMap<String, String> = mutableMapOf()) :
    GpxDocumentSource {
    public val names: MutableMap<String, String> = mutableMapOf()

    override fun open(uri: String): InputStream =
        ByteArrayInputStream((documents[uri] ?: throw java.io.FileNotFoundException(uri)).toByteArray())

    override fun displayName(uri: String): String? = names[uri]
}
