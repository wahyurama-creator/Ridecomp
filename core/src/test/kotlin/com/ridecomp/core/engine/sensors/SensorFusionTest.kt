package com.ridecomp.core.engine.sensors

import com.ridecomp.core.engine.metrics.AltitudeSource
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.recording.RideRecorder
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WHEEL_M = 2.0

class SensorFusionTest {
    private val fusion = SensorFusion(wheelCircumferenceM = WHEEL_M)

    @Test
    fun `values older than 3 seconds are missing`() {
        fusion.onReading(SensorReading.HeartRate(HeartRateMeasurement(140, emptyList()), receivedAtMs = 0))

        assertEquals(140, fusion.snapshot(3_000).heartRateBpm)
        assertNull(fusion.snapshot(3_001).heartRateBpm)
    }

    @Test
    fun `power meter cadence wins over the csc sensor by default`() {
        fusion.onReading(SensorReading.SpeedCadence(CscMeasurement(null, RevolutionData(0, 0, 1_024)), 0))
        fusion.onReading(SensorReading.SpeedCadence(CscMeasurement(null, RevolutionData(1, 1_024, 1_024)), 1_000))
        fusion.onReading(SensorReading.Power(PowerMeasurement(200, null, null, RevolutionData(0, 0, 1_024)), 0))
        fusion.onReading(SensorReading.Power(PowerMeasurement(210, null, null, RevolutionData(3, 2_048, 1_024)), 1_000))

        val snapshot = fusion.snapshot(1_000)

        assertEquals(90, snapshot.cadenceRpm)
        assertEquals(210, snapshot.powerW)
        assertTrue(snapshot.hasPowerMeter)
        assertTrue(snapshot.hasCadenceSensor)
    }

    @Test
    fun `csc cadence can be preferred`() {
        val preferCsc = SensorFusion(WHEEL_M, CadencePriority.SPEED_CADENCE)
        preferCsc.onReading(SensorReading.SpeedCadence(CscMeasurement(null, RevolutionData(0, 0, 1_024)), 0))
        preferCsc.onReading(SensorReading.SpeedCadence(CscMeasurement(null, RevolutionData(1, 1_024, 1_024)), 1_000))
        preferCsc.onReading(SensorReading.Power(PowerMeasurement(200, null, null, RevolutionData(0, 0, 1_024)), 0))
        preferCsc.onReading(
            SensorReading.Power(PowerMeasurement(200, null, null, RevolutionData(3, 2_048, 1_024)), 1_000),
        )

        assertEquals(60, preferCsc.snapshot(1_000).cadenceRpm)
    }

    @Test
    fun `wheel speed from csc and connection state with battery`() {
        fusion.onReading(SensorReading.SpeedCadence(CscMeasurement(RevolutionData(0, 0, 1_024), null), 0))
        fusion.onReading(SensorReading.SpeedCadence(CscMeasurement(RevolutionData(4, 1_024, 1_024), null), 1_000))
        fusion.onReading(SensorReading.Connection(SensorKind.SPEED_CADENCE, isConnected = true, 1_000))
        fusion.onReading(SensorReading.Battery(SensorKind.SPEED_CADENCE, 12, 1_000))

        val snapshot = fusion.snapshot(1_000)

        assertEquals(8.0, snapshot.wheelSpeedMps)
        assertEquals(SensorState(isConnected = true, batteryPercent = 12), snapshot.states[SensorKind.SPEED_CADENCE])
    }

    @Test
    fun `a disconnect resets counters so no rate spans the gap`() {
        fusion.onReading(SensorReading.SpeedCadence(CscMeasurement(RevolutionData(0, 0, 1_024), null), 0))
        fusion.onReading(SensorReading.Connection(SensorKind.SPEED_CADENCE, isConnected = false, 500))
        fusion.onReading(SensorReading.SpeedCadence(CscMeasurement(RevolutionData(500, 1_024, 1_024), null), 60_000))

        assertNull(fusion.snapshot(60_000).wheelSpeedMps)
    }
}

