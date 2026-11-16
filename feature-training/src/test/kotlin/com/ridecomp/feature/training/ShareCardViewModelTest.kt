package com.ridecomp.feature.training

import com.ridecomp.core.engine.share.ShareDetail
import com.ridecomp.core.engine.share.ShareStat
import com.ridecomp.core.engine.share.ShareTemplate
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeSegmentRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeShareCardFiles
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.GetLoadInputsUseCase
import com.ridecomp.core.usecase.LoadShareCardUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ReadRideTrackUseCase
import com.ridecomp.core.usecase.SaveShareCardUseCase
import com.ridecomp.core.usecase.ShareCardImageUseCase
import com.ridecomp.feature.training.viewmodel.ShareCardUiEvent
import com.ridecomp.feature.training.viewmodel.ShareCardUseCases
import com.ridecomp.feature.training.viewmodel.ShareCardViewModel
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
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ShareCardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val activities = FakeActivityRepository()
    private val files = FakeShareCardFiles()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        activities.put(
            Activity(
                1,
                "u1",
                "Gowes Pagi",
                ActivityStatus.FINISHED,
                0,
                null,
                RideTotals(distanceM = 3_000.0, avgHeartRateBpm = 140),
                0,
            ),
        )
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

    private fun viewModel() = ShareCardViewModel(
        1,
        ShareCardUseCases(
            LoadShareCardUseCase(
                activities,
                FakeBestEffortRepository(activities),
                FakeSegmentRepository(activities),
                GetLoadInputsUseCase(FakeProfileRepository(), FakeClock()),
                ReadRideTrackUseCase(activities, dispatchers),
                dispatchers,
            ),
            ObserveRideSettingsUseCase(FakeSettingsRepository()),
            ShareCardImageUseCase(files, FakeClock()),
            SaveShareCardUseCase(files),
        ),
        { _, _ -> null },
        dispatchers,
    )

    @Test
    fun `privacy is on by default and measured statistics only are offered`() {
        val state = viewModel().uiState.value

        assertTrue(state.isEndsHidden)
        assertEquals(201, state.line.size)
        assertEquals(
            listOf(ShareStat.DISTANCE, ShareStat.TIME, ShareStat.ASCENT, ShareStat.AVG_SPEED, ShareStat.AVG_HEART_RATE),
            state.availableStats,
        )
        assertEquals(4, state.stats.size)
    }

    @Test
    fun `at most four statistics and showing the ends gives the whole line`() {
        val viewModel = viewModel()

        viewModel.onEvent(ShareCardUiEvent.ToggleStat(ShareStat.AVG_HEART_RATE))
        assertEquals(4, viewModel.uiState.value.stats.size)
        viewModel.onEvent(ShareCardUiEvent.ToggleStat(ShareStat.TIME))
        viewModel.onEvent(ShareCardUiEvent.ToggleStat(ShareStat.AVG_HEART_RATE))
        assertEquals(
            listOf(ShareStat.DISTANCE, ShareStat.ASCENT, ShareStat.AVG_SPEED, ShareStat.AVG_HEART_RATE),
            viewModel.uiState.value.stats,
        )

        viewModel.onEvent(ShareCardUiEvent.SetEndsHidden(false))
        assertEquals(301, viewModel.uiState.value.line.size)
    }

    @Test
    fun `the sticker and the transparent switch give a transparent card, splits are offered`() {
        val viewModel = viewModel()
        assertEquals(ShareTemplate.POSTER, viewModel.uiState.value.template)
        assertTrue(ShareDetail.SPLITS in viewModel.uiState.value.availableDetails)
        assertFalse(viewModel.uiState.value.isTransparent)

        viewModel.onEvent(ShareCardUiEvent.SetBackgroundTransparent(true))
        assertTrue(viewModel.uiState.value.isTransparent)
        viewModel.onEvent(ShareCardUiEvent.SetTemplate(ShareTemplate.PHOTO))
        assertFalse(viewModel.uiState.value.isTransparent)
        viewModel.onEvent(ShareCardUiEvent.SetTemplate(ShareTemplate.STICKER))
        assertTrue(viewModel.uiState.value.isTransparent)
    }
}
