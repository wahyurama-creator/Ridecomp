package com.ridecomp.core.engine.training

import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.LiveSensors
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.RideStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetCoachTest {
    private fun live(seconds: Long, heartRate: Int? = null, power: Int? = null) = LiveRide(
        status = RideStatus.RECORDING,
        nowMs = seconds * 1_000,
        movingMs = seconds * 1_000,
        sensors = LiveSensors(heartRateBpm = heartRate, powerW = power),
    )

    @Test
    fun `zone bounds come from HR max and FTP`() {
        assertEquals(114 to 133, TargetConfig.heartRateBounds(Intensity.ENDURANCE, 190))
        assertEquals(null to 114, TargetConfig.heartRateBounds(Intensity.RECOVERY, 190))
        assertEquals(171 to null, TargetConfig.heartRateBounds(Intensity.VO2MAX, 190))
        assertEquals(189 to 221, TargetConfig.powerBounds(Intensity.THRESHOLD, 210))
    }

    @Test
    fun `heart rate first, then power, then feel`() {
        val coach = TargetCoach(PlanSession(Intensity.ENDURANCE, 60), hrMaxBpm = 190, ftpW = 200)

        assertEquals(TargetSource.HEART_RATE, coach.onLive(live(1, heartRate = 120, power = 150)).status.source)
        assertEquals(TargetSource.POWER, coach.onLive(live(2, power = 150)).status.source)
        val feel = coach.onLive(live(3)).status
        assertEquals(TargetSource.FEEL, feel.source)
        assertEquals(ZoneState.UNKNOWN, feel.state)
        assertNull(feel.lowValue)

        val noHrMax = TargetCoach(PlanSession(Intensity.ENDURANCE, 60), hrMaxBpm = null, ftpW = null)
        assertEquals(TargetSource.FEEL, noHrMax.onLive(live(1, heartRate = 120)).status.source)
    }

    @Test
    fun `time in zone counts moving time only while in zone`() {
        val coach = TargetCoach(PlanSession(Intensity.ENDURANCE, 60), hrMaxBpm = 190, ftpW = null)
        coach.onLive(live(0, heartRate = 120))
        coach.onLive(live(10, heartRate = 125))
        coach.onLive(live(20, heartRate = 150))
        val status = coach.onLive(live(30, heartRate = 120)).status

        assertEquals(ZoneState.IN, status.state)
        assertEquals(20_000, status.inZoneMs)
    }

    @Test
    fun `an easy session ridden too hard warns after 20 s, then once a minute`() {
        val coach = TargetCoach(PlanSession(Intensity.ENDURANCE, 60), hrMaxBpm = 190, ftpW = null)
        val alerts = (0L..100L).flatMap { second -> coach.onLive(live(second, heartRate = 150)).alerts }

        // At 20 s and 80 s.
        assertEquals(listOf(CoachAlert.TOO_HARD, CoachAlert.TOO_HARD), alerts)
    }

    @Test
    fun `a hard session does not warn, any session says when it is done once`() {
        val coach = TargetCoach(PlanSession(Intensity.VO2MAX, 1), hrMaxBpm = 190, ftpW = null)
        val alerts = (0L..90L).flatMap { second -> coach.onLive(live(second, heartRate = 120)).alerts }

        assertEquals(listOf(CoachAlert.SESSION_DONE), alerts)
        assertTrue(coach.onLive(live(91, heartRate = 120)).status.isDone)
    }
}
