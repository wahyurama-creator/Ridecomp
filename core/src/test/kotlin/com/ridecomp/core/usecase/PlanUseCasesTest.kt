package com.ridecomp.core.usecase

import com.ridecomp.core.model.BuiltInPlan
import com.ridecomp.core.model.BuiltInPlans
import com.ridecomp.core.model.CustomPlan
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.PlanWeek
import com.ridecomp.core.model.TrainingPlanLimits
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeTrainingPlanRepository
import com.ridecomp.core.util.Outcome
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class PlanUseCasesTest {
    private val repository = FakeTrainingPlanRepository()
    private val clock = FakeClock(LocalDate.of(2026, 11, 17).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    private val calendar = TrainingCalendar(clock) { ZoneOffset.UTC }
    private val week = PlanWeek(listOf(PlanSession(Intensity.ENDURANCE, 60), null, null, null, null, null, null))

    @Test
    fun `built-in plans have whole weeks and get harder then easier`() {
        BuiltInPlans.ALL.forEach { plan ->
            assertTrue(plan.weeks.all { it.days.size == TrainingPlanLimits.DAYS_PER_WEEK })
            assertTrue(plan.sessionCount > 0)
        }
        assertEquals(4, BuiltInPlans.plan(BuiltInPlan.ZONE2_BASE).weeks.size)
        assertEquals(8, BuiltInPlans.plan(BuiltInPlan.RACE_PREP).weeks.size)
    }

    @Test
    fun `today is day 1 of week 1 on the start day, then moves with the calendar`() = runTest {
        StartPlanUseCase(repository, calendar)(PlanRef.BuiltIn(BuiltInPlan.ZONE2_BASE))
        val active = ObserveActivePlanUseCase(repository, calendar)

        val first = active().first()!!.today!!
        assertEquals(0, first.weekIndex)
        assertEquals(0, first.dayIndex)
        assertEquals(PlanSession(Intensity.ENDURANCE, 45), first.session)

        clock.advanceBy(DAY_MS * 9)
        val tenth = active().first()!!.today!!
        assertEquals(1, tenth.weekIndex)
        assertEquals(2, tenth.dayIndex)

        clock.advanceBy(DAY_MS * 30)
        val after = active().first()!!
        assertNull(after.today)
    }

    @Test
    fun `plans list built-ins then the rider's own, deleting the followed one stops it`() = runTest {
        val id = (
            SaveCustomPlanUseCase(
                repository,
            )(CustomPlan(name = "  Borobudur  ", weekCount = 3, week = week)) as Outcome.Success
            ).value
        StartPlanUseCase(repository, calendar)(PlanRef.Custom(id))

        val plans = ObservePlansUseCase(repository)().first()
        assertEquals(BuiltInPlans.ALL.size + 1, plans.size)
        assertEquals("Borobudur", plans.last().name)
        assertEquals(3, plans.last().weeks.size)

        DeleteCustomPlanUseCase(repository)(id)
        assertNull(repository.active.value)
    }

    @Test
    fun `a plan needs weeks in range and at least one ride of a sensible length`() = runTest {
        val save = SaveCustomPlanUseCase(repository)
        val empty = PlanWeek(List(TrainingPlanLimits.DAYS_PER_WEEK) { null })

        val result = save(CustomPlan(name = "", weekCount = 0, week = empty))
        assertEquals(
            setOf(PlanField.WEEKS, PlanField.SESSIONS),
            assertIs<Outcome.Failure<InvalidPlan>>(result).error.fields,
        )

        val tooLong = PlanWeek(listOf(PlanSession(Intensity.TEMPO, 600)) + List(6) { null })
        assertIs<Outcome.Failure<InvalidPlan>>(save(CustomPlan(name = "", weekCount = 4, week = tooLong)))
    }

    @Test
    fun `a ride with the session stores today's session for recovery, one without clears it`() = runTest {
        val rideSession = RideSessionUseCase(repository, ObserveActivePlanUseCase(repository, calendar))
        StartPlanUseCase(repository, calendar)(PlanRef.BuiltIn(BuiltInPlan.ZONE2_BASE))

        assertEquals(PlanSession(Intensity.ENDURANCE, 45), rideSession.forNewRide(withSession = true))
        assertEquals(PlanSession(Intensity.ENDURANCE, 45), rideSession.forRecoveredRide())

        assertNull(rideSession.forNewRide(withSession = false))
        assertNull(rideSession.forRecoveredRide())
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
