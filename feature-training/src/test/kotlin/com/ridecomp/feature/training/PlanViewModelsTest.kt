package com.ridecomp.feature.training

import app.cash.turbine.test
import com.ridecomp.core.model.BuiltInPlan
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeTrainingPlanRepository
import com.ridecomp.core.usecase.DeleteCustomPlanUseCase
import com.ridecomp.core.usecase.GetCustomPlanUseCase
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObservePlansUseCase
import com.ridecomp.core.usecase.PlanField
import com.ridecomp.core.usecase.SaveCustomPlanUseCase
import com.ridecomp.core.usecase.StartPlanUseCase
import com.ridecomp.core.usecase.StopPlanUseCase
import com.ridecomp.core.usecase.TrainingCalendar
import com.ridecomp.feature.training.model.PlanRefArg
import com.ridecomp.feature.training.viewmodel.PlanBuilderEffect
import com.ridecomp.feature.training.viewmodel.PlanBuilderUiEvent
import com.ridecomp.feature.training.viewmodel.PlanBuilderViewModel
import com.ridecomp.feature.training.viewmodel.PlanDetailEffect
import com.ridecomp.feature.training.viewmodel.PlanDetailUiEvent
import com.ridecomp.feature.training.viewmodel.PlanDetailUseCases
import com.ridecomp.feature.training.viewmodel.PlanDetailViewModel
import com.ridecomp.feature.training.viewmodel.PlansViewModel
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelsTest {
    private val repository = FakeTrainingPlanRepository()
    private val calendar = TrainingCalendar(FakeClock()) { ZoneOffset.UTC }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun detail(ref: PlanRef) = PlanDetailViewModel(
        ref,
        PlanDetailUseCases(
            ObservePlansUseCase(repository),
            ObserveActivePlanUseCase(repository, calendar),
            StartPlanUseCase(repository, calendar),
            StopPlanUseCase(repository),
            DeleteCustomPlanUseCase(repository),
        ),
    )

    @Test
    fun `a ready-made plan can be started and stopped`() = runTest {
        val ref = PlanRef.BuiltIn(BuiltInPlan.GRAN_FONDO)
        val viewModel = detail(ref)
        assertFalse(viewModel.uiState.value.isFollowed)

        viewModel.onEvent(PlanDetailUiEvent.Start)
        assertTrue(viewModel.uiState.value.isFollowed)
        assertEquals(0, viewModel.uiState.value.active?.today?.weekIndex)

        val list = PlansViewModel(ObservePlansUseCase(repository), ObserveActivePlanUseCase(repository, calendar))
        assertEquals(ref, list.uiState.value.active?.plan?.ref)

        viewModel.onEvent(PlanDetailUiEvent.Stop)
        assertFalse(viewModel.uiState.value.isFollowed)
    }

    @Test
    fun `the builder needs a ride, saves a repeating week and the plan can be deleted`() = runTest {
        val builder = PlanBuilderViewModel(null, GetCustomPlanUseCase(repository), SaveCustomPlanUseCase(repository))
        repeat(7) { day ->
            builder.onEvent(PlanBuilderUiEvent.EditDay(day))
            builder.onEvent(PlanBuilderUiEvent.DaySet(null))
        }
        builder.onEvent(PlanBuilderUiEvent.Save)
        assertTrue(PlanField.SESSIONS in builder.uiState.value.invalidFields)

        builder.onEvent(PlanBuilderUiEvent.EditDay(2))
        builder.onEvent(PlanBuilderUiEvent.DaySet(PlanSession(Intensity.THRESHOLD, 90)))
        builder.onEvent(PlanBuilderUiEvent.WeeksPicked(6))
        builder.onEvent(PlanBuilderUiEvent.NameChanged("Borobudur"))
        builder.effects.test {
            builder.onEvent(PlanBuilderUiEvent.Save)
            assertEquals(PlanBuilderEffect.Saved(PlanRef.Custom(1)), awaitItem())
        }
        val saved = repository.custom.value.single()
        assertEquals(6, saved.weekCount)
        assertEquals(PlanSession(Intensity.THRESHOLD, 90), saved.week.days[2])

        val viewModel = detail(PlanRef.Custom(1))
        viewModel.onEvent(PlanDetailUiEvent.AskDelete)
        viewModel.effects.test {
            viewModel.onEvent(PlanDetailUiEvent.ConfirmDelete)
            assertEquals(PlanDetailEffect.Deleted, awaitItem())
        }
        assertTrue(repository.custom.value.isEmpty())
    }

    @Test
    fun `plan references go through navigation unchanged`() {
        listOf(PlanRef.BuiltIn(BuiltInPlan.RACE_PREP), PlanRef.Custom(12)).forEach { ref ->
            assertEquals(ref, PlanRefArg.decode(PlanRefArg.encode(ref)))
        }
        assertNull(PlanRefArg.decode("builtin:NOPE"))
    }
}
