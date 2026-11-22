package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.engine.metrics.AltitudeSource
import com.ridecomp.core.engine.route.GpxPoint
import com.ridecomp.core.engine.route.RouteBuilder
import com.ridecomp.core.model.BatteryMode
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.getOrNull
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val METER_IN_LAT_DEG = 1.0 / 111_195.0
private const val START_MS = 1_767_225_600_000L

/** Drives a [RideRecorder] like the service would: one fix and one tick per second. */
private class RideSimulator(
    settings: RideSettings = RideSettings(drinkIntervalMin = null, eatIntervalMin = null),
    context: RideContext = RideContext(),
) {
    val recorder = RideRecorder(settings, AltitudeSource.GPS, context = context)
    var second = 0L
    var northM = 0.0
    val events = mutableListOf<RideEventType>()
    val samples = mutableListOf<RideSample>()
    var lapCount = 0

    val nowMs: Long get() = START_MS + second * 1_000

    fun action(action: RideAction) {
        val output = requireNotNull(recorder.handle(action, nowMs).getOrNull()) { "$action rejected" }
        events += output.events.map { it.type }
        lapCount += output.laps.size
    }

    /** Rides [seconds] at [kmh] (0 = standing still). */
    fun ride(seconds: Int, kmh: Double, altitudeM: Double = 100.0) {
        repeat(seconds) {
            second++
            val mps = kmh / 3.6
            northM += mps
            recorder.onLocation(
                LocationFix(nowMs, -7.0 + northM * METER_IN_LAT_DEG, 110.0, altitudeM, 5.0, mps),
            )
            val output = recorder.tick(nowMs)
            events += output.events.map { it.type }
            lapCount += output.laps.size
            output.sample?.let(samples::add)
        }
    }

    fun live() = recorder.tick(nowMs).live
}

class RideRecorderTest {
    @Test
    fun `a stretch without gps on a followed route is measured along the route, not straight across`() {
        // L-shaped route: 500 m north, then 500 m east. The signal is lost from 400 m north to 200 m east
        // (300 m around the corner); straight across that is only 224 m.
        val meterInLonDeg = METER_IN_LAT_DEG / cos(Math.toRadians(7.0))
        val north = (0..5).map { GpxPoint(-7.0 + it * 100 * METER_IN_LAT_DEG, 110.0, null) }
        val east = (1..5).map { GpxPoint(north.last().latDeg, 110.0 + it * 100 * meterInLonDeg, null) }
        val route = RouteBuilder.build(north + east, "r", null, START_MS).points
        val recorder = RideRecorder(
            RideSettings(drinkIntervalMin = null, eatIntervalMin = null),
            AltitudeSource.GPS,
            context = RideContext(routeId = 1, routePoints = route),
        )
        recorder.handle(RideAction.START, START_MS)
        var nowMs = START_MS
        (1..200).forEach { second ->
            nowMs += 1_000
            val alongM = second * 5.0
            val isLost = alongM > 400.0 && alongM < 700.0
            if (!isLost) {
                val (lat, lon) = if (alongM <= 500.0) {
                    -7.0 + alongM * METER_IN_LAT_DEG to 110.0
                } else {
                    north.last().latDeg to 110.0 + (alongM - 500.0) * meterInLonDeg
                }
                recorder.onLocation(LocationFix(nowMs, lat, lon, 100.0, 5.0, 5.0))
            }
            recorder.tick(nowMs)
        }
        val distanceM = recorder.tick(nowMs).live.distanceM
        assertTrue(distanceM in 980.0..1_010.0, "distance $distanceM m (straight across would give ~924 m)")
    }

