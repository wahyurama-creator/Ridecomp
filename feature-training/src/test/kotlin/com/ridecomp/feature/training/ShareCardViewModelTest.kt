package com.ridecomp.feature.training

import com.ridecomp.core.engine.map.NetworkStatus
import com.ridecomp.core.engine.share.ShareBackdrop
import com.ridecomp.core.engine.share.ShareDetail
import com.ridecomp.core.engine.share.ShareStat
import com.ridecomp.core.engine.share.ShareTarget
import com.ridecomp.core.engine.share.ShareTemplate
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeNetworkStatusSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeSegmentRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeShareCardFiles
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.GetLoadInputsUseCase
import com.ridecomp.core.usecase.GetShareTargetsUseCase
import com.ridecomp.core.usecase.LoadShareCardUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ReadRideTrackUseCase
import com.ridecomp.core.usecase.SaveShareCardUseCase
import com.ridecomp.core.usecase.ShareCardImageUseCase
import com.ridecomp.feature.training.viewmodel.ShareAction
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
    private val files = FakeShareCardFiles(installed = setOf(ShareTarget.WHATSAPP))

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

    private fun viewModel(isOnline: Boolean = true) = ShareCardViewModel(
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
            GetShareTargetsUseCase(files),
            ObserveMapTilesUseCase(
                FakeNetworkStatusSource(NetworkStatus(isConnected = isOnline, isUnmetered = isOnline)),
                FakeSettingsRepository(),
                FakeOfflineMapRepository(),
            ),
        ),
        { _, _ -> null },
        dispatchers,
    )

    @Test
    fun `privacy is on by default and measured statistics only are offered`() {
        val state = viewModel().uiState.value

        assertTrue(state.isEndsHidden)
        assertEquals(201, state.line.size)
        assertEquals(201, state.trail.size)
        assertEquals(
            listOf(ShareStat.DISTANCE, ShareStat.ASCENT, ShareStat.TIME, ShareStat.AVG_SPEED, ShareStat.AVG_HEART_RATE),
            state.availableStats,
        )
        assertEquals(state.availableStats, state.stats)
    }

    @Test
    fun `statistics keep their order and showing the ends gives the whole line`() {
        val viewModel = viewModel()

        viewModel.onEvent(ShareCardUiEvent.ToggleStat(ShareStat.AVG_HEART_RATE))
        viewModel.onEvent(ShareCardUiEvent.ToggleStat(ShareStat.TIME))
        assertEquals(listOf(ShareStat.DISTANCE, ShareStat.ASCENT, ShareStat.AVG_SPEED), viewModel.uiState.value.stats)
        viewModel.onEvent(ShareCardUiEvent.ToggleStat(ShareStat.TIME))
        assertEquals(
            listOf(ShareStat.DISTANCE, ShareStat.ASCENT, ShareStat.TIME, ShareStat.AVG_SPEED),
            viewModel.uiState.value.stats,
        )

        viewModel.onEvent(ShareCardUiEvent.SetEndsHidden(false))
        assertEquals(301, viewModel.uiState.value.line.size)
        assertEquals(301, viewModel.uiState.value.trail.size)
    }

    @Test
    fun `at most six statistics`() {
        val totals = RideTotals(distanceM = 3_000.0, avgHeartRateBpm = 140, avgPowerW = 180, calories = 300)
        activities.put(Activity(1, "u1", "Gowes Pagi", ActivityStatus.FINISHED, 0, null, totals, 0))
        val viewModel = viewModel()
        assertEquals(7, viewModel.uiState.value.availableStats.size)
        assertEquals(6, viewModel.uiState.value.stats.size)
        assertFalse(ShareStat.AVG_HEART_RATE in viewModel.uiState.value.stats)

        viewModel.onEvent(ShareCardUiEvent.ToggleStat(ShareStat.AVG_HEART_RATE))
        assertEquals(6, viewModel.uiState.value.stats.size)
    }

    @Test
    fun `the gallery lists every card, the map cards only with a map`() {
        val online = viewModel().uiState.value
        assertEquals(ShareTemplate.entries.toSet(), online.templates.toSet())
        assertEquals(ShareTemplate.MAP, online.templates.first())

        val offline = viewModel(isOnline = false).uiState.value
        assertFalse(offline.hasMap)
        assertTrue(offline.templates.none { it.backdrop == ShareBackdrop.MAP })
        assertEquals(ShareTemplate.entries.size - 2, offline.templates.size)
    }

    @Test
    fun `installed apps get their own button and receive the card`() {
        val viewModel = viewModel()
        assertEquals(listOf(ShareTarget.WHATSAPP, ShareTarget.OTHER), viewModel.uiState.value.targets)

        viewModel.onEvent(ShareCardUiEvent.Render(ShareAction.Share(ShareTarget.WHATSAPP)))
        assertEquals(ShareAction.Share(ShareTarget.WHATSAPP), viewModel.uiState.value.pending)
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
        viewModel.onEvent(ShareCardUiEvent.SetTemplate(ShareTemplate.DOTTED_LIST))
        assertTrue(viewModel.uiState.value.isTransparent)
        viewModel.onEvent(ShareCardUiEvent.SetTemplate(ShareTemplate.MAP))
        assertFalse(viewModel.uiState.value.isTransparent)
    }
}
