package com.ridecomp.feature.ride

import app.cash.turbine.test
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBatteryLevelSource
import com.ridecomp.core.testing.FakeLocationSource
import com.ridecomp.core.testing.FakeRideController
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.HandleRecoveredRideUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveDataPagesUseCase
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.PerformRideActionUseCase
import com.ridecomp.core.usecase.ReadBatteryUseCase
import com.ridecomp.core.usecase.RecoveryChoice
import com.ridecomp.core.usecase.RenameActivityUseCase
import com.ridecomp.core.usecase.StartRideUseCase
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.viewmodel.HomeEffect
import com.ridecomp.feature.ride.viewmodel.HomeUiEvent
import com.ridecomp.feature.ride.viewmodel.HomeViewModel
import com.ridecomp.feature.ride.viewmodel.PreRideEffect
import com.ridecomp.feature.ride.viewmodel.PreRideUiEvent
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.feature.ride.viewmodel.RideEffect
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.feature.ride.viewmodel.SummaryEffect
import com.ridecomp.feature.ride.viewmodel.SummaryUiEvent
import com.ridecomp.feature.ride.viewmodel.SummaryViewModel
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RideViewModelsTest {
    private val controller = FakeRideController()
    private val repository = FakeActivityRepository()
    private val settings = FakeSettingsRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun homeViewModel() = HomeViewModel(
        GetRecoverableRideUseCase(repository),
        HandleRecoveredRideUseCase(controller, repository),
        ObserveHistoryPageUseCase(repository),
        ObserveLiveRideUseCase(controller),
        ObserveRideSettingsUseCase(settings),
    )

    @Test
    fun `home offers recovery for an unfinished ride and resumes it`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        val viewModel = homeViewModel()
        assertEquals(id, viewModel.uiState.value.recoverable?.id)

        viewModel.effects.test {
            viewModel.onEvent(HomeUiEvent.Recover(RecoveryChoice.RESUME))
            assertEquals(HomeEffect.OpenRide, awaitItem())
        }
        assertEquals(listOf("RESUME:$id"), controller.commands)
        assertNull(viewModel.uiState.value.recoverable)
    }

    @Test
    fun `the ride currently recorded is never offered for recovery`() = runTest {
        repository.create(NewActivity("u", "Ride", 0))
        controller.live.value = LiveRide(status = RideStatus.RECORDING)

        val viewModel = homeViewModel()

        assertNull(viewModel.uiState.value.recoverable)
        assertTrue(viewModel.uiState.value.isRideInProgress)
    }

    @Test
    fun `pre ride start launches the ride`() = runTest {
        val viewModel = PreRideViewModel(
            ObserveGpsStatusUseCase(FakeLocationSource()),
            ReadBatteryUseCase(FakeBatteryLevelSource()),
            StartRideUseCase(controller),
        )
        assertEquals(80, viewModel.uiState.value.battery?.percent)

        viewModel.effects.test {
            viewModel.onEvent(PreRideUiEvent.Start)
            assertEquals(PreRideEffect.OpenRide, awaitItem())
        }
        assertEquals(listOf(FakeRideController.START), controller.commands)
    }

    @Test
    fun `ride screen opens the summary when finished and closes when the ride ends`() = runTest {
        val viewModel = RideViewModel(
            ObserveLiveRideUseCase(controller),
            ObserveRideSettingsUseCase(settings),
            ObserveDataPagesUseCase(settings),
            PerformRideActionUseCase(controller),
        )

        viewModel.effects.test {
            controller.live.value = LiveRide(status = RideStatus.RECORDING)
            controller.live.value = LiveRide(status = RideStatus.FINISHED)
            assertEquals(RideEffect.OpenSummary, awaitItem())
            controller.live.value = LiveRide(status = RideStatus.IDLE)
            assertEquals(RideEffect.Close, awaitItem())
        }
        viewModel.onEvent(RideAction.LAP)
        assertEquals(listOf("LAP"), controller.commands)
    }

    @Test
    fun `summary renames before saving`() = runTest {
        val id = repository.create(NewActivity("u", "Morning ride", 0))
        controller.activityId.value = id
        val viewModel = SummaryViewModel(
            ObserveLiveRideUseCase(controller),
            ObserveActivityUseCase(repository),
            ObserveRideSettingsUseCase(settings),
            RenameActivityUseCase(repository),
            PerformRideActionUseCase(controller),
        )
        assertEquals("Morning ride", viewModel.uiState.value.name)

        viewModel.effects.test {
            viewModel.onEvent(SummaryUiEvent.NameChanged("Kaliurang"))
            viewModel.onEvent(SummaryUiEvent.Save)
            assertEquals(SummaryEffect.Saved(id), awaitItem())
        }
        assertEquals("Kaliurang", repository.get(id)?.name)
        assertEquals(listOf("SAVE"), controller.commands)
        assertEquals(ActivityStatus.RECORDING, repository.get(id)?.status)
    }

    @Test
    fun `metrics show placeholders for missing data and units`() {
        val formatter = UnitFormatter(UnitSystem.METRIC, Locale.US)
        val live = LiveRide(distanceM = 12_345.0, movingMs = 3_723_000)

        assertEquals("12.35", RideMetric.DISTANCE.value(live, formatter))
        assertEquals("1:02:03", RideMetric.MOVING_TIME.value(live, formatter))
        assertEquals(UnitFormatter.NO_VALUE, RideMetric.SPEED.value(live, formatter))
        assertEquals(UnitFormatter.NO_VALUE, RideMetric.HEART_RATE.value(live, formatter))
        assertEquals(6, DefaultDataPages.MAIN.metrics.size)
    }
}
