package com.ridecomp.feature.ride

import app.cash.turbine.test
import com.ridecomp.core.model.BuiltInMetricKeys
import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPageLimits
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.usecase.ObserveDataPagesUseCase
import com.ridecomp.core.usecase.ResetDataPagesUseCase
import com.ridecomp.core.usecase.SaveDataPagesUseCase
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.model.RideMetricKeys
import com.ridecomp.feature.ride.model.toDataPages
import com.ridecomp.feature.ride.model.toLayout
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorEffect
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DataPagesEditorViewModelTest {
    private val settings = FakeSettingsRepository()
    private val profiles = FakeActivityProfileRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(profileId: Long? = null) = DataPagesEditorViewModel(
        profileId,
        ObserveDataPagesUseCase(settings, profiles),
        SaveDataPagesUseCase(settings, profiles),
        ResetDataPagesUseCase(settings, profiles),
    )

    @Test
    fun `starts from the defaults and every metric key round trips`() {
        val state = viewModel().uiState.value

        assertEquals(DefaultDataPages.ALL.toLayout().pages, state.pages)
        assertEquals(
            RideMetric.entries.toSet(),
            RideMetric.entries.mapNotNull {
                RideMetricKeys.metricOf(it.key)
            }.toSet(),
        )
        assertEquals(DefaultDataPages.ALL, DefaultDataPages.ALL.toLayout().toDataPages())
        // Built-in profile pages (in :core) only use keys the ride screen knows.
        assertEquals(emptyList(), BuiltInMetricKeys.ALL.filterNot { it in RideMetricKeys.ALL })
    }

    @Test
    fun `edit a page, choose a metric and save`() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(DataPagesEditorUiEvent.SelectPage(1))
        viewModel.onEvent(DataPagesEditorUiEvent.SetFieldCount(2))
        viewModel.onEvent(DataPagesEditorUiEvent.PickField(1))
        viewModel.onEvent(DataPagesEditorUiEvent.ChooseMetric(RideMetric.HEART_RATE))
        viewModel.onEvent(DataPagesEditorUiEvent.SetFirstFullWidth(true))

        viewModel.effects.test {
            viewModel.onEvent(DataPagesEditorUiEvent.Save)
            assertEquals(DataPagesEditorEffect.Saved, awaitItem())
        }
        val saved = settings.dataPages.value?.pages?.get(1)
        assertEquals(
            DataPageLayout(listOf(RideMetric.GRADE.key, RideMetric.HEART_RATE.key), isFirstFullWidth = true),
            saved,
        )
        assertNull(viewModel.uiState.value.pickingField)
    }

    @Test
    fun `pages can be added up to the limit and the last page cannot be removed`() {
        val viewModel = viewModel()

        repeat(DataPageLimits.MAX_PAGES) { viewModel.onEvent(DataPagesEditorUiEvent.AddPage) }
        assertEquals(DataPageLimits.MAX_PAGES, viewModel.uiState.value.pages.size)
        assertFalse(viewModel.uiState.value.canAddPage)

        repeat(DataPageLimits.MAX_PAGES) { viewModel.onEvent(DataPagesEditorUiEvent.RemovePage) }
        assertEquals(1, viewModel.uiState.value.pages.size)
    }

    @Test
    fun `stored pages are loaded and reset restores the defaults`() = runTest {
        settings.dataPages.value = DataPagesLayout(listOf(DataPageLayout(listOf(RideMetric.POWER.key))))
        val viewModel = viewModel()
        assertEquals(1, viewModel.uiState.value.pages.size)

        viewModel.onEvent(DataPagesEditorUiEvent.ResetToDefaults)

        assertNull(settings.dataPages.value)
        assertEquals(DefaultDataPages.ALL.size, viewModel.uiState.value.pages.size)
    }

    @Test
    fun `a profile edits its own pages and reset returns to the general pages`() = runTest {
        val viewModel = viewModel(profileId = 1)
        viewModel.onEvent(DataPagesEditorUiEvent.SetFieldCount(1))
        viewModel.onEvent(DataPagesEditorUiEvent.Save)

        assertEquals(1, profiles.get(1)?.dataPages?.pages?.first()?.fields?.size)
        assertNull(settings.dataPages.value)

        viewModel.onEvent(DataPagesEditorUiEvent.ResetToDefaults)
        assertNull(profiles.get(1)?.dataPages)
        assertEquals(DefaultDataPages.ALL.size, viewModel.uiState.value.pages.size)
    }
}
