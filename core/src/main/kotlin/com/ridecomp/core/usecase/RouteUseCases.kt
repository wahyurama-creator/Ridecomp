package com.ridecomp.core.usecase

import com.ridecomp.core.engine.route.ClimbDetector
import com.ridecomp.core.engine.route.GpxError
import com.ridecomp.core.engine.route.GpxParser
import com.ridecomp.core.engine.route.NewRoute
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.route.RouteBuilder
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RouteConfig
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.RouteTurn
import com.ridecomp.core.engine.route.TurnDetector
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.GpxDocumentSource
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Imports a GPX file (T3.1): parse safely (SEC-IN-01/02), build distances and ascent, save. The name comes
 * from the file content, else the file name label, else [fallbackName]; it is text only (SEC-IN-04).
 */
public class ImportGpxUseCase(
    private val documents: GpxDocumentSource,
    private val routes: RouteRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(uri: String, fallbackName: String): Outcome<Long, GpxError> =
        withContext(dispatchers.io) {
            val parsed = try {
                documents.open(uri).use(GpxParser::parse)
            } catch (_: IOException) {
                GpxError.MALFORMED.asFailure()
            } catch (_: SecurityException) {
                // The document provider revoked access.
                GpxError.MALFORMED.asFailure()
            }
            when (parsed) {
                is Outcome.Failure -> parsed
                is Outcome.Success -> {
                    val label = documents.displayName(uri)
                    val name = parsed.value.name ?: label?.substringBeforeLast('.')?.ifBlank { null } ?: fallbackName
                    // Simplification and detection are CPU work.
                    val route = withContext(dispatchers.default) {
                        RouteBuilder.build(parsed.value.points, name, label, clock.nowMs()).withDetections()
                    }
                    routes.save(route).asSuccess()
                }
            }
        }
}

public class ObserveRoutesUseCase(private val routes: RouteRepository) {
    public operator fun invoke(): Flow<List<Route>> = routes.observeAll()
}

public class ObserveRouteUseCase(private val routes: RouteRepository) {
    public operator fun invoke(id: Long): Flow<Route?> = routes.observe(id)
}

public class GetRoutePointsUseCase(private val routes: RouteRepository) {
    public suspend operator fun invoke(id: Long): List<RoutePoint> = routes.getPoints(id)
}

/** Climbs and turns of a route, in route order (T3.11, T3.12). */
public data class RouteGuidance(val climbs: List<RouteClimb>, val turns: List<RouteTurn>)

/**
 * Climbs and turns stored at import. Routes imported before T3.11 have none stored: they are detected
 * from the points now (CPU work, off the caller's thread). A route that truly has neither is detected
 * again on each call, which is cheap and gives the same empty result.
 */
public class GetRouteGuidanceUseCase(private val routes: RouteRepository, private val dispatchers: DispatcherProvider) {
    public suspend operator fun invoke(id: Long): RouteGuidance {
        val climbs = routes.getClimbs(id)
        val turns = routes.getTurns(id)
        if (climbs.isNotEmpty() || turns.isNotEmpty()) return RouteGuidance(climbs, turns)
        val points = routes.getPoints(id)
        return withContext(dispatchers.default) {
            RouteGuidance(ClimbDetector.detect(points), TurnDetector.detect(points))
        }
    }
}

public class RenameRouteUseCase(private val routes: RouteRepository) {
    public suspend operator fun invoke(id: Long, name: String) {
        val trimmed = name.trim().take(RouteConfig.MAX_NAME_LENGTH)
        if (trimmed.isNotEmpty()) routes.rename(id, trimmed)
    }
}

public class DeleteRouteUseCase(private val routes: RouteRepository) {
    public suspend operator fun invoke(id: Long) {
        routes.delete(id)
    }
}

/** "Balik arah" (T3.2): saves the route reversed as a new route named [reversedName]. */
public class ReverseRouteUseCase(
    private val routes: RouteRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(id: Long, reversedName: String): Long? {
        val points = routes.getPoints(id)
        if (points.size < 2) return null
        // Called from the UI: reversing and detection run off the main thread.
        val route = withContext(dispatchers.default) {
            val reversed = RouteBuilder.reversed(points)
            NewRoute(
                name = reversedName.trim().take(RouteConfig.MAX_NAME_LENGTH),
                sourceLabel = null,
                points = reversed,
                distanceM = reversed.last().cumDistanceM,
                ascentM = RouteBuilder.ascent(reversed.mapNotNull { it.elevationM }),
                createdAtMs = clock.nowMs(),
            ).withDetections()
        }
        return routes.save(route)
    }
}
