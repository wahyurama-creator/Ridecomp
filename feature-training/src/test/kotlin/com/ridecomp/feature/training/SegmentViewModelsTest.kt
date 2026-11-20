package com.ridecomp.feature.training

import app.cash.turbine.test
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.testing.FakeActivityAnalysisRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeSegmentRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.AnalysisRequests
import com.ridecomp.core.usecase.CreateSegmentFromActivityUseCase
import com.ridecomp.core.usecase.DeleteSegmentUseCase
import com.ridecomp.core.usecase.LoadTrackOutlineUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveSegmentEffortsUseCase
import com.ridecomp.core.usecase.ObserveSegmentUseCase
import com.ridecomp.core.usecase.ReadRideTrackUseCase
import com.ridecomp.core.usecase.RequestReanalysisUseCase
import com.ridecomp.feature.training.viewmodel.SegmentDetailEffect
import com.ridecomp.feature.training.viewmodel.SegmentDetailUiEvent
import com.ridecomp.feature.training.viewmodel.SegmentDetailViewModel
import com.ridecomp.feature.training.viewmodel.SegmentDraft
import com.ridecomp.feature.training.viewmodel.SegmentEditorEffect
import com.ridecomp.feature.training.viewmodel.SegmentEditorUiEvent
import com.ridecomp.feature.training.viewmodel.SegmentEditorViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SegmentViewModelsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val activities = FakeActivityRepository()
    private val segments = FakeSegmentRepository(activities)
    private val settings = ObserveRideSettingsUseCase(FakeSettingsRepository())
    private val readTrack = ReadRideTrackUseCase(activities, dispatchers)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        activities.put(
            Activity(
                id = 1,
                uuid = "u1",
                name = "Gowes Pagi",
                status = ActivityStatus.FINISHED,
                startedAtMs = 0,
                endedAtMs = null,
                totals = RideTotals(),
                lastCheckpointAtMs = 0,
            ),
        )
        // 3 km going north, 10 m per second.
        activities.samples[1] = (0..300).map {
            RideSample(
                timeMs = it * 1_000L,
                latDeg = -7.8 + it * 10.0 / 111_320.0,
                lonDeg = 110.37,
                distanceM =
                it * 10.0,
            )
        }.toMutableList()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun editor(draft: SegmentDraft? = null) = SegmentEditorViewModel(
        1,
        draft,
        LoadTrackOutlineUseCase(readTrack, dispatchers),
        ObserveActivityUseCase(activities),
        settings,
        CreateSegmentFromActivityUseCase(
            readTrack,
            segments,
            RequestReanalysisUseCase(FakeActivityAnalysisRepository(activities), AnalysisRequests()),
            FakeClock(),
        ),
    )

    @Test
    fun `the editor starts on the whole ride named after it and refuses a too short part`() {
        val viewModel = editor()

        assertEquals("Gowes Pagi", viewModel.uiState.value.name)
        assertEquals(3_000.0, viewModel.uiState.value.endM)
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onEvent(SegmentEditorUiEvent.RangeChanged(1_000.0, 1_100.0))

        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `a best effort draft starts on its stretch with the sprint name`() {
        // Seconds 60..120 at 10 m/s: 600 m to 1200 m.
        val viewModel = editor(SegmentDraft(60_000L..120_000L, "Sprint 20 Nov"))

        assertEquals("Sprint 20 Nov", viewModel.uiState.value.name)
        assertEquals(600.0, viewModel.uiState.value.startM)
        assertEquals(1_200.0, viewModel.uiState.value.endM)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `saving creates the segment and opens it`() = runTest(dispatcher) {
        val viewModel = editor()
        viewModel.onEvent(SegmentEditorUiEvent.RangeChanged(500.0, 2_000.0))
        viewModel.onEvent(SegmentEditorUiEvent.NameChanged("Tanjakan pagi"))

        viewModel.effects.test {
            viewModel.onEvent(SegmentEditorUiEvent.Save)
            val created = assertIs<SegmentEditorEffect.Created>(awaitItem())
            assertEquals("Tanjakan pagi", segments.segments.value.getValue(created.segmentId).name)
        }
    }

    @Test
    fun `deleting a segment asks first, then leaves the screen`() = runTest(dispatcher) {
        editor().apply { onEvent(SegmentEditorUiEvent.Save) }
        val id = segments.segments.value.keys.single()
        val viewModel = SegmentDetailViewModel(
            id,
            ObserveSegmentUseCase(segments),
            ObserveSegmentEffortsUseCase(segments),
            settings,
            DeleteSegmentUseCase(segments),
        )

        viewModel.onEvent(SegmentDetailUiEvent.AskDelete)
        assertTrue(viewModel.uiState.value.isDeleteAsked)
        viewModel.effects.test {
            viewModel.onEvent(SegmentDetailUiEvent.ConfirmDelete)
            assertEquals(SegmentDetailEffect.Deleted, awaitItem())
        }
        assertTrue(segments.segments.value.isEmpty())
    }
}
