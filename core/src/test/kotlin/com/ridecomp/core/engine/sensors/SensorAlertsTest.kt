package com.ridecomp.core.engine.sensors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensorAlertsTest {
    private val fusion = SensorFusion(wheelCircumferenceM = 2.1)

    @Test
    fun `no alert for sensors that never connected`() {
        assertTrue(fusion.snapshot(0).alerts().isEmpty())
    }

    @Test
    fun `dropped sensor is lost until it is back`() {
        fusion.onReading(SensorReading.Connection(SensorKind.HEART_RATE, isConnected = true, receivedAtMs = 0))
        fusion.onReading(SensorReading.Connection(SensorKind.HEART_RATE, isConnected = false, receivedAtMs = 1))

        assertEquals(
            listOf(SensorAlert(SensorKind.HEART_RATE, SensorAlertType.LOST)),
            fusion.snapshot(2).alerts(),
        )

        fusion.onReading(SensorReading.Connection(SensorKind.HEART_RATE, isConnected = true, receivedAtMs = 3))
        assertTrue(fusion.snapshot(4).alerts().isEmpty())
    }

    @Test
    fun `low battery is reported below the threshold only`() {
        fusion.onReading(SensorReading.Connection(SensorKind.POWER, isConnected = true, receivedAtMs = 0))
        fusion.onReading(SensorReading.Battery(SensorKind.POWER, SensorLimits.LOW_BATTERY_PERCENT, receivedAtMs = 1))
        assertTrue(fusion.snapshot(2).alerts().isEmpty())

        fusion.onReading(SensorReading.Battery(SensorKind.POWER, percent = 9, receivedAtMs = 3))
        assertEquals(
            listOf(SensorAlert(SensorKind.POWER, SensorAlertType.LOW_BATTERY, batteryPercent = 9)),
            fusion.snapshot(4).alerts(),
        )
    }

    @Test
    fun `a lost sensor is reported as lost, not low battery`() {
        fusion.onReading(SensorReading.Connection(SensorKind.SPEED_CADENCE, isConnected = true, receivedAtMs = 0))
        fusion.onReading(SensorReading.Battery(SensorKind.SPEED_CADENCE, percent = 5, receivedAtMs = 1))
        fusion.onReading(SensorReading.Connection(SensorKind.SPEED_CADENCE, isConnected = false, receivedAtMs = 2))

        assertEquals(SensorAlertType.LOST, fusion.snapshot(3).alerts().single().type)
    }
}
