package com.ridecomp.feature.ride

import app.cash.turbine.test
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.ComponentType
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeBikeComponentRepository
import com.ridecomp.core.testing.FakeBikeRepository
import com.ridecomp.core.testing.FakeBikeRide
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.usecase.BikeField
import com.ridecomp.core.usecase.DeleteBikeUseCase
import com.ridecomp.core.usecase.DeleteComponentUseCase
import com.ridecomp.core.usecase.GetBikeUseCase
import com.ridecomp.core.usecase.GetComponentUseCase
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.ObserveBikeChoiceUseCase
import com.ridecomp.core.usecase.ObserveBikeDistanceUseCase
import com.ridecomp.core.usecase.ObserveComponentWearUseCase
import com.ridecomp.core.usecase.ReplaceComponentUseCase
import com.ridecomp.core.usecase.SaveBikeUseCase
import com.ridecomp.core.usecase.SaveComponentUseCase
import com.ridecomp.core.usecase.SelectBikeUseCase
import com.ridecomp.feature.ride.viewmodel.BikeEditorEffect
import com.ridecomp.feature.ride.viewmodel.BikeEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.BikeEditorUseCases
import com.ridecomp.feature.ride.viewmodel.BikeEditorViewModel
import com.ridecomp.feature.ride.viewmodel.BikeTarget
import com.ridecomp.feature.ride.viewmodel.BikesUiEvent
import com.ridecomp.feature.ride.viewmodel.BikesViewModel
import com.ridecomp.feature.ride.viewmodel.ComponentEditorEffect
import com.ridecomp.feature.ride.viewmodel.ComponentEditorUiEvent
import com.ridecomp.feature.ride.viewmodel.ComponentEditorUseCases
import com.ridecomp.feature.ride.viewmodel.ComponentEditorViewModel
import com.ridecomp.feature.ride.viewmodel.ComponentTarget
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
    private val components = FakeBikeComponentRepository()
    private val clock = FakeClock(CLOCK_START_MS)

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
            ObserveComponentWearUseCase(components),
            ObserveBikeDistanceUseCase(components),
        ),
    )

    @Test
    fun `the list marks the bike for the next ride and can switch it`() = runTest {
        bikes.save(Bike(name = "MTB", profileId = MTB_ID))
        val viewModel = BikesViewModel(
            ObserveBikeChoiceUseCase(bikes),
            ObserveActivityProfilesUseCase(profiles),
            SelectBikeUseCase(bikes),
            ObserveComponentWearUseCase(components),
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
    fun `a folding bike starts on 20 inch wheels unless the rider chose a wheel`() = runTest {
        val viewModel = editor(BikeTarget.New)

        viewModel.onEvent(BikeEditorUiEvent.TypePicked(FOLDING_ID))
        assertEquals(
            ProfileDefaults.FOLDING_WHEEL_CIRCUMFERENCE_MM.toString(),
            viewModel.uiState.value.wheelCircumferenceMm,
        )
        viewModel.onEvent(BikeEditorUiEvent.TypePicked(ROAD_ID))
        assertEquals(ProfileDefaults.WHEEL_CIRCUMFERENCE_MM.toString(), viewModel.uiState.value.wheelCircumferenceMm)

        viewModel.onEvent(BikeEditorUiEvent.WheelPicked(MTB_WHEEL_MM))
        viewModel.onEvent(BikeEditorUiEvent.TypePicked(FOLDING_ID))
        assertEquals(MTB_WHEEL_MM.toString(), viewModel.uiState.value.wheelCircumferenceMm)
    }

    @Test
    fun `onboarding a folding bike also starts it on 20 inch wheels`() = runTest {
        val viewModel = editor(BikeTarget.First)

        viewModel.onEvent(BikeEditorUiEvent.TypePicked(FOLDING_ID))
        assertEquals(
            ProfileDefaults.FOLDING_WHEEL_CIRCUMFERENCE_MM.toString(),
            viewModel.uiState.value.wheelCircumferenceMm,
        )
    }

    @Test
    fun `changing the type of a saved bike keeps its wheel`() = runTest {
        val viewModel = editor(BikeTarget.Existing(1))

        viewModel.onEvent(BikeEditorUiEvent.TypePicked(FOLDING_ID))
        assertEquals(ProfileDefaults.WHEEL_CIRCUMFERENCE_MM.toString(), viewModel.uiState.value.wheelCircumferenceMm)
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

    private fun componentEditor(target: ComponentTarget) = ComponentEditorViewModel(
        target,
        ComponentEditorUseCases(
            GetComponentUseCase(components),
            ObserveComponentWearUseCase(components),
            SaveComponentUseCase(components),
            ReplaceComponentUseCase(components, clock),
            DeleteComponentUseCase(components),
        ),
        clock,
    )

    @Test
    fun `a new part takes its type's limit, km typed with a comma, and shows as due on the bike`() = runTest {
        val editor = componentEditor(ComponentTarget(bikeId = 1))
        assertEquals("3000", editor.uiState.value.limitKm)

        editor.onEvent(ComponentEditorUiEvent.TypePicked(ComponentType.BRAKE_PADS))
        assertEquals("2000", editor.uiState.value.limitKm)
        editor.onEvent(ComponentEditorUiEvent.InitialChanged("1999,5"))
        editor.effects.test {
            editor.onEvent(ComponentEditorUiEvent.Save)
            assertEquals(ComponentEditorEffect.Done, awaitItem())
        }
        components.rides.value = listOf(FakeBikeRide(bikeId = 1, startedAtMs = clock.nowMs(), distanceM = 1_000.0))

        val bike = editor(BikeTarget.Existing(1))
        val part = bike.uiState.value.components.single()
        assertEquals(ComponentType.BRAKE_PADS, part.component.type)
        assertEquals(2_000_500.0, part.distanceM)
        assertTrue(part.isDue)
        assertEquals(1_000.0, bike.uiState.value.bikeDistanceM)
    }

    @Test
    fun `a part can be marked replaced and deleted`() = runTest {
        val id = components.save(
            BikeComponent(bikeId = 1, type = ComponentType.CHAIN, installedAtMs = 0, initialDistanceM = 3_500_000.0),
        )
        val editor = componentEditor(ComponentTarget(bikeId = 1, componentId = id))
        assertFalse(editor.uiState.value.isNew)
        assertEquals(3_500_000.0, editor.uiState.value.distanceM)

        editor.effects.test {
            editor.onEvent(ComponentEditorUiEvent.Replaced)
            assertEquals(ComponentEditorEffect.Done, awaitItem())
        }
        assertEquals(0.0, components.get(id)?.initialDistanceM)

        editor.onEvent(ComponentEditorUiEvent.AskDelete)
        editor.effects.test {
            editor.onEvent(ComponentEditorUiEvent.ConfirmDelete)
            assertEquals(ComponentEditorEffect.Done, awaitItem())
        }
        assertEquals(null, components.get(id))
    }

    private companion object {
        const val ROAD_ID = 1L
        const val MTB_ID = 3L
        const val MTB_WHEEL_MM = 2_273
        const val FOLDING_ID = 6L
        const val CLOCK_START_MS = 1_000L
    }
}
