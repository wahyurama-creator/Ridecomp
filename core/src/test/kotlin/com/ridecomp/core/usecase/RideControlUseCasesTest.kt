package com.ridecomp.core.usecase

import app.cash.turbine.test
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBatteryLevelSource
import com.ridecomp.core.testing.FakeLocationSource
import com.ridecomp.core.testing.FakeRideController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RideControlUseCasesTest {
    private val controller = FakeRideController()

    @Test
    fun `start and actions go to the controller`() {
        StartRideUseCase(controller)(profileId = 3)
        PerformRideActionUseCase(controller)(RideAction.LAP)

        assertEquals(listOf(FakeRideController.START, "LAP"), controller.commands)
        assertEquals(3, controller.startedProfileId)
    }

    @Test
    fun `recovery choices`() = runTest {
        val repository = FakeActivityRepository()
        val id = repository.create(NewActivity("u", "Ride", 0))
        val handle = HandleRecoveredRideUseCase(controller, repository)

        handle(id, RecoveryChoice.RESUME)
        handle(id, RecoveryChoice.SAVE)
        handle(id, RecoveryChoice.DISCARD)

        assertEquals(
            listOf(
                "${FakeRideController.RESUME_PREFIX}$id",
                "${FakeRideController.RESUME_PREFIX}$id",
                "FINISH",
                "SAVE",
            ),
            controller.commands,
        )
        assertNull(repository.get(id))
    }

    @Test
    fun `gps status without permission or with gps off never starts gps`() = runTest {
        val noPermission = FakeLocationSource(isPermissionGrantedValue = false)
        ObserveGpsStatusUseCase(noPermission)().test {
            assertEquals(GpsStatus.NoPermission, awaitItem())
            awaitComplete()
        }
        val gpsOff = FakeLocationSource(isGpsEnabledValue = false)
        ObserveGpsStatusUseCase(gpsOff)().test {
            assertEquals(GpsStatus.Disabled, awaitItem())
            awaitComplete()
        }
        assertEquals(0, noPermission.activeCollectors + gpsOff.activeCollectors)
    }

    @Test
    fun `gps availability never starts the gps`() {
        val source = FakeLocationSource()
        assertEquals(GpsAvailability.READY, CheckGpsAvailabilityUseCase(source)())
        assertEquals(
            GpsAvailability.DISABLED,
            CheckGpsAvailabilityUseCase(FakeLocationSource(isGpsEnabledValue = false))(),
        )
        assertEquals(0, source.activeCollectors)
    }

    @Test
    fun `gps status goes from searching to ready and stops gps when not collected`() = runTest {
        val source = FakeLocationSource()

        ObserveGpsStatusUseCase(source)().test {
            assertEquals(GpsStatus.Searching(), awaitItem())
            source.fixesFlow.emit(LocationFix(0, -7.0, 110.0, null, 80.0, null))
            assertEquals(GpsStatus.Searching(80.0), awaitItem())
            source.fixesFlow.emit(LocationFix(1, -7.0, 110.0, null, 6.0, null))
            assertEquals(GpsStatus.Ready(6.0), awaitItem())
            assertEquals(1, source.activeCollectors)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, source.activeCollectors)
    }

    @Test
    fun `battery and live state are exposed`() {
        assertEquals(80, ReadBatteryUseCase(FakeBatteryLevelSource())()?.percent)
        val observe = ObserveLiveRideUseCase(controller)
        assertFalse(observe().value.isRideInProgress())
        controller.live.value = LiveRide(status = RideStatus.FINISHED)
        assertTrue(observe().value.isRideInProgress())
        assertNull(observe.activeActivityId().value)
    }
}
