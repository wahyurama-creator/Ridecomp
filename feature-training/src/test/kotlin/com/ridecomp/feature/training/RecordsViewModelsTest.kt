package com.ridecomp.feature.training

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
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.repository.SegmentEffort
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.FakeSegmentRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.EvaluateBestEffortsUseCase
import com.ridecomp.core.usecase.GetActivityKindUseCase
import com.ridecomp.core.usecase.ObservePersonalRecordsUseCase
import com.ridecomp.core.usecase.ObserveRecordKindsUseCase
import com.ridecomp.core.usecase.ObserveRideResultsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.PreviewNewRecordsUseCase
import com.ridecomp.core.usecase.ReadRideTrackUseCase
import com.ridecomp.feature.training.viewmodel.NewRecordsViewModel
import com.ridecomp.feature.training.viewmodel.RecordsUiEvent
import com.ridecomp.feature.training.viewmodel.RecordsViewModel
import com.ridecomp.feature.training.viewmodel.RideResultLine
import com.ridecomp.feature.training.viewmodel.RideResultsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val activities = FakeActivityRepository()
    private val efforts = FakeBestEffortRepository(activities)
    private val settings = ObserveRideSettingsUseCase(FakeSettingsRepository())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun put(id: Long, startedAtMs: Long) = activities.put(
        Activity(
            id = id,
            uuid = "u$id",
            name = "Ride $id",
            status = ActivityStatus.FINISHED,
            startedAtMs = startedAtMs,
            endedAtMs = null,
            totals = RideTotals(distanceM = 1_000.0, avgSpeedMps = 10.0),
            lastCheckpointAtMs = startedAtMs,
        ),
    )

    private fun effort(key: BestEffortKey, value: Double) = RideEffort(BestEffort(key, value, null, null), isPr = true)

    @Test
    fun `no records shows the empty state`() {
        val viewModel = RecordsViewModel(
            ObservePersonalRecordsUseCase(efforts),
            ObserveRecordKindsUseCase(efforts),
            settings,
        )

        assertTrue(viewModel.uiState.value.isLoaded)
        assertTrue(viewModel.uiState.value.records.isEmpty())
    }

    @Test
    fun `records follow the selected profile kind`() = runTest(dispatcher) {
        put(1, 1_000)
        put(2, 2_000)
        efforts.replace(1, ActivityKind.ROAD, listOf(effort(BestEffortKey.LONGEST_RIDE, 40_000.0)))
        efforts.replace(2, ActivityKind.INDOOR, listOf(effort(BestEffortKey.POWER_5_S, 600.0)))
        val viewModel = RecordsViewModel(
            ObservePersonalRecordsUseCase(efforts),
            ObserveRecordKindsUseCase(efforts),
            settings,
        )

        assertEquals(ActivityKind.ROAD, viewModel.uiState.value.selectedKind)
        assertEquals(listOf(BestEffortKey.LONGEST_RIDE), viewModel.uiState.value.records.map { it.key })

        viewModel.onEvent(RecordsUiEvent.SelectKind(ActivityKind.INDOOR))

        assertEquals(listOf(BestEffortKey.POWER_5_S), viewModel.uiState.value.records.map { it.key })
    }

    @Test
    fun `the summary card lists the records the ride would set`() = runTest(dispatcher) {
        put(1, 1_000)
        activities.samples[1] = (0..100).map { RideSample(timeMs = 1_000L + it * 1_000L, distanceM = it * 10.0) }
            .toMutableList()
        val dispatchers = TestDispatcherProvider(dispatcher)
        val evaluate = EvaluateBestEffortsUseCase(
            efforts,
            GetActivityKindUseCase(FakeActivityProfileRepository()),
            dispatchers,
        )
        val preview = PreviewNewRecordsUseCase(activities, ReadRideTrackUseCase(activities, dispatchers), evaluate)

        val viewModel = NewRecordsViewModel(1, preview, settings)

        // 100 s at 10 m/s: 1 km, the 30 s and 1 min speeds (T11.8) and the distance.
        assertEquals(
            listOf(
                BestEffortKey.FASTEST_1_KM,
                BestEffortKey.SPEED_30_S,
                BestEffortKey.SPEED_1_MIN,
                BestEffortKey.LONGEST_RIDE,
            ),
            viewModel.uiState.value.records.map { it.effort.key },
        )
    }

    @Test
    fun `results are ordered by rank and count efforts and segments`() = runTest {
        put(1, startedAtMs = 1_000)
        put(2, startedAtMs = 2_000)
        val segments = FakeSegmentRepository(activities)
        val segmentId = segments.create(
            Segment(
                1, "Sprint", 500.0, 0.0, 0.0,
                listOf(
                    SegmentPoint(0.0, 0.0, 0.0),
                ),
                MapBounds(0.0, 0.0, 0.0, 0.0), SegmentSource.ACTIVITY, 0,
            ),
        )
        // Ride 1 was faster over the segment: ride 2's pass ranks 2nd, its 1 km is a record.
        segments.replaceForActivity(
            1,
            listOf(SegmentEffort(segmentId, 1, SegmentMatch(0, 60_000, 60.0, 8.3, null, null), true)),
        )
        segments.replaceForActivity(
            2,
            listOf(SegmentEffort(segmentId, 2, SegmentMatch(0, 70_000, 70.0, 7.1, null, null), false)),
        )
        efforts.replace(
            2,
            ActivityKind.ROAD,
            listOf(effort(BestEffortKey.FASTEST_1_KM, 100.0), effort(BestEffortKey.POWER_5_S, 400.0)),
        )

        val ride1 = RideResultsViewModel(1, ObserveRideResultsUseCase(efforts, segments), settings).uiState.value
        val ride2 = RideResultsViewModel(2, ObserveRideResultsUseCase(efforts, segments), settings).uiState.value

        assertTrue(ride1.lines.single() is RideResultLine.Pass)
        assertEquals(listOf(1, 1, 2), ride2.lines.map { it.rank })
        assertTrue(ride2.lines.last() is RideResultLine.Pass)
        assertEquals(2, ride2.effortCount)
        assertEquals(1, ride2.segmentCount)
    }
}
