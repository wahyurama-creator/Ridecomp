package com.ridecomp.core.usecase

import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.ReconnectBackoff
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.testing.FakeBleScanner
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeSensorRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class SensorUseCasesTest {
    private val repository = FakeSensorRepository()
    private val hrm = DiscoveredSensor("AA:BB", "HRM-Dual", setOf(SensorKind.HEART_RATE), -60)

    @Test
    fun `reconnect backoff is 2 4 8 then every 15 seconds and resets after success`() {
        val backoff = ReconnectBackoff()

        assertEquals(listOf(2_000L, 4_000L, 8_000L, 15_000L, 15_000L), List(5) { backoff.nextDelayMs() })
        backoff.reset()
        assertEquals(2_000L, backoff.nextDelayMs())
    }

    @Test
    fun `pairing saves the sensor with auto connect and a fallback name`() = runTest {
        val pair = PairSensorUseCase(repository, FakeClock())

        pair(hrm, fallbackName = "Sensor")
        pair(hrm.copy(address = "CC:DD", name = null), fallbackName = "Sensor")

        val saved = ObserveSavedSensorsUseCase(repository)().first()
        assertEquals(listOf("HRM-Dual", "Sensor"), saved.map { it.name })
        assertEquals(2, repository.getAutoConnect().size)
    }

    @Test
    fun `forgetting removes the sensor`() = runTest {
        PairSensorUseCase(repository, FakeClock())(hrm, "Sensor")

        ForgetSensorUseCase(repository)("AA:BB")

        assertEquals(emptyList(), repository.getAutoConnect())
    }

    @Test
    fun `scan reports what blocks it`() = runTest {
        val scanner = FakeBleScanner(results = listOf(hrm))
        val scan = ScanSensorsUseCase(scanner)

        assertNull(scan.blocker())
        assertEquals(listOf(hrm), scan().toList())
        scanner.isEnabled = false
        assertEquals(ScanBlocker.BLUETOOTH_OFF, scan.blocker())
        scanner.isPermitted = false
        assertEquals(ScanBlocker.NO_PERMISSION, scan.blocker())
    }
}