    @Test
    fun `a barometer that reports only every few seconds adds no climb on a flat road`() {
        // Flat road: GPS says 100 m; the uncalibrated barometer altitude is 40 m (an offset of 60 m), and the
        // phone delivers a pressure reading only every 5 s (sensor batching / OEM throttling).
        val recorder =
            RideRecorder(RideSettings(drinkIntervalMin = null, eatIntervalMin = null), AltitudeSource.BAROMETER)
        recorder.handle(RideAction.START, START_MS)
        var northM = 0.0
        var nowMs = START_MS
        repeat(600) { second ->
            nowMs += 1_000
            northM += 6.0
            if (second % 5 == 0) recorder.onAltitude(40.0, nowMs)
            recorder.onLocation(LocationFix(nowMs, -7.0 + northM * METER_IN_LAT_DEG, 110.0, 100.0, 5.0, 6.0))
            recorder.tick(nowMs)
        }
        val elevation = recorder.tick(nowMs).live.elevation
        assertTrue(elevation.ascentM < 5.0, "ascent ${elevation.ascentM} m on a flat road")
    }

    @Test
    fun `following a route reports distance to go and an off route event when the rider leaves it`() {
        // Route: 1 km north from the start, then 1 km east. The rider keeps going north.
        val north = (0..10).map { GpxPoint(-7.0 + it * 100 * METER_IN_LAT_DEG, 110.0, null) }
        val east = (1..10).map { GpxPoint(north.last().latDeg, 110.0 + it * 100 * METER_IN_LAT_DEG, null) }
        val route = RouteBuilder.build(north + east, "r", null, START_MS).points
        val ride = RideSimulator(context = RideContext(routeId = 7, routePoints = route))
        ride.action(RideAction.START)

        ride.ride(seconds = 90, kmh = 20.0)
        val onRoute = assertNotNull(ride.live().route)
        assertFalse(onRoute.isOffRoute)
        assertTrue(onRoute.distanceRemainingM in 1_450.0..1_550.0, "to go ${onRoute.distanceRemainingM}")
        assertEquals(7, ride.live().routeId)

        ride.ride(seconds = 120, kmh = 20.0)
        assertTrue(RideEventType.OFF_ROUTE in ride.events)
        assertTrue(assertNotNull(ride.live().route).isOffRoute)
    }

