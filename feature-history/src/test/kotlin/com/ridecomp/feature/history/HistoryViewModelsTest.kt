package com.ridecomp.feature.history

import app.cash.turbine.test
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.model.StravaUpload
import com.ridecomp.core.model.StravaUploadState
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeExportFileStore
import com.ridecomp.core.testing.FakeExportedFileHandler
import com.ridecomp.core.testing.FakeGuideRepository
import com.ridecomp.core.testing.FakeNetworkStatusSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakePressureSource
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeStravaAccountStore
import com.ridecomp.core.testing.FakeStravaApi
import com.ridecomp.core.testing.FakeStravaUploadRepository
import com.ridecomp.core.testing.FakeTrainingRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.AnalysisSectionKind
import com.ridecomp.core.usecase.AnalysisStatKind
import com.ridecomp.core.usecase.DeleteActivityUseCase
import com.ridecomp.core.usecase.DismissTipUseCase
import com.ridecomp.core.usecase.EstimateFtpUseCase
import com.ridecomp.core.usecase.ExportActivityUseCase
import com.ridecomp.core.usecase.ExportFitUseCase
import com.ridecomp.core.usecase.ExportFormat
import com.ridecomp.core.usecase.ExportGpxUseCase
import com.ridecomp.core.usecase.GetLapsUseCase
import com.ridecomp.core.usecase.HasBarometerUseCase
import com.ridecomp.core.usecase.LoadActivityAnalysisUseCase
import com.ridecomp.core.usecase.LoadRideChartUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveGuideUseCase
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveMonthTotalsUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveRidesOnStravaUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
import com.ridecomp.core.usecase.ObserveStravaUploadUseCase
import com.ridecomp.core.usecase.QueueStravaUploadUseCase
import com.ridecomp.core.usecase.RecomputeTrainingUseCase
import com.ridecomp.core.usecase.SaveActivityFileUseCase
import com.ridecomp.core.usecase.ShareActivityFileUseCase
import com.ridecomp.core.usecase.StravaUploadRequests
import com.ridecomp.core.usecase.SuggestExportFileNameUseCase
import com.ridecomp.core.usecase.TrainingCalendar
import com.ridecomp.feature.history.viewmodel.ActivityDetailEffect
import com.ridecomp.feature.history.viewmodel.ActivityDetailUiEvent
import com.ridecomp.feature.history.viewmodel.ActivityDetailUseCases
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.ActivityStravaUseCases
import com.ridecomp.feature.history.viewmodel.FileMessage
import com.ridecomp.feature.history.viewmodel.HistoryUiEvent
import com.ridecomp.feature.history.viewmodel.HistoryViewModel
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    private val profiles = FakeProfileRepository()
    private val guide = FakeGuideRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val stravaStore = FakeStravaAccountStore()
    private val stravaUploads = FakeStravaUploadRepository()

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
            ObserveRidesOnStravaUseCase(stravaUploads),
        )
        stravaUploads.save(StravaUpload(3, StravaUploadState.DONE, 1, 2))
        stravaUploads.save(StravaUpload(4, StravaUploadState.QUEUED))

        assertEquals(setOf(3L), viewModel.uiState.value.onStrava)
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
            loadAnalysis = LoadActivityAnalysisUseCase(
                LoadRideChartUseCase(repository),
                repository,
                profiles,
                EstimateFtpUseCase(profiles, FakeBestEffortRepository(repository), guide, FakeClock()),
                FakeClock(),
            ),
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
            observeMapTiles = ObserveMapTilesUseCase(FakeNetworkStatusSource(), settings, FakeOfflineMapRepository()),
            observeGuide = ObserveGuideUseCase(guide),
            dismissTip = DismissTipUseCase(guide),
            // The test phone has no barometer: climbing is shown as an estimate (D-148).
            hasBarometer = HasBarometerUseCase(FakePressureSource()),
        )
        val strava = ActivityStravaUseCases(
            ObserveStravaStatusUseCase(stravaStore, FakeStravaApi.SETUP),
            ObserveStravaUploadUseCase(stravaUploads),
            QueueStravaUploadUseCase(stravaUploads, StravaUploadRequests(), FakeClock()),
        )
        return ActivityDetailViewModel(id, useCases, strava, TestDispatcherProvider(dispatcher))
    }

    @Test
    fun `detail shows the ride and its sections in display units`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        repository.appendSamples(id, (1..10).map { RideSample(it * 1_000L, distanceM = it * 1_000.0, speedMps = 10.0) })

        val state = detailViewModel(id).uiState.value

        assertEquals(ActivityStatus.FINISHED, state.activity?.status)
        val speed = state.sections.single { it.kind == AnalysisSectionKind.SPEED }.values
        assertEquals(10, speed.size)
        assertEquals(36.0, speed.first().y)
        assertEquals(1.0, speed.first().x)
        // No altitude or sensor data: those sections are hidden.
        assertEquals(listOf(AnalysisSectionKind.SPEED), state.sections.map { it.kind })
        // Without a barometer the climbing is labelled as a GPS estimate.
        assertTrue(state.isAscentEstimated)
    }

    @Test
    fun `section numbers use the ride totals and the route follows the gps points`() = runTest {
        val id = repository.create(NewActivity("u1", "Ride", FakeClock.DEFAULT_START_MS))
        val totals = RideTotals(distanceM = 5_000.0, movingS = 1_000, avgSpeedMps = 5.0, maxSpeedMps = 10.0)
        repository.finish(id, "Ride", totals, FakeClock.DEFAULT_START_MS + 1_000)
        repository.appendSamples(
            id,
            (1..5).map {
                RideSample(
                    it * 1_000L,
                    latDeg = -7.0 + it * 0.001,
                    lonDeg = 110.0,
                    distanceM = it * 10.0,
                    speedMps = 4.0,
                )
            } + RideSample(6_000L, distanceM = 60.0, speedMps = 4.0),
        )

        val state = detailViewModel(id).uiState.value

        val stats = state.sections.single { it.kind == AnalysisSectionKind.SPEED }.stats
        assertEquals(
            listOf(AnalysisStatKind.AVG, AnalysisStatKind.MAX, AnalysisStatKind.MOVING_TIME),
            stats.map { it.kind },
        )
        assertEquals(18.0, stats[0].value, DELTA)
        assertEquals(36.0, stats[1].value, DELTA)
        // Times stay in seconds: not converted like the speeds (T11.3).
        assertEquals(1_000.0, stats[2].value, DELTA)
        // The last sample has no fix: left out of the route.
        assertEquals(5, state.route.size)
    }

    @Test
    fun `a ride without heart rate shows the sensor card until it is closed`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        repository.appendSamples(id, (1..5).map { RideSample(it * 1_000L, distanceM = it * 10.0, speedMps = 5.0) })
        val viewModel = detailViewModel(id)
        assertTrue(viewModel.uiState.value.isHeartRateTipShown)

        viewModel.onEvent(ActivityDetailUiEvent.DismissHeartRateTip)

        assertFalse(viewModel.uiState.value.isHeartRateTipShown)
        assertTrue(GuideTip.HEART_RATE_ZONES in guide.observe().first().dismissed)
    }

    @Test
    fun `a ride with heart rate shows its zones, or asks for hr max`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        repository.appendSamples(id, (1..5).map { RideSample(it * 1_000L, distanceM = it * 10.0, heartRateBpm = 150) })

        val missing = detailViewModel(id).uiState.value
        assertTrue(missing.isHrMaxMissing)
        assertFalse(missing.isHeartRateTipShown)

        profiles.save(RiderProfile(hrMaxBpm = 200))
        val zones = assertNotNull(detailViewModel(id).uiState.value.heartRateZones)
        assertEquals(200, zones.hrMaxBpm)
        assertFalse(zones.isEstimated)
        assertEquals(5L, zones.zones[2].seconds)
    }

    @Test
    fun `strava row follows the account and queues the ride`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        val viewModel = detailViewModel(id)
        assertEquals(StravaStatus.NotConnected, viewModel.uiState.value.stravaStatus)

        stravaStore.account.value = StravaAccount(tokens = FakeStravaApi.GRANT.tokens)
        viewModel.onEvent(ActivityDetailUiEvent.UploadToStrava)

        assertIs<StravaStatus.Connected>(viewModel.uiState.value.stravaStatus)
        assertEquals(StravaUploadState.QUEUED, viewModel.uiState.value.stravaUpload?.state)
    }

    @Test
    fun `sensor sections appear when the ride has sensor data`() = runTest {
        val id = savedRide(FakeClock.DEFAULT_START_MS)
        repository.appendSamples(
            id,
            (1..5).map {
                RideSample(it * 1_000L, distanceM = it * 10.0, heartRateBpm = 140, cadenceRpm = 85, powerW = 200)
            },
        )

        val sections = detailViewModel(id).uiState.value.sections

        // Page order (T11.2): heart rate, power, cadence; no speed or elevation data, so those are hidden.
        assertEquals(
            listOf(AnalysisSectionKind.HEART_RATE, AnalysisSectionKind.POWER, AnalysisSectionKind.CADENCE),
            sections.map { it.kind },
        )
        assertEquals(200.0, sections.single { it.kind == AnalysisSectionKind.POWER }.values.first().y)
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

private const val DELTA = 1e-9
