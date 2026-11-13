package com.ridecomp.feature.training

import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.EvaluateBestEffortsUseCase
import com.ridecomp.core.usecase.GetActivityKindUseCase
import com.ridecomp.core.usecase.ObservePersonalRecordsUseCase
import com.ridecomp.core.usecase.ObserveRecordKindsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.PreviewNewRecordsUseCase
import com.ridecomp.core.usecase.ReadRideTrackUseCase
import com.ridecomp.feature.training.viewmodel.NewRecordsViewModel
import com.ridecomp.feature.training.viewmodel.RecordsUiEvent
import com.ridecomp.feature.training.viewmodel.RecordsViewModel
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

        assertEquals(
            listOf(BestEffortKey.FASTEST_1_KM, BestEffortKey.LONGEST_RIDE),
            viewModel.uiState.value.records.map { it.effort.key },
        )
    }
}
