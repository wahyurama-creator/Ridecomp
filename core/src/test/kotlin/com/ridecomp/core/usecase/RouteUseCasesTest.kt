package com.ridecomp.core.usecase

import com.ridecomp.core.engine.route.GpxError
import com.ridecomp.core.engine.route.GpxPoint
import com.ridecomp.core.engine.route.PolylineSimplifier
import com.ridecomp.core.engine.route.RouteBuilder
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.TurnDirection
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeGpxDocumentSource
import com.ridecomp.core.testing.FakeRouteRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.util.Outcome
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class RouteUseCasesTest {
    private val repository = FakeRouteRepository()
    private val documents = FakeGpxDocumentSource()
    private val clock = FakeClock()

    /** ~1.11 km north per 0.01°, climbing 10 m then descending 3 m (below hysteresis) then +20 m. */
    private val gpx = """<gpx><trk><trkseg>
        <trkpt lat="0.00" lon="0"><ele>100</ele></trkpt>
        <trkpt lat="0.01" lon="0"><ele>110</ele></trkpt>
        <trkpt lat="0.02" lon="0"><ele>109</ele></trkpt>
        <trkpt lat="0.03" lon="0"><ele>130</ele></trkpt>
        </trkseg></trk></gpx>"""

    @Test
    fun `import builds distance and ascent and names the route from the file label`() = runTest {
        documents.documents["content://a"] = gpx
        documents.names["content://a"] = "Kaliurang.gpx"
        val import =
            ImportGpxUseCase(
                documents,
                repository,
                clock,
                TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            )

        val id = assertNotNull((import("content://a", "Route") as? Outcome.Success)?.value)

        val route = assertNotNull(repository.observe(id).first())
        assertEquals("Kaliurang", route.name)
        assertEquals("Kaliurang.gpx", route.sourceLabel)
        assertTrue(abs(route.distanceM - 3_335.8) < 5.0, "distance ${route.distanceM}")
        assertEquals(30.0, route.ascentM)
        assertEquals(4, repository.getPoints(id).size)

        documents.documents["content://bad"] = "<!DOCTYPE x><gpx/>"
        assertEquals(Outcome.Failure(GpxError.DOCTYPE_NOT_ALLOWED), import("content://bad", "Route"))
        assertEquals(Outcome.Failure(GpxError.MALFORMED), import("content://missing", "Route"))
    }

    @Test
    fun `reverse, rename and delete`() = runTest {
        documents.documents["u"] = gpx
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val id =
            assertNotNull(
                (ImportGpxUseCase(documents, repository, clock, dispatchers)("u", "Route") as? Outcome.Success)?.value,
            )

        val reversedId = assertNotNull(ReverseRouteUseCase(repository, clock, dispatchers)(id, "Route (reverse)"))
        val reversed = repository.getPoints(reversedId)
        assertEquals(0.03, reversed.first().latDeg)
        assertEquals(0.0, reversed.first().cumDistanceM)
        assertEquals(repository.getPoints(id).last().cumDistanceM, reversed.last().cumDistanceM, 1e-6)
        // Reversed: 130 → 109 → 110 → 100; the 1 m rise is below the 2 m hysteresis.
        assertEquals(0.0, assertNotNull(repository.observe(reversedId).first()).ascentM)

        RenameRouteUseCase(repository)(id, "  Merapi  ")
        assertEquals("Merapi", repository.observe(id).first()?.name)
        DeleteRouteUseCase(repository)(id)
        assertEquals(listOf(reversedId), ObserveRoutesUseCase(repository)().first().map { it.id })
    }

    @Test
    fun `simplification keeps corners and drops points on a straight line`() {
        val straight = (0..100).map { RoutePoint(it * 0.0001, 0.0, null, it * 11.0) }
        assertEquals(2, PolylineSimplifier.simplify(straight).size)

        val corner = straight.take(50) + (1..50).map { RoutePoint(49 * 0.0001, it * 0.0001, null, 0.0) }
        val simplified = PolylineSimplifier.simplify(corner)
        assertEquals(3, simplified.size)
        assertEquals(corner[49], simplified[1])
        assertEquals(0.0, RouteBuilder.ascent(emptyList()))
    }

    @Test
    fun `routes saved without climbs and turns get them detected when read`() = runTest {
        // ~600 m north then ~600 m east in 0.0001° (~11 m) steps: one right turn, no elevation.
        val points =
            (0..54).map { GpxPoint(it * 0.0001, 0.0, null) } + (1..54).map { GpxPoint(0.0054, it * 0.0001, null) }
        val legacyId = repository.save(RouteBuilder.build(points, "Old", null, 0))
        val guidance =
            GetRouteGuidanceUseCase(repository, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))

        val detected = guidance(legacyId)

        assertTrue(detected.climbs.isEmpty())
        assertEquals(TurnDirection.RIGHT, detected.turns.single().direction)
        // Stored detections are used as they are.
        val storedId = repository.save(
            RouteBuilder.build(points, "New", null, 0).withDetections(),
        )
        assertEquals(guidance(storedId), detected)
    }
}