    @Test
    fun `a ride with a traffic light auto pauses and resumes without hands`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)

        sim.ride(60, kmh = 28.8)
        sim.ride(40, kmh = 0.0)
        sim.ride(60, kmh = 28.8)

        assertEquals(
            listOf(RideEventType.START, RideEventType.AUTO_PAUSE, RideEventType.AUTO_RESUME),
            sim.events,
        )
        assertEquals(RideStatus.RECORDING, sim.recorder.currentStatus)
    }

    @Test
    fun `moving time excludes the auto pause and distance matches the route`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(60, kmh = 28.8)
        sim.ride(40, kmh = 0.0)
        sim.ride(60, kmh = 28.8)

        val live = sim.live()

        assertEquals(960.0, live.distanceM, absoluteTolerance = 16.0)
        assertTrue(live.movingMs in 120_000L..130_000L, "moving ${live.movingMs}")
        assertTrue(live.elapsedMs >= 160_000L)
    }

    @Test
    fun `auto pause starts when the rider stopped, not after the detection delay`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(60, kmh = 28.8)
        val stopSecond = sim.second
        sim.ride(40, kmh = 0.0)

        val pause = requireNotNull(sim.recorder.tick(sim.nowMs).live.movingMs)

        // Moving time ends within the speed smoothing lag of the real stop, not 5 s later.
        assertTrue(pause <= (stopSecond + 8) * 1_000, "moving $pause stop ${stopSecond * 1_000}")
    }

    @Test
    fun `a short roll that does not resume adds no distance`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(30, kmh = 20.0)
        sim.ride(30, kmh = 0.0)
        val distanceWhilePaused = sim.live().distanceM

        sim.ride(1, kmh = 10.0)
        sim.ride(20, kmh = 0.0)

        assertEquals(RideStatus.AUTO_PAUSED, sim.recorder.currentStatus)
        assertEquals(distanceWhilePaused, sim.live().distanceM)
    }

    @Test
    fun `exactly one record per second and none while paused`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(30, kmh = 20.0)
        sim.action(RideAction.PAUSE)
        sim.ride(30, kmh = 0.0)
        sim.action(RideAction.RESUME)
        sim.ride(30, kmh = 20.0)

        val times = sim.samples.map { it.timeMs }
        assertEquals(60, times.size)
        assertEquals(times.sorted(), times)
        assertEquals(times.size, times.toSet().size)
        assertTrue(times.all { it % 1_000 == 0L })
    }

    @Test
    fun `manual pause is never resumed automatically`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(10, kmh = 20.0)
        sim.action(RideAction.PAUSE)

        sim.ride(60, kmh = 25.0)

        assertEquals(RideStatus.PAUSED, sim.recorder.currentStatus)
    }

    @Test
    fun `finish is rejected while recording and accepted from pause with a final lap`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(20, kmh = 20.0)

        assertIs<Outcome.Failure<InvalidRideCommand>>(sim.recorder.handle(RideAction.FINISH, sim.nowMs))

        sim.action(RideAction.PAUSE)
        sim.action(RideAction.FINISH)
        assertEquals(RideStatus.FINISHED, sim.recorder.currentStatus)
        assertEquals(1, sim.lapCount)
        assertEquals(RideEventType.STOP, sim.events.last())
    }

    @Test
    fun `lap button and auto lap both close laps`() {
        val sim = RideSimulator(RideSettings(autoLapM = 500.0, drinkIntervalMin = null, eatIntervalMin = null))
        sim.action(RideAction.START)
        sim.ride(30, kmh = 36.0)
        sim.action(RideAction.LAP)
        sim.ride(60, kmh = 36.0)

        assertEquals(2, sim.lapCount)
        assertEquals(2, sim.events.count { it == RideEventType.LAP })
        assertNotNull(sim.live().lap)
    }

    @Test
    fun `idle prompt shows after long auto pause and keep riding hides it`() {
        val sim = RideSimulator(RideSettings(idlePromptMin = 1, drinkIntervalMin = null, eatIntervalMin = null))
        sim.action(RideAction.START)
        sim.ride(20, kmh = 20.0)
        sim.ride(90, kmh = 0.0)

        assertTrue(sim.live().isIdlePromptVisible)
        sim.action(RideAction.KEEP_RIDING)
        assertTrue(!sim.live().isIdlePromptVisible)
        assertEquals(RideStatus.AUTO_PAUSED, sim.recorder.currentStatus)
    }

    @Test
    fun `drink reminder appears on moving time and can be acknowledged`() {
        val sim = RideSimulator(RideSettings(drinkIntervalMin = 1, eatIntervalMin = null))
        sim.action(RideAction.START)
        sim.ride(62, kmh = 20.0)

        assertNotNull(sim.live().reminder)
        sim.action(RideAction.ACK_REMINDER)
        assertNull(sim.live().reminder)
        assertTrue(sim.events.containsAll(listOf(RideEventType.REMINDER_DRINK, RideEventType.REMINDER_ACK)))
    }

    @Test
    fun `battery change is reported and logged`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)

        sim.recorder.onBattery(BatteryStatus(9, isCharging = false), sim.nowMs)
        val output = sim.recorder.tick(sim.nowMs + 1_000)

        assertEquals(BatteryMode.CRITICAL, output.batteryChange?.mode)
        assertEquals(listOf(RideEventType.BATTERY_MODE_CRITICAL), output.events.map { it.type })
        assertEquals(BatteryMode.CRITICAL, output.live.batteryMode)
    }

    @Test
    fun `a stalled tick is not counted as moving time and logs a gap`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(10, kmh = 20.0)
        val before = sim.live().movingMs

        val output = sim.recorder.tick(sim.nowMs + 60_000)

        assertEquals(before, output.live.movingMs)
        assertTrue(RideEventType.GAP in output.events.map { it.type })
    }

    @Test
    fun `stale gps gives no speed and no fix`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(5, kmh = 20.0)

        val live = sim.recorder.tick(sim.nowMs + 10_000).live

        assertNull(live.speedMps)
        assertTrue(!live.hasGpsFix)
    }

    @Test
    fun `restore continues totals as paused with a gap event`() {
        val recorder = RideRecorder(RideSettings(), AltitudeSource.GPS)

        recorder.restore(
            startedAtMs = START_MS,
            totals = RecoveredTotals(distanceM = 5_000.0, movingMs = 600_000, maxSpeedMps = 9.0, lapIndex = 2),
            resumeAs = RideStatus.PAUSED,
            nowMs = START_MS + 900_000,
        )
        val output = recorder.tick(START_MS + 901_000)

        assertEquals(RideStatus.PAUSED, output.live.status)
        assertEquals(5_000.0, output.live.distanceM)
        assertEquals(600_000L, output.live.movingMs)
        assertEquals(3, output.live.lap?.number)
        assertEquals(listOf(RideEventType.GAP), output.events.map { it.type })
    }

    @Test
    fun `gps slows down after a long manual pause and returns to 1 Hz on resume`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(5, kmh = 20.0)
        sim.action(RideAction.PAUSE)

        assertEquals(GpsConfig.INTERVAL_MS, sim.recorder.desiredGpsIntervalMs(sim.nowMs + 60_000))
        assertEquals(GpsConfig.LOW_POWER_INTERVAL_MS, sim.recorder.desiredGpsIntervalMs(sim.nowMs + 11 * 60_000))
        sim.action(RideAction.RESUME)
        assertEquals(GpsConfig.INTERVAL_MS, sim.recorder.desiredGpsIntervalMs(sim.nowMs + 11 * 60_000))
    }

    @Test
    fun `totals summarise the ride`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.ride(100, kmh = 36.0)

        val totals = sim.recorder.totals(sim.nowMs)

        assertEquals(1_000.0, totals.distanceM, absoluteTolerance = 15.0)
        assertEquals(10.0, totals.avgSpeedMps, absoluteTolerance = 0.3)
        assertEquals(100L, totals.elapsedS)
    }

    @Test
    fun `discard from pause returns to idle`() {
        val sim = RideSimulator()
        sim.action(RideAction.START)
        sim.action(RideAction.PAUSE)

        sim.action(RideAction.DISCARD)

        assertEquals(RideStatus.IDLE, sim.recorder.currentStatus)
    }

    @Test
    fun `snooze hides the reminder`() {
        val sim = RideSimulator(RideSettings(drinkIntervalMin = 1, eatIntervalMin = null))
        sim.action(RideAction.START)
        sim.ride(62, kmh = 20.0)

        sim.action(RideAction.SNOOZE_REMINDER)

        assertNull(sim.live().reminder)
    }
}

