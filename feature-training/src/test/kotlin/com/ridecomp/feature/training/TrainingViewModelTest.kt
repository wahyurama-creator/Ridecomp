package com.ridecomp.feature.training

import com.ridecomp.core.engine.training.LoadMethod
import com.ridecomp.core.engine.training.RideLoad
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeTrainingPlanRepository
import com.ridecomp.core.testing.FakeTrainingRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.ExtendTrainingToTodayUseCase
import com.ridecomp.core.usecase.LoadWeekZonesUseCase
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveTrainingUseCase
import com.ridecomp.core.usecase.ReadRideTrackUseCase
import com.ridecomp.core.usecase.RecomputeTrainingUseCase
import com.ridecomp.core.usecase.TrainingCalendar
import com.ridecomp.feature.training.viewmodel.TrainingViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val activities = FakeActivityRepository()
    private val training = FakeTrainingRepository(activities)
    private val today = LocalDate.of(2026, 11, 12)
    private val clock = FakeClock(nowMs = today.atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
    private val calendar = TrainingCalendar(clock) { ZoneOffset.UTC }
    private val profiles = FakeProfileRepository(RiderProfile(hrMaxBpm = 200))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TrainingViewModel(
        ObserveTrainingUseCase(training, calendar),
        LoadWeekZonesUseCase(
            training,
            ReadRideTrackUseCase(activities, dispatchers),
            profiles,
            calendar,
            clock,
            dispatchers,
        ),
        ObserveRideSettingsUseCase(FakeSettingsRepository()),
        ExtendTrainingToTodayUseCase(RecomputeTrainingUseCase(training, calendar, dispatchers), calendar),
        ObserveActivePlanUseCase(FakeTrainingPlanRepository(), calendar),
    )

    @Test
    fun `without any load the tab shows how to get one`() {
        assertFalse(viewModel().uiState.value.hasLoad)
    }

    @Test
    fun `a ride with heart rate this week fills the cards and the zones`() = runTest(dispatcher) {
        val startedAtMs = today.atTime(7, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        activities.put(
            Activity(
                1,
                "u1",
                "Pagi",
                ActivityStatus.FINISHED,
                startedAtMs,
                null,
                RideTotals(distanceM = 20_000.0),
                startedAtMs,
            ),
        )
        activities.samples[1] = (0 until 600).map { RideSample(timeMs = startedAtMs + it * 1_000L, heartRateBpm = 150) }
            .toMutableList()
        training.setRideLoad(1, RideLoad(40.0, LoadMethod.HEART_RATE, null))
        RecomputeTrainingUseCase(training, calendar, dispatchers)(startedAtMs)

        val state = viewModel().uiState.value

        assertTrue(state.hasLoad)
        assertEquals(1, state.overview?.week?.rides)
        assertEquals(listOf(0L, 0L, 600L, 0L, 0L), state.zonesS)
    }
}
