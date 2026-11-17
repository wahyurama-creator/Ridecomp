package com.ridecomp.feature.ride

import app.cash.turbine.test
import com.ridecomp.core.model.Bike
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeBikeRepository
import com.ridecomp.core.usecase.BikeField
import com.ridecomp.core.usecase.DeleteBikeUseCase
import com.ridecomp.core.usecase.GetBikeUseCase
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.ObserveBikeChoiceUseCase
import com.ridecomp.core.usecase.SaveBikeUseCase
import com.ridecomp.core.usecase.SelectBikeUseCase
import com.ridecomp.feature.ride.viewmodel.BikeEditorEffect
import com.ridecomp.feature.ride.viewmodel.BikeEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.BikeEditorUseCases
import com.ridecomp.feature.ride.viewmodel.BikeEditorViewModel
import com.ridecomp.feature.ride.viewmodel.BikeTarget
import com.ridecomp.feature.ride.viewmodel.BikesUiEvent
import com.ridecomp.feature.ride.viewmodel.BikesViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BikeViewModelsTest {
    private val bikes = FakeBikeRepository()
    private val profiles = FakeActivityProfileRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun editor(target: BikeTarget) = BikeEditorViewModel(
        target,
        BikeEditorUseCases(
            GetBikeUseCase(bikes),
            ObserveBikeChoiceUseCase(bikes),
            ObserveActivityProfilesUseCase(profiles),
            SaveBikeUseCase(bikes),
            DeleteBikeUseCase(bikes),
        ),
    )

    @Test
    fun `the list marks the bike for the next ride and can switch it`() = runTest {
        bikes.save(Bike(name = "MTB", profileId = MTB_ID))
        val viewModel =
            BikesViewModel(
                ObserveBikeChoiceUseCase(bikes),
                ObserveActivityProfilesUseCase(profiles),
                SelectBikeUseCase(bikes),
            )

        assertEquals(2, viewModel.uiState.value.bikes.size)
        assertEquals(1L, viewModel.uiState.value.selectedId)

        viewModel.onEvent(BikesUiEvent.Select(2))
        assertEquals(2L, viewModel.uiState.value.selectedId)
    }

    @Test
    fun `a new bike starts as Road with the defaults and is saved with a comma decimal`() = runTest {
        val viewModel = editor(BikeTarget.New)
        assertTrue(viewModel.uiState.value.isNew)
        assertEquals(ROAD_ID, viewModel.uiState.value.profileId)
        assertFalse(viewModel.uiState.value.canDelete)

        viewModel.onEvent(BikeEditorUiEvent.NameChanged("Polygon Xtrada"))
        viewModel.onEvent(BikeEditorUiEvent.TypePicked(MTB_ID))
        viewModel.onEvent(BikeEditorUiEvent.FieldChanged(BikeField.WEIGHT, "13,5"))
        viewModel.onEvent(BikeEditorUiEvent.WheelPicked(MTB_WHEEL_MM))
        viewModel.effects.test {
            viewModel.onEvent(BikeEditorUiEvent.Save)
            assertEquals(BikeEditorEffect.Done, awaitItem())
        }

        val saved = bikes.observeAll().first().last()
        assertEquals(Bike(2, "Polygon Xtrada", MTB_ID, 13.5, MTB_WHEEL_MM), saved)
    }

    @Test
    fun `unparsable and out of range values are marked, nothing is saved`() = runTest {
        val viewModel = editor(BikeTarget.First)

        viewModel.onEvent(BikeEditorUiEvent.FieldChanged(BikeField.WEIGHT, "abc"))
        viewModel.onEvent(BikeEditorUiEvent.Save)
        assertEquals(setOf(BikeField.WEIGHT), viewModel.uiState.value.invalidFields)

        viewModel.onEvent(BikeEditorUiEvent.FieldChanged(BikeField.WEIGHT, "9"))
        viewModel.onEvent(BikeEditorUiEvent.FieldChanged(BikeField.WHEEL_CIRCUMFERENCE, "500"))
        viewModel.onEvent(BikeEditorUiEvent.Save)
        assertEquals(setOf(BikeField.WHEEL_CIRCUMFERENCE), viewModel.uiState.value.invalidFields)
        assertEquals(listOf(Bike(id = 1, profileId = ROAD_ID)), bikes.observeAll().first())
    }

    @Test
    fun `onboarding edits the first bike in place`() = runTest {
        val viewModel = editor(BikeTarget.First)
        assertEquals(1L, viewModel.uiState.value.id)

        viewModel.onEvent(BikeEditorUiEvent.FieldChanged(BikeField.WEIGHT, "10.2"))
        viewModel.onEvent(BikeEditorUiEvent.Save)

        assertEquals(listOf(Bike(id = 1, profileId = ROAD_ID, weightKg = 10.2)), bikes.observeAll().first())
    }

    @Test
    fun `a bike can be deleted after confirming, but not the last one`() = runTest {
        assertFalse(editor(BikeTarget.Existing(1)).uiState.value.canDelete)
        bikes.save(Bike(name = "MTB", profileId = MTB_ID))
        val viewModel = editor(BikeTarget.Existing(2))
        assertTrue(viewModel.uiState.value.canDelete)

        viewModel.onEvent(BikeEditorUiEvent.AskDelete)
        assertTrue(viewModel.uiState.value.isDeleteAsked)
        viewModel.effects.test {
            viewModel.onEvent(BikeEditorUiEvent.ConfirmDelete)
            assertEquals(BikeEditorEffect.Done, awaitItem())
        }
        assertEquals(listOf(1L), bikes.observeAll().first().map { it.id })
    }

    private companion object {
        const val ROAD_ID = 1L
        const val MTB_ID = 3L
        const val MTB_WHEEL_MM = 2_273
    }
}