class RideRecorderSensorsTest {
    private val start = 1_767_225_600_000L
    private val recorder = RideRecorder(
        RideSettings(isAutoPauseEnabled = false, drinkIntervalMin = null, eatIntervalMin = null),
        AltitudeSource.GPS,
        RiderProfile(weightKg = 70.0, wheelCircumferenceMm = 2_000, hrMaxBpm = 190),
    )

    private fun ride(seconds: Int, withWheel: Boolean, withPower: Boolean): List<com.ridecomp.core.model.RideSample> {
        recorder.handle(RideAction.START, start)
        val samples = mutableListOf<com.ridecomp.core.model.RideSample>()
        repeat(seconds) { i ->
            val now = start + (i + 1) * 1_000L
            recorder.onLocation(LocationFix(now, -7.0 + i * 0.00007, 110.0, 100.0, 5.0, 8.0))
            recorder.onSensor(SensorReading.HeartRate(HeartRateMeasurement(150, emptyList()), now))
            if (withWheel) {
                recorder.onSensor(
                    SensorReading.SpeedCadence(CscMeasurement(RevolutionData(i * 5L, i * 1_024, 1_024), null), now),
                )
            }
            if (withPower) {
                recorder.onSensor(
                    SensorReading.Power(
                        PowerMeasurement(
                            220,
                            null,
                            null,
                            RevolutionData(
                                i.toLong(),
                                i * 1_024,
                                1_024,
                            ),
                        ),
                        now,
                    ),
                )
            }
            recorder.tick(now).sample?.let(samples::add)
        }
        return samples
    }

    @Test
    fun `wheel sensor drives distance and speed`() {
        ride(30, withWheel = true, withPower = false)

        val totals = recorder.totals(start + 30_000)

        // 5 revolutions/s × 2 m = 10 m/s, ~29 counted seconds after the first rate is known.
        assertEquals(290.0, totals.distanceM, absoluteTolerance = 25.0)
        assertEquals(10.0, totals.maxSpeedMps, absoluteTolerance = 0.01)
    }

    @Test
    fun `samples carry heart rate and real power, and power meter rides have no estimate`() {
        val samples = ride(40, withWheel = false, withPower = true)

        val last = samples.last()
        assertEquals(150, last.heartRateBpm)
        assertEquals(220, last.powerW)
        assertNull(last.estPowerW)
        val totals = recorder.totals(start + 40_000)
        assertEquals(220, totals.avgPowerW)
        assertEquals(220, totals.normalizedPowerW)
        assertEquals(150, totals.avgHeartRateBpm)
        assertNotNull(totals.calories)
    }

    @Test
    fun `without a power meter the estimate is kept separately and never as power`() {
        val samples = ride(10, withWheel = false, withPower = false)

        assertTrue(samples.all { it.powerW == null })
        assertTrue(samples.drop(2).all { (it.estPowerW ?: 0) > 0 })
        assertNotNull(recorder.totals(start + 10_000).estAvgPowerW)
    }

    @Test
    fun `sensor loss and return are logged during a ride`() {
        recorder.handle(RideAction.START, start)
        recorder.onSensor(SensorReading.Connection(SensorKind.HEART_RATE, isConnected = false, start + 1_000))
        recorder.onSensor(SensorReading.Connection(SensorKind.HEART_RATE, isConnected = true, start + 9_000))

        val events = recorder.tick(start + 1_000).events.map { it.type }

        assertEquals(listOf(RideEventType.SENSOR_LOST, RideEventType.SENSOR_BACK), events)
    }

    @Test
    fun `heart rate zone and live sensor values`() {
        ride(5, withWheel = false, withPower = false)

        val live = recorder.tick(start + 5_000).live.sensors

        assertEquals(150, live.heartRateBpm)
        assertEquals(3, live.heartRateZone)
    }
}
