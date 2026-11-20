package com.ridecomp.core.usecase

import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.engine.map.MapBounds
import com.ridecomp.core.engine.segments.Segment
import com.ridecomp.core.engine.segments.SegmentMatch
import com.ridecomp.core.engine.segments.SegmentPoint
import com.ridecomp.core.engine.segments.SegmentSource
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.repository.SegmentEffort
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.FakeSegmentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The "Results" card (T11.7): top-3 best efforts at the time of the ride and every segment passed. */
class RideResultsUseCasesTest {
    private val activities = FakeActivityRepository()
    private val efforts = FakeBestEffortRepository(activities)
    private val segments = FakeSegmentRepository(activities)
    private val observe = ObserveRideResultsUseCase(efforts, segments)

    private fun ride(id: Long) {
        activities.put(
            Activity(
                id = id,
                uuid = "ride-$id",
                name = "Ride $id",
                status = ActivityStatus.FINISHED,
                startedAtMs = id * DAY_MS,
                endedAtMs = id * DAY_MS,
                totals = RideTotals(),
                lastCheckpointAtMs = id * DAY_MS,
            ),
        )
    }

    private suspend fun fiveKm(id: Long, seconds: Double) {
        efforts.replace(
            id,
            ActivityKind.ROAD,
            listOf(RideEffort(BestEffort(BestEffortKey.FASTEST_5_KM, seconds, null, null), false)),
        )
    }

    private suspend fun segment(name: String): Long = segments.create(
        Segment(
            id = 0,
            name = name,
            lengthM = SEGMENT_M,
            gainM = 0.0,
            avgGradePercent = 0.0,
            points = listOf(SegmentPoint(0.0, 0.0, 0.0), SegmentPoint(0.0, 0.0, SEGMENT_M)),
            bounds = MapBounds(0.0, 0.0, 0.0, 0.0),
            source = SegmentSource.ACTIVITY,
            createdAtMs = 0,
        ),
    )

    private fun pass(segmentId: Long, activityId: Long, startMs: Long, seconds: Double) = SegmentEffort(
        segmentId,
        activityId,
        SegmentMatch(startMs, startMs + (seconds * MS).toLong(), seconds, SEGMENT_M / seconds, null, null),
        isPr = false,
    )

    @Test
    fun `best efforts rank against earlier rides only, below the top 3 they are left out`() = runTest {
        (1L..5L).forEach(::ride)
        // 5 km times: ride 5 is slower than three earlier rides (rank 4), ride 2 is a record then.
        fiveKm(1, 700.0)
        fiveKm(2, 600.0)
        fiveKm(3, 650.0)
        fiveKm(4, 500.0)
        fiveKm(5, 690.0)

        assertEquals(listOf(1), observe(2).first().efforts.map { it.rank })
        assertEquals(listOf(2), observe(3).first().efforts.map { it.rank })
        assertTrue(observe(5).first().efforts.isEmpty())
    }

    @Test
    fun `a tie with an earlier ride is not a record`() = runTest {
        ride(1)
        ride(2)
        fiveKm(1, 600.0)
        fiveKm(2, 600.0)

        assertEquals(2, observe(2).first().efforts.single().rank)
    }

    @Test
    fun `every segment passed is listed, top-3 passes first, the others in ride order`() = runTest {
        (1L..5L).forEach(::ride)
        val climb = segment("Climb")
        val sprint = segment("Sprint")
        val flat = segment("Flat")
        // Earlier rides: the climb was done faster 3 times, the sprint never.
        (1L..3L).forEach { segments.replaceForActivity(it, listOf(pass(climb, it, 0, 100.0))) }
        segments.replaceForActivity(
            5,
            listOf(pass(climb, 5, startMs = 0, seconds = 200.0), pass(flat, 5, 10, 300.0), pass(sprint, 5, 20, 60.0)),
        )

        val results = observe(5).first()

        assertEquals(listOf("Flat", "Sprint", "Climb"), results.segments.map { it.segmentName })
        assertEquals(listOf(1, 1, 4), results.segments.map { it.rank })
        assertTrue(observe(4).first().isEmpty)
    }

    @Test
    fun `distance and speed efforts long enough can become a segment, power and whole rides cannot`() {
        fun effort(key: BestEffortKey, value: Double, window: Boolean = true) =
            BestEffort(key, value, if (window) 10_000L else null, if (window) 70_000L else null)

        assertEquals(10_000L..70_000L, effort(BestEffortKey.FASTEST_1_KM, 100.0).segmentWindowMs)
        // 1 min at 10 m/s = 600 m; 30 s at 6 m/s = 180 m is shorter than a segment can be.
        assertEquals(10_000L..70_000L, effort(BestEffortKey.SPEED_1_MIN, 10.0).segmentWindowMs)
        assertNull(effort(BestEffortKey.SPEED_30_S, 6.0).segmentWindowMs)
        assertNull(effort(BestEffortKey.FASTEST_100_KM, 10_000.0).segmentWindowMs)
        assertNull(effort(BestEffortKey.POWER_5_MIN, 250.0).segmentWindowMs)
        assertNull(effort(BestEffortKey.LONGEST_RIDE, 40_000.0, window = false).segmentWindowMs)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MS = 1_000
        const val SEGMENT_M = 1_000.0
    }
}