class TrackPointBufferTest {
    private fun sample(t: Long) = RideSample(timeMs = t)

    @Test
    fun `flush is due every 5 seconds`() {
        val buffer = TrackPointBuffer()
        buffer.add(sample(0))

        assertTrue(!buffer.isFlushDue(0))
        assertTrue(!buffer.isFlushDue(4_000))
        assertTrue(buffer.isFlushDue(5_000))
    }

    @Test
    fun `flush is due early when many points wait`() {
        val buffer = TrackPointBuffer(flushSize = 3)
        buffer.isFlushDue(0)
        repeat(3) { buffer.add(sample(it.toLong())) }

        assertTrue(buffer.isFlushDue(1_000))
    }

    @Test
    fun `successful flush removes written points only`() {
        val buffer = TrackPointBuffer()
        repeat(4) { buffer.add(sample(it.toLong())) }
        val written = buffer.peekAll().take(3)

        buffer.onFlushed(written.size, nowMs = 5_000)

        assertEquals(listOf(3L), buffer.peekAll().map { it.timeMs })
    }

    @Test
    fun `failed writes keep points but memory stays bounded`() {
        val buffer = TrackPointBuffer(capacity = 10)

        repeat(25) { buffer.add(sample(it.toLong())) }

        assertEquals(10, buffer.size)
        assertEquals(15, buffer.droppedCount)
        assertEquals(15L, buffer.peekAll().first().timeMs)
    }

    @Test
    fun `empty buffer is never due`() {
        assertTrue(!TrackPointBuffer().isFlushDue(100_000))
    }
}
