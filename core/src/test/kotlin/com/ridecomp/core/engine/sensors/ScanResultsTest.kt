package com.ridecomp.core.engine.sensors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanResultsTest {
    private fun found(address: String, rssiDbm: Int) =
        DiscoveredSensor(address, name = null, kinds = setOf(SensorKind.HEART_RATE), rssiDbm = rssiDbm)

    @Test
    fun `one entry per address with the latest rssi, strongest first`() {
        val list = emptyList<DiscoveredSensor>()
            .withScanResult(found("A", -80), emptySet())
            .withScanResult(found("B", -60), emptySet())
            .withScanResult(found("A", -50), emptySet())

        assertEquals(listOf("A" to -50, "B" to -60), list.map { it.address to it.rssiDbm })
    }

    @Test
    fun `paired devices are left out and the list is bounded`() {
        val list = (1..5).fold(emptyList<DiscoveredSensor>()) { acc, i ->
            acc.withScanResult(found("D$i", -i), pairedAddresses = setOf("D1"), maxSize = 3)
        }

        assertEquals(listOf("D2", "D3", "D4"), list.map { it.address })
    }

    @Test
    fun `battery is low only below the threshold`() {
        val sensor = SavedSensor("A", "HRM", setOf(SensorKind.HEART_RATE))

        assertFalse(sensor.isBatteryLow)
        assertFalse(sensor.copy(lastBatteryPercent = SensorLimits.LOW_BATTERY_PERCENT).isBatteryLow)
        assertTrue(sensor.copy(lastBatteryPercent = SensorLimits.LOW_BATTERY_PERCENT - 1).isBatteryLow)
    }
}
