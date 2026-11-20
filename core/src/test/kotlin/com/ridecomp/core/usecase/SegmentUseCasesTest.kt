package com.ridecomp.core.usecase

import com.ridecomp.core.engine.segments.SegmentError
import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.testing.FakeActivityAnalysisRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeSegmentRepository
import com.ridecomp.core.testing.RecordingLogger
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.util.Outcome
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** Segments from rides, matching after saving and when a segment is created later (T4.2). */
class SegmentUseCasesTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val activities = FakeActivityRepository()
    private val analysis = FakeActivityAnalysisRepository(activities)
    private val segments = FakeSegmentRepository(activities)
    private val requests = AnalysisRequests()
    private val readTrack = ReadRideTrackUseCase(activities, dispatchers)
    private val reanalyze = RequestReanalysisUseCase(analysis, requests)
    private val createFromRide = CreateSegmentFromActivityUseCase(readTrack, segments, reanalyze, FakeClock())
    private var otherStepRuns = 0
    private val otherStep = object : AnalysisStep {
        // A step before the segments: never re-run by a new segment.
        override val version = AnalysisConfig.SEGMENTS_VERSION - 1

        override suspend fun analyze(activity: Activity, track: RideTrack) {
            otherStepRuns++
        }
    }
    private val analyze = AnalyzeActivitiesUseCase(
        analysis,
        readTrack,
        listOf(otherStep, SegmentsStep(segments, dispatchers)),
        requests,
        RecordingLogger(),
    )

    private fun lat(y: Double) = LAT0 + y / M_PER_DEG
    private fun lon(x: Double) = LON0 + x / (M_PER_DEG * cos(Math.toRadians(LAT0)))

    /** A ride going east along y = 0 from x = -300 m to 1300 m at [speedMps], one sample a second. */
    private fun ride(id: Long, startedAtMs: Long, speedMps: Double) {
        activities.put(
            Activity(
                id = id,
                uuid = "u$id",
                name = "Ride $id",
                status = ActivityStatus.FINISHED,
                startedAtMs = startedAtMs,
                endedAtMs = null,
                totals = RideTotals(),
                lastCheckpointAtMs = startedAtMs,
            ),
        )
        val seconds = (RIDE_M / speedMps).toInt()
        activities.samples[id] = (0..seconds).map {
            val distance = it * speedMps
            RideSample(
                timeMs = startedAtMs + it * MS,
                latDeg = lat(0.0),
                lonDeg = lon(distance - LEAD_IN_M),
                altitudeM = 100.0 + distance / 100.0,
                distanceM = distance,
            )
        }.toMutableList()
    }

    @Test
    fun `a segment made from a ride matches every ride over it, the faster one is the record`() = runTest(dispatcher) {
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 8.0)
        ride(id = 2, startedAtMs = 2 * DAY_MS, speedMps = 10.0)
        analyze()
        assertEquals(2, otherStepRuns)

        val created = createFromRide(activityId = 1, startDistanceM = 300.0, endDistanceM = 1_300.0, name = "Lurus")
        val segmentId = (created as Outcome.Success).value
        analyze()

        val efforts = segments.observeEfforts(segmentId).first()
        assertEquals(listOf(2L, 1L), efforts.map { it.effort.activityId })
        assertEquals(listOf(true, true), segments.efforts.value.sortedBy { it.activityId }.map { it.isPr })
        // Only the segment step ran again on the old rides.
        assertEquals(2, otherStepRuns)
        val summary = segments.observeSummaries().first().single()
        assertEquals(100.0, summary.bestElapsedS ?: 0.0, ONE_SAMPLE_S)
        assertEquals(1_000.0, summary.segment.lengthM, ONE_SAMPLE_M)
        assertEquals(10.0, summary.segment.gainM, ONE_SAMPLE_M)
    }

    @Test
    fun `a ride whose version is lowered during its analysis is analyzed again`() = runTest(dispatcher) {
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 10.0)
        var runs = 0
        val lowering = object : AnalysisStep {
            override val version = AnalysisConfig.SEGMENTS_VERSION

            override suspend fun analyze(activity: Activity, track: RideTrack) {
                // A segment created meanwhile, while this never-analyzed ride is being analyzed.
                if (runs++ == 0) reanalyze(AnalysisConfig.SEGMENTS_VERSION)
            }
        }

        AnalyzeActivitiesUseCase(analysis, readTrack, listOf(lowering), requests, RecordingLogger())()

        assertEquals(2, runs)
        assertEquals(AnalysisConfig.VERSION, activities.analyzedVersions[1])
    }

    @Test
    fun `a slower later ride is not a record`() = runTest(dispatcher) {
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 10.0)
        createFromRide(activityId = 1, startDistanceM = 300.0, endDistanceM = 1_300.0, name = "Lurus")
        ride(id = 2, startedAtMs = 2 * DAY_MS, speedMps = 8.0)

        analyze()

        assertEquals(mapOf(1L to true, 2L to false), segments.efforts.value.associate { it.activityId to it.isPr })
    }

    @Test
    fun `too short a selection is refused and deleting a segment removes its efforts`() = runTest(dispatcher) {
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 10.0)

        assertEquals(
            Outcome.Failure(SegmentError.TOO_SHORT),
            createFromRide(activityId = 1, startDistanceM = 300.0, endDistanceM = 400.0, name = "Pendek"),
        )

        val id = (createFromRide(1, 300.0, 1_300.0, "Lurus") as Outcome.Success).value
        analyze()
        DeleteSegmentUseCase(segments)(id)

        assertTrue(segments.efforts.value.isEmpty())
        assertTrue(segments.observeSummaries().first().isEmpty())
    }

    private companion object {
        const val LAT0 = -7.78
        const val LON0 = 110.37
        const val M_PER_DEG = 111_320.0
        const val MS = 1_000L
        const val DAY_MS = 86_400_000L
        const val RIDE_M = 1_600.0
        const val LEAD_IN_M = 300.0
        const val ONE_SAMPLE_S = 2.0
        const val ONE_SAMPLE_M = 15.0
    }
}
