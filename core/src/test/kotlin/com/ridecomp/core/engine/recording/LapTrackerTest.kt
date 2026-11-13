package com.ridecomp.core.engine.recording

import com.ridecomp.core.model.LapTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun progress(tSec: Long, distanceM: Double, movingSec: Long = tSec, ascentM: Double = 0.0) =
    RideProgress(timeMs = tSec * 1_000, distanceM = distanceM, movingMs = movingSec * 1_000, ascentM = ascentM)

class LapTrackerTest {
    @Test
    fun `manual lap closes the running lap with averages`() {
        val tracker = LapTracker()
        tracker.start(progress(0, 0.0))
        tracker.onTick(progress(50, 400.0), speedMps = 9.0)

        val lap = requireNotNull(tracker.manualLap(progress(100, 800.0, movingSec = 100, ascentM = 12.0)))

        assertEquals(0, lap.index)
        assertEquals(800.0, lap.distanceM)
        assertEquals(100L, lap.movingS)
        assertEquals(8.0, lap.avgSpeedMps)
        assertEquals(9.0, lap.maxSpeedMps)
        assertEquals(12.0, lap.ascentM)
        assertEquals(LapTrigger.MANUAL, lap.trigger)
    }

    @Test
    fun `auto lap is cut exactly at the lap distance and the overshoot goes to the next lap`() {
        val tracker = LapTracker(autoLapM = 1_000.0)
        tracker.start(progress(0, 0.0))

        val laps = (1L..250L).mapNotNull { tracker.onTick(progress(it, it * 8.5), speedMps = 8.5) }

        assertEquals(2, laps.size)
        assertEquals(listOf(1_000.0, 1_000.0), laps.map { it.distanceM })
        assertEquals(listOf(0, 1), laps.map { it.index })
        assertEquals(LapTrigger.DISTANCE, laps.first().trigger)
        val running = requireNotNull(tracker.status(progress(250, 250 * 8.5)))
        assertEquals(3, running.number)
        assertEquals(125.0, running.distanceM, absoluteTolerance = 1e-9)
    }

    @Test
    fun `no auto lap when disabled`() {
        val tracker = LapTracker(autoLapM = null)
        tracker.start(progress(0, 0.0))

        assertNull(tracker.onTick(progress(1_000, 50_000.0), speedMps = 10.0))
    }

    @Test
    fun `finish closes the last lap but drops an empty one`() {
        val tracker = LapTracker()
        tracker.start(progress(0, 0.0))
        tracker.manualLap(progress(60, 500.0))

        assertNull(tracker.finish(progress(60, 500.0)))
    }

    @Test
    fun `finish returns the final partial lap`() {
        val tracker = LapTracker()
        tracker.start(progress(0, 0.0))

        val lap = assertNotNull(tracker.finish(progress(30, 240.0)))

        assertEquals(240.0, lap.distanceM)
    }

    @Test
    fun `lap status reports elapsed and moving time separately`() {
        val tracker = LapTracker()
        tracker.start(progress(0, 0.0))

        val status = requireNotNull(tracker.status(progress(120, 600.0, movingSec = 100)))

        assertEquals(120_000L, status.elapsedMs)
        assertEquals(100_000L, status.movingMs)
        assertEquals(6.0, status.avgSpeedMps)
    }

    @Test
    fun `nothing happens before start`() {
        val tracker = LapTracker(autoLapM = 1_000.0)

        assertNull(tracker.onTick(progress(10, 5_000.0), speedMps = 5.0))
        assertNull(tracker.manualLap(progress(10, 5_000.0)))
        assertNull(tracker.status(progress(10, 5_000.0)))
        assertNull(tracker.finish(progress(10, 5_000.0)))
    }

    @Test
    fun `restart from recovery continues lap numbering`() {
        val tracker = LapTracker()
        tracker.start(progress(0, 0.0), startIndex = 3)

        assertEquals(3, requireNotNull(tracker.manualLap(progress(10, 80.0))).index)
    }

    @Test
    fun `zero moving time gives zero average speed`() {
        val tracker = LapTracker()
        tracker.start(progress(0, 0.0))

        assertEquals(0.0, requireNotNull(tracker.manualLap(progress(10, 0.0, movingSec = 0))).avgSpeedMps)
    }

    @Test
    fun `auto lap projection compares the pace with the previous full lap`() {
        val tracker = LapTracker(autoLapM = 1_000.0)
        tracker.start(progress(0, 0.0))
        (1L..100L).forEach { tracker.onTick(progress(it, it * 10.0), speedMps = 10.0) }

        // Second lap: 500 m in 40 s → 80 s at this pace, 20 s faster than the 100 s first lap.
        val running = requireNotNull(tracker.status(progress(140, 1_500.0)))

        assertEquals(2, running.number)
        assertEquals(100_000L, running.previousLapMs)
        assertEquals(80_000L, running.projectedLapMs)
        assertEquals(-20_000L, running.projectedDeltaMs)
        // Too early to project; manual laps have no projection.
        assertNull(requireNotNull(tracker.status(progress(105, 1_050.0))).projectedLapMs)
        val manual = LapTracker().apply { start(progress(0, 0.0)) }
        assertNull(requireNotNull(manual.status(progress(60, 600.0))).projectedLapMs)
    }
}
