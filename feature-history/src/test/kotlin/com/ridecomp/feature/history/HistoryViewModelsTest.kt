package com.ridecomp.feature.history

import app.cash.turbine.test
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeExportFileStore
import com.ridecomp.core.testing.FakeExportedFileHandler
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeTrainingRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.DeleteActivityUseCase
import com.ridecomp.core.usecase.ExportActivityUseCase
import com.ridecomp.core.usecase.ExportFitUseCase
import com.ridecomp.core.usecase.ExportFormat
import com.ridecomp.core.usecase.ExportGpxUseCase
import com.ridecomp.core.usecase.GetLapsUseCase
import com.ridecomp.core.usecase.LoadRideChartUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveMonthTotalsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.RecomputeTrainingUseCase
import com.ridecomp.core.usecase.SaveActivityFileUseCase
import com.ridecomp.core.usecase.ShareActivityFileUseCase
import com.ridecomp.core.usecase.SuggestExportFileNameUseCase
import com.ridecomp.core.usecase.TrainingCalendar
import com.ridecomp.feature.history.viewmodel.ActivityDetailEffect
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiEvent
import com.ridecomp.feature.history.viewmodel.ActivityDetailUseCases
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.ChartKind
import com.ridecomp.feature.history.viewmodel.FileMessage
import com.ridecomp.feature.history.viewmodel.HistoryUiEvent
import com.ridecomp.feature.history.viewmodel.HistoryViewModel
import java.time.ZoneOffset
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
class HistoryViewModelsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeActivityRepository()
    private val settings = FakeSettingsRepository()
    private val handler = FakeExportedFileHandler()
    private val store = FakeExportFileStore()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun savedRide(startedAt: Long): Long {
        val id = repository.create(NewActivity("u$startedAt", "Ride $startedAt", startedAt))
        repository.finish(id, "Ride $startedAt", RideTotals(distanceM = 1_000.0, movingS = 200), startedAt + 1_000)
        return id
    }

    @Test
    fun `history loads 20 rides then more on demand`() = runTest {
        (1L..45L).forEach { savedRide(it) }
        val viewModel = HistoryViewModel(
            ObserveHistoryPageUseCase(repository),
            ObserveHistoryCountUseCase(repository),
            ObserveRideSettingsUseCase(settings),
            ObserveMonthTotalsUseCase(repository, FakeClock()),
            FakeClock(),
        )

        assertEquals(20, viewModel.uiState.value.rides.size)
        assertEquals(45L, viewModel.uiState.value.rides.first().startedAtMs)
        viewModel.onEvent(HistoryUiEvent.LoadMore)
        viewModel.onEvent(HistoryUiEvent.LoadMore)
        viewModel.onEvent(HistoryUiEvent.LoadMore)

        assertEquals(45, viewModel.uiState.value.rides.size)
        assertTrue(!viewModel.uiState.value.hasMore)
    }

    private fun detailViewModel(id: Long): ActivityDetailViewModel {
        val export = ExportActivityUseCase(
            ExportFitUseCase(repository, store, FakeClock(), ZoneOffset.UTC, FakeActivityProfileRepository()),
            ExportGpxUseCase(repository, store, ZoneOffset.UTC),
        )
        val useCases = ActivityDetailUseCases(
            observeActivity = ObserveActivityUseCase(repository),
            getLaps = GetLapsUseCase(repository),
            loadChart = LoadRideChartUseCase(repository),
            observeSettings = ObserveRideSettingsUseCase(settings),
            share = ShareActivityFileUseCase(export, handler),
            save = SaveActivityFileUseCase(export, handler),
            suggestName = SuggestExportFileNameUseCase(repository, ZoneOffset.UTC),
            delete = DeleteActivityUseCase(
                repository,
                RecomputeTrainingUseCase(
                    FakeTrainingRepository(repository),
                    TrainingCalendar(FakeClock()),
                    TestDispatcherProvider(dispatcher),
                ),
            ),
        )
        return ActivityDetailViewModel(id, useCases, TestDispatcherProvider(dispatcher))
    }

    @Test
    fun `detail shows the ride and downsampled charts in display units`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        repository.appendSamples(id, (1..10).map { RideSample(it * 1_000L, distanceM = it * 1_000.0, speedMps = 10.0) })

        val state = detailViewModel(id).uiState.value

        assertEquals(ActivityStatus.FINISHED, state.activity?.status)
        val speed = state.charts.single { it.kind == ChartKind.SPEED }.values
        assertEquals(10, speed.size)
        assertEquals(36.0, speed.first().y)
        assertEquals(1.0, speed.first().x)
        // No altitude or sensor data: those charts are left out.
        assertEquals(listOf(ChartKind.SPEED), state.charts.map { it.kind })
    }

    @Test
    fun `sensor charts appear when the ride has sensor data`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        repository.appendSamples(
            id,
            (1..5).map {
                RideSample(it * 1_000L, distanceM = it * 10.0, heartRateBpm = 140, cadenceRpm = 85, powerW = 200)
            },
        )

        val charts = detailViewModel(id).uiState.value.charts

        assertEquals(
            listOf(ChartKind.HEART_RATE, ChartKind.CADENCE, ChartKind.POWER),
            charts.map { it.kind },
        )
        assertEquals(200.0, charts.last().values.first().y)
    }

    @Test
    fun `share exports and hands the file to the share sheet`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        val viewModel = detailViewModel(id)

        viewModel.onEvent(ActivityDetailUiEvent.Share(ExportFormat.FIT))

        assertEquals(ExportFormat.FIT, handler.shared.single().format)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `failed save shows an error message`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        val viewModel = detailViewModel(id)
        handler.isCopyFailing = true

        viewModel.onEvent(ActivityDetailUiEvent.SaveTo(ExportFormat.FIT, "content://x"))

        assertEquals(FileMessage.EXPORT_FAILED, viewModel.uiState.value.message)
        viewModel.onEvent(ActivityDetailUiEvent.MessageShown)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `delete removes the ride and closes the screen`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        val viewModel = detailViewModel(id)

        viewModel.effects.test {
            viewModel.onEvent(ActivityDetailUiEvent.Delete)
            assertEquals(ActivityDetailEffect.Deleted, awaitItem())
        }
        assertNull(repository.get(id))
        assertTrue(viewModel.uiState.value.isMissing)
    }

    @Test
    fun `suggested file name for the save dialog`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)

        assertEquals("RideComp_20260101_0000.fit", detailViewModel(id).suggestedFileName(ExportFormat.FIT))
    }
}
