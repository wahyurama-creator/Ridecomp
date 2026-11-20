package com.ridecomp.core.usecase

import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.testing.FakeActivityAnalysisRepository
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.RecordingLogger
import com.ridecomp.core.testing.TestDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** Post-ride analysis and record flags (T4.1, D-67). */
class BestEffortUseCasesTest {
    private val dispatcher = StandardTestDispatcher()
    private val activities = FakeActivityRepository()
    private val efforts = FakeBestEffortRepository(activities)
    private val profiles = FakeActivityProfileRepository(
        listOf(
            ActivityProfile(id = ROAD_ID, kind = ActivityKind.ROAD),
            ActivityProfile(id = INDOOR_ID, kind = ActivityKind.INDOOR),
        ),
    )
    private val logger = RecordingLogger()
    private val kindOf = GetActivityKindUseCase(profiles)
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val readTrack = ReadRideTrackUseCase(activities, dispatchers)
    private val evaluate = EvaluateBestEffortsUseCase(efforts, kindOf, dispatchers)
    private val analysis = FakeActivityAnalysisRepository(activities)
    private val analyze = AnalyzeActivitiesUseCase(
        analysis,
        readTrack,
        listOf(BestEffortsStep(efforts, evaluate, kindOf)),
        AnalysisRequests(),
        logger,
    )

    /** A finished ride of 1.2 km at a constant [speedMps]. */
    private fun ride(id: Long, startedAtMs: Long, speedMps: Double, profileId: Long? = ROAD_ID) {
        val seconds = (RIDE_M / speedMps).toInt()
        activities.put(
            Activity(
                id = id,
                uuid = "ride-$id",
                name = "Ride $id",
                status = ActivityStatus.FINISHED,
                startedAtMs = startedAtMs,
                endedAtMs = startedAtMs + seconds * MS,
                totals = RideTotals(distanceM = RIDE_M, avgSpeedMps = speedMps),
                lastCheckpointAtMs = startedAtMs,
                profileId = profileId,
            ),
        )
        activities.samples[id] = (0..seconds).map {
            RideSample(timeMs = startedAtMs + it * MS, distanceM = it * speedMps)
        }.toMutableList()
    }

    private fun prOf(id: Long, key: BestEffortKey): Boolean? =
        efforts.stored.value[id]?.second?.firstOrNull { it.effort.key == key }?.isPr

    @Test
    fun `rides are analyzed oldest first and only a better effort is a new record`() = runTest(dispatcher) {
        // Inserted out of order: the analysis must still follow the ride dates.
        ride(id = 3, startedAtMs = 3 * DAY_MS, speedMps = 8.0)
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 8.0)
        ride(id = 2, startedAtMs = 2 * DAY_MS, speedMps = 10.0)

        analyze()

        assertEquals(true, prOf(1, BestEffortKey.FASTEST_1_KM))
        assertEquals(true, prOf(2, BestEffortKey.FASTEST_1_KM))
        assertEquals(false, prOf(3, BestEffortKey.FASTEST_1_KM))
        val record = efforts.observeRecords(ActivityKind.ROAD).first().first { it.key == BestEffortKey.FASTEST_1_KM }
        assertEquals(2L, record.activityId)
        assertEquals(100.0, record.value, TOLERANCE)
        assertTrue(analysis.getPendingAnalysis(AnalysisConfig.VERSION, AnalysisConfig.BATCH_SIZE).isEmpty())
    }

    @Test
    fun `rides analyzed before the speed efforts gain them, oldest first`() = runTest(dispatcher) {
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 10.0)
        ride(id = 2, startedAtMs = 2 * DAY_MS, speedMps = 8.0)
        // Fully analyzed by the app before T11.8 (training was the last step, D-125).
        activities.analyzedVersions[1] = AnalysisConfig.TRAINING_VERSION
        activities.analyzedVersions[2] = AnalysisConfig.TRAINING_VERSION

        analyze()

        assertEquals(true, prOf(1, BestEffortKey.SPEED_1_MIN))
        assertEquals(false, prOf(2, BestEffortKey.SPEED_1_MIN))
        assertEquals(AnalysisConfig.VERSION, activities.analyzedVersions[2])
    }

    @Test
    fun `indoor records are kept apart from outdoor ones`() = runTest(dispatcher) {
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 10.0)
        ride(id = 2, startedAtMs = 2 * DAY_MS, speedMps = 8.0, profileId = INDOOR_ID)

        analyze()

        assertEquals(true, prOf(2, BestEffortKey.FASTEST_1_KM))
        assertEquals(listOf(ActivityKind.ROAD, ActivityKind.INDOOR), efforts.observeKinds().first())
    }

    @Test
    fun `a failing ride is logged and skipped, the others are analyzed`() = runTest(dispatcher) {
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 10.0)
        ride(id = 2, startedAtMs = 2 * DAY_MS, speedMps = 10.0)
        val failingRepository = object : BestEffortRepository by efforts {
            override suspend fun replace(activityId: Long, kind: ActivityKind, efforts: List<RideEffort>) {
                if (activityId == 1L) error("disk full")
                this@BestEffortUseCasesTest.efforts.replace(activityId, kind, efforts)
            }
        }
        val failingAnalyze = AnalyzeActivitiesUseCase(
            analysis,
            readTrack,
            listOf(BestEffortsStep(failingRepository, evaluate, kindOf)),
            AnalysisRequests(),
            logger,
        )

        failingAnalyze()

        assertTrue(logger.entries.any { it.level == RecordingLogger.Level.ERROR })
        assertTrue(efforts.stored.value.containsKey(2))
        assertEquals(AnalysisConfig.VERSION, activities.analyzedVersions[1])
    }

    @Test
    fun `the summary preview lists new records without storing them`() = runTest(dispatcher) {
        ride(id = 1, startedAtMs = 1 * DAY_MS, speedMps = 8.0)
        analyze()
        ride(id = 2, startedAtMs = 2 * DAY_MS, speedMps = 10.0)

        val preview = PreviewNewRecordsUseCase(activities, readTrack, evaluate)(2)

        assertTrue(preview.any { it.effort.key == BestEffortKey.FASTEST_1_KM })
        assertFalse(preview.any { it.effort.key == BestEffortKey.MOST_ASCENT })
        assertFalse(efforts.stored.value.containsKey(2))
    }

    private companion object {
        const val ROAD_ID = 1L
        const val INDOOR_ID = 4L
        const val MS = 1_000L
        const val DAY_MS = 86_400_000L
        const val RIDE_M = 1_200.0
        const val TOLERANCE = 1e-6
    }
}
