package com.ridecomp.core.usecase

import com.ridecomp.core.engine.training.LoadMethod
import com.ridecomp.core.engine.training.RideLoad
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeTrainingRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** Incremental daily trend: adding and deleting rides (T4.6). */
class TrainingUseCasesTest {
    private val dispatcher = StandardTestDispatcher()
    private val activities = FakeActivityRepository()
    private val training = FakeTrainingRepository(activities)

    /** "Today" is 2026-11-10 UTC. */
    private val clock =
        FakeClock(nowMs = LocalDate.of(2026, 11, 10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    private val calendar = TrainingCalendar(clock) { ZoneOffset.UTC }
    private val recompute = RecomputeTrainingUseCase(training, calendar, TestDispatcherProvider(dispatcher))

    private suspend fun ride(id: Long, day: Int, load: Double) {
        val startedAtMs = LocalDate.of(2026, 11, day).atTime(7, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        activities.put(
            Activity(id, "u$id", "Ride $id", ActivityStatus.FINISHED, startedAtMs, null, RideTotals(), startedAtMs),
        )
        training.setRideLoad(id, RideLoad(load, LoadMethod.HEART_RATE, null))
        recompute(startedAtMs)
    }

    @Test
    fun `days are filled up to today and recomputed when a ride is deleted`() = runTest(dispatcher) {
        ride(id = 1, day = 2, load = 70.0)
        ride(id = 2, day = 5, load = 120.0)

        val days = training.observeDays(LocalDate.of(2026, 11, 1)).first()
        assertEquals((2..10).map { LocalDate.of(2026, 11, it) }, days.map { it.date })
        assertEquals(120.0, days.single { it.date == LocalDate.of(2026, 11, 5) }.load)
        val withBoth = days.last().ctl

        DeleteActivityUseCase(activities, recompute)(2)

        val after = training.observeDays(LocalDate.of(2026, 11, 1)).first()
        assertEquals(0.0, after.single { it.date == LocalDate.of(2026, 11, 5) }.load)
        assertEquals(true, after.last().ctl < withBoth)
        assertEquals(LocalDate.of(2026, 11, 10), after.last().date)
    }

    @Test
    fun `days between rides are filled and fitness decays instead of restarting from zero`() = runTest(dispatcher) {
        // The first ride is analyzed on its own day, the next one 8 days later.
        clock.setWallClock(LocalDate.of(2026, 11, 2).atTime(20, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        ride(id = 1, day = 2, load = 100.0)
        clock.setWallClock(LocalDate.of(2026, 11, 10).atTime(20, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        ride(id = 2, day = 10, load = 0.0)

        val days = training.observeDays(LocalDate.of(2026, 11, 1)).first()

        assertEquals((2..10).map { LocalDate.of(2026, 11, it) }, days.map { it.date })
        val afterFirst = days.first().ctl
        assertEquals(afterFirst * (1 - 1 / 42.0).let { f -> Math.pow(f, 8.0) }, days.last().ctl, 1e-9)
    }

    @Test
    fun `deleting the only ride clears its trend`() = runTest(dispatcher) {
        ride(id = 1, day = 9, load = 50.0)

        DeleteActivityUseCase(activities, recompute)(1)

        assertEquals(emptyList(), training.observeDays(LocalDate.of(2026, 11, 1)).first())
    }

    @Test
    fun `the overview gives this week's totals and the methods used`() = runTest(dispatcher) {
        ride(id = 1, day = 9, load = 50.0)
        ride(id = 2, day = 10, load = 30.0)

        val overview = ObserveTrainingUseCase(training, calendar)().first()

        assertEquals(2, overview.week.rides)
        assertEquals(80.0, overview.week.load)
        assertEquals(listOf(LoadMethod.HEART_RATE), overview.methods)
        assertEquals(LocalDate.of(2026, 11, 10), overview.today?.date)
    }
}
