package com.ridecomp.core.usecase

import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.PeriodTotals
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** 13-UI-SCREENS #11: "BULAN INI" counts finished rides of the calendar month in the phone's zone. */
class MonthTotalsUseCaseTest {
    private val jakarta = ZoneId.of("Asia/Jakarta")

    private fun ms(day: Int, hour: Int, month: Int = 11) =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, jakarta).toInstant().toEpochMilli()

    private fun ride(id: Long, startedAtMs: Long, distanceM: Double, status: ActivityStatus = ActivityStatus.FINISHED) =
        Activity(
            id = id,
            uuid = "u$id",
            name = "r$id",
            status = status,
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs,
            totals = RideTotals(distanceM = distanceM, ascentM = distanceM / 100),
            lastCheckpointAtMs = startedAtMs,
        )

    @Test
    fun `only finished rides from the first of the month count`() = runTest {
        val repository = FakeActivityRepository().apply {
            put(ride(1, ms(day = 31, hour = 23, month = 10), 50_000.0))
            put(ride(2, ms(day = 1, hour = 0), 10_000.0))
            put(ride(3, ms(day = 12, hour = 6), 20_000.0))
            put(ride(4, ms(day = 13, hour = 6), 5_000.0, status = ActivityStatus.RECORDING))
        }

        val totals = ObserveMonthTotalsUseCase(repository, FakeClock(ms(day = 13, hour = 9)), { jakarta })().first()

        assertEquals(PeriodTotals(rides = 2, distanceM = 30_000.0, ascentM = 300.0), totals)
    }
}
