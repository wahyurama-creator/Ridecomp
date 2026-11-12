package com.ridecomp.core.engine.recording

import com.ridecomp.core.engine.metrics.AltitudeSource
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeIdGenerator
import com.ridecomp.core.testing.FakeRideNameProvider
import com.ridecomp.core.testing.RecordingLogger
import com.ridecomp.core.util.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private const val METER_IN_LAT_DEG = 1.0 / 111_195.0
private const val T0 = 1_767_225_600_000L

class RecordingSessionTest {
    private val repository = FakeActivityRepository()
    private val logger = RecordingLogger()
    private val session = RecordingSession(repository, FakeIdGenerator(), FakeRideNameProvider(), logger)
    private val settings = RideSettings(drinkIntervalMin = null, eatIntervalMin = null)
    private var second = 0L

    private val now: Long get() = T0 + second * 1_000

    private suspend fun ride(seconds: Int, mps: Double = 8.0) {
        repeat(seconds) {
            second++
            session.onLocation(LocationFix(now, -7.0 + second * mps * METER_IN_LAT_DEG, 110.0, 100.0, 5.0, mps))
            session.tick(now)
        }
    }

    @Test
    fun `start creates a recording activity with a default name and start event`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)

        val id = assertNotNull(session.activityId)
        val activity = assertNotNull(repository.get(id))
        assertEquals(ActivityStatus.RECORDING, activity.status)
        assertEquals("Ride $T0", activity.name)
        assertEquals(listOf(RideEventType.START), repository.events[id]?.map { it.type })
    }

    @Test
    fun `points are written in 5 second batches with a checkpoint each time`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)
        val id = requireNotNull(session.activityId)

        ride(4)
        assertEquals(0, repository.getSampleCount(id))

        ride(8)
        assertTrue(repository.getSampleCount(id) in 5..12)
        assertTrue(repository.checkpointCount >= 1)
        assertTrue(requireNotNull(repository.get(id)).totals.distanceM > 0.0)
    }

    @Test
    fun `starting twice is refused`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)

        val second = session.start(settings, AltitudeSource.GPS, now)

        assertEquals(Outcome.Failure(SessionError.AlreadyRecording), second)
    }

    @Test
    fun `finish flushes everything and save marks the ride finished`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)
        val id = requireNotNull(session.activityId)
        ride(7)

        session.perform(RideAction.PAUSE, now)
        session.perform(RideAction.FINISH, now)
        assertEquals(7, repository.getSampleCount(id))
        assertEquals(ActivityStatus.PAUSED, repository.get(id)?.status)

        session.perform(RideAction.SAVE, now)
        assertEquals(ActivityStatus.FINISHED, repository.get(id)?.status)
        assertNull(session.activityId)
        assertEquals(RideStatus.IDLE, session.status)
        assertEquals(1, repository.getLaps(id).size)
    }

    @Test
    fun `discard deletes the ride`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)
        val id = requireNotNull(session.activityId)
        ride(3)

        session.perform(RideAction.PAUSE, now)
        session.perform(RideAction.DISCARD, now)

        assertNull(repository.get(id))
        assertNull(session.activityId)
    }

    @Test
    fun `a broken database never stops recording and nothing is lost once it recovers`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)
        val id = requireNotNull(session.activityId)

        repository.isFailing = true
        ride(12)
        assertEquals(RideStatus.RECORDING, session.status)
        assertTrue(logger.entries.any { it.message.contains("Database write failed") })

        repository.isFailing = false
        ride(6)
        assertEquals(18, repository.getSampleCount(id))
    }

    @Test
    fun `recovered ride continues from its checkpoint`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)
        val id = requireNotNull(session.activityId)
        ride(12)
        val checkpoint = requireNotNull(repository.get(id))

        val restarted = RecordingSession(repository, FakeIdGenerator(), FakeRideNameProvider(), logger)
        restarted.resume(checkpoint, settings, AltitudeSource.GPS, RideStatus.PAUSED, now + 60_000)
        val output = assertNotNull(restarted.tick(now + 61_000))

        assertEquals(RideStatus.PAUSED, output.live.status)
        assertEquals(checkpoint.totals.distanceM, output.live.distanceM)
        assertEquals(id, restarted.activityId)
        assertTrue(RideEventType.GAP in repository.events[id].orEmpty().map { it.type })
    }

    @Test
    fun `commands without a ride are rejected`() = runTest {
        assertEquals(Outcome.Failure(SessionError.NoActiveRide), session.perform(RideAction.PAUSE, now))
        assertNull(session.tick(now))
    }

    @Test
    fun `invalid commands are reported, not thrown`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)

        val result = session.perform(RideAction.FINISH, now)

        assertIs<Outcome.Failure<SessionError.Rejected>>(result)
    }

    @Test
    fun `emergency checkpoint writes pending points immediately`() = runTest {
        session.start(settings, AltitudeSource.GPS, now)
        val id = requireNotNull(session.activityId)
        ride(3)

        session.emergencyCheckpoint(now)

        assertEquals(3, repository.getSampleCount(id))
    }
}
