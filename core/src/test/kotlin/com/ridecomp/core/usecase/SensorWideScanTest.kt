package com.ridecomp.core.usecase

import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.isWideScanCandidate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which devices "Show all Bluetooth devices" lists (T12.2). */
class SensorWideScanTest {
    private fun device(name: String?, rssi: Int) = DiscoveredSensor("AA", name, emptySet(), rssi)

    @Test
    fun `named devices in reach and nameless ones right next to the phone are listed`() {
        assertTrue(isWideScanCandidate(device("GTR 3", -85)))
        assertFalse(isWideScanCandidate(device("Far TV", -95)))
        assertTrue(isWideScanCandidate(device(null, -50)))
        assertFalse(isWideScanCandidate(device(" ", -70)))
    }
}
