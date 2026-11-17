package com.ridecomp.core.engine.voice

import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RecorderOutput
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.LapTrigger
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnnouncementTrackerTest {
    private fun output(distanceM: Double, status: RideStatus = RideStatus.RECORDING) =
        RecorderOutput(LiveRide(status = status, distanceM = distanceM, movingMs = 120_000, avgSpeedMps = 8.0))

    @Test
    fun `every kilometre is announced once, not the ones before a resume`() {
        val tracker = AnnouncementTracker(VoiceSettings(everyUnits = 1), UnitSystem.METRIC)

        // Recovered at 3.4 km: the starting point, nothing said.
        assertTrue(tracker.onOutput(output(3_400.0)).isEmpty())
        assertTrue(tracker.onOutput(output(3_990.0)).isEmpty())
        val fourth = tracker.onOutput(output(4_001.0)).single()
        assertEquals(4_000.0, assertIs<Announcement.Distance>(fourth).distanceM)
        assertTrue(tracker.onOutput(output(4_500.0)).isEmpty())
    }

    @Test
    fun `steps follow the unit system and the chosen interval`() {
        val tracker = AnnouncementTracker(VoiceSettings(everyUnits = 5), UnitSystem.IMPERIAL)
        tracker.onOutput(output(0.0))

        assertTrue(tracker.onOutput(output(8_000.0)).isEmpty())
        val five = assertIs<Announcement.Distance>(tracker.onOutput(output(8_100.0)).single())
        assertEquals(5 * 1_609.344, five.distanceM, 1e-6)
    }

    @Test
    fun `nothing is said while paused, the next kilometre still is`() {
        val tracker = AnnouncementTracker(VoiceSettings(everyUnits = 1), UnitSystem.METRIC)
        tracker.onOutput(output(900.0))

        assertTrue(tracker.onOutput(output(1_050.0, RideStatus.AUTO_PAUSED)).isEmpty())
        assertEquals(1, tracker.onOutput(output(2_010.0)).size)
    }

    @Test
    fun `laps and reminders are announced, laps can be turned off`() {
        val lap = Lap(0, 0, 60_000, 1_000.0, 60, 8.0, 10.0, 0.0, LapTrigger.MANUAL)
        val withLap = RecorderOutput(
            LiveRide(status = RideStatus.RECORDING),
            events = listOf(RideEvent(1, RideEventType.REMINDER_DRINK)),
            laps = listOf(lap),
        )

        val all = AnnouncementTracker(VoiceSettings(everyUnits = 1), UnitSystem.METRIC).onOutput(withLap)
        assertEquals(listOf(Announcement.LapDone(1, lap), Announcement.DrinkReminder), all)

        val noLaps = AnnouncementTracker(VoiceSettings(everyUnits = 1, isLapEnabled = false), UnitSystem.METRIC)
        assertEquals(listOf<Announcement>(Announcement.DrinkReminder), noLaps.onOutput(withLap))
    }

    @Test
    fun `off says nothing`() {
        val tracker = AnnouncementTracker(VoiceSettings(), UnitSystem.METRIC)

        tracker.onOutput(output(0.0))
        assertTrue(tracker.onOutput(output(5_000.0)).isEmpty())
    }
}
