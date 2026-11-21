package com.ridecomp.feature.sensors

import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.SensorProbeResult
import com.ridecomp.core.testing.FakeBleScanner
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeSensorProbe
import com.ridecomp.core.testing.FakeSensorRepository
import com.ridecomp.core.usecase.ForgetSensorUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.PairSensorUseCase
import com.ridecomp.core.usecase.ProbeSensorUseCase
import com.ridecomp.core.usecase.ScanBlocker
import com.ridecomp.core.usecase.ScanSensorsUseCase
import com.ridecomp.feature.sensors.viewmodel.ProbeFailure
import com.ridecomp.feature.sensors.viewmodel.SensorsUiEvent
import com.ridecomp.feature.sensors.viewmodel.SensorsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SensorsViewModelTest {
    private val repository = FakeSensorRepository()
    private val scanner = FakeBleScanner()
    private val probe = FakeSensorProbe()
    private val watch = DiscoveredSensor("EE:FF", "Watch HR", emptySet(), -60)
    private val gtr3 = DiscoveredSensor("11:22", "GTR 3", emptySet(), -55)
    private val hrm = DiscoveredSensor("AA:BB", "HRM-Dual", setOf(SensorKind.HEART_RATE), -70)
    private val powerMeter = DiscoveredSensor("CC:DD", null, setOf(SensorKind.POWER), -50)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SensorsViewModel(
        ObserveSavedSensorsUseCase(repository),
        ScanSensorsUseCase(scanner),
        PairSensorUseCase(repository, FakeClock()),
        ForgetSensorUseCase(repository),
        ProbeSensorUseCase(probe),
    )

    @Test
    fun `show all devices finds watches, a watch with heart rate is checked then saved as an HR sensor`() = runTest {
        scanner.results = listOf(hrm)
        scanner.wideResults = listOf(watch, gtr3)
        probe.results[watch.address] = SensorProbeResult.Supported(setOf(SensorKind.HEART_RATE), isTrainer = false)
        probe.results[gtr3.address] = SensorProbeResult.NoSupportedService
        val viewModel = viewModel()

        viewModel.onEvent(SensorsUiEvent.StartScan)
        assertEquals(listOf("AA:BB"), viewModel.uiState.value.discovered.map { it.address })
        viewModel.onEvent(SensorsUiEvent.WideScan(isOn = true))
        viewModel.onEvent(SensorsUiEvent.StartScan)
        assertEquals(setOf("AA:BB", "EE:FF", "11:22"), viewModel.uiState.value.discovered.map { it.address }.toSet())

        viewModel.onEvent(SensorsUiEvent.Pair(watch, fallbackName = "Unnamed"))
        val saved = viewModel.uiState.value.saved.single()
        assertEquals("Watch HR", saved.name)
        assertEquals(setOf(SensorKind.HEART_RATE), saved.kinds)
        assertNull(viewModel.uiState.value.probingAddress)

        // A watch that keeps its heart rate to its own app is not saved, and the rider is told why.
        viewModel.onEvent(SensorsUiEvent.Pair(gtr3, fallbackName = "Unnamed"))
        assertEquals(1, viewModel.uiState.value.saved.size)
        assertEquals(ProbeFailure("GTR 3", isUnreachable = false), viewModel.uiState.value.probeFailure)
        assertEquals(listOf("EE:FF", "11:22"), probe.probed)

        // A sensor that advertised its kind is paired without a check.
        viewModel.onEvent(SensorsUiEvent.Pair(hrm, fallbackName = "Unnamed"))
        assertEquals(listOf("EE:FF", "11:22"), probe.probed)
        assertEquals(2, viewModel.uiState.value.saved.size)
    }

    @Test
    fun `a GloryFit watch found by checking is saved as an experimental heart rate sensor`() = runTest {
        scanner.wideResults = listOf(gtr3)
        probe.results[gtr3.address] =
            SensorProbeResult.Supported(setOf(SensorKind.HEART_RATE), isTrainer = false, isGloryFitWatch = true)
        val viewModel = viewModel()
        viewModel.onEvent(SensorsUiEvent.WideScan(isOn = true))
        viewModel.onEvent(SensorsUiEvent.StartScan)

        viewModel.onEvent(SensorsUiEvent.Pair(viewModel.uiState.value.discovered.single(), fallbackName = "Sensor"))

        val saved = repository.getAutoConnect().single()
        assertEquals(setOf(SensorKind.HEART_RATE), saved.kinds)
        assertTrue(saved.isGloryFitWatch)
        assertNull(viewModel.uiState.value.probeFailure)
    }

    @Test
    fun `scan lists found sensors strongest first and ends by itself`() {
        scanner.results = listOf(hrm, powerMeter, hrm)
        val viewModel = viewModel()

        viewModel.onEvent(SensorsUiEvent.StartScan)

        val state = viewModel.uiState.value
        assertEquals(listOf("CC:DD", "AA:BB"), state.discovered.map { it.address })
        assertFalse(state.isScanning)
        assertNull(state.blocker)
    }

    @Test
    fun `a scan that finds nothing says so, the next scan clears it`() {
        val viewModel = viewModel()

        viewModel.onEvent(SensorsUiEvent.StartScan)
        assertTrue(viewModel.uiState.value.hasFoundNothing)

        scanner.results = listOf(hrm)
        viewModel.onEvent(SensorsUiEvent.StartScan)
        assertFalse(viewModel.uiState.value.hasFoundNothing)
    }

    @Test
    fun `switching to all devices during a scan restarts it, stopping ends it`() {
        scanner.isEndless = true
        val viewModel = viewModel()

        viewModel.onEvent(SensorsUiEvent.StartScan)
        viewModel.onEvent(SensorsUiEvent.WideScan(isOn = true))
        assertTrue(viewModel.uiState.value.isScanning)
        assertTrue(viewModel.uiState.value.isWideScan)

        viewModel.onEvent(SensorsUiEvent.StopScan)
        assertFalse(viewModel.uiState.value.isScanning)
        assertFalse(viewModel.uiState.value.hasFoundNothing)
    }

    @Test
    fun `missing permission or bluetooth off blocks the scan`() {
        scanner.isPermitted = false
        val viewModel = viewModel()

        viewModel.onEvent(SensorsUiEvent.StartScan)
        assertEquals(ScanBlocker.NO_PERMISSION, viewModel.uiState.value.blocker)

        scanner.isPermitted = true
        scanner.isEnabled = false
        viewModel.onEvent(SensorsUiEvent.StartScan)
        assertEquals(ScanBlocker.BLUETOOTH_OFF, viewModel.uiState.value.blocker)
        assertTrue(viewModel.uiState.value.discovered.isEmpty())
    }

    @Test
    fun `pairing moves the sensor from found to saved and forget asks first`() = runTest {
        scanner.results = listOf(hrm, powerMeter)
        val viewModel = viewModel()
        viewModel.onEvent(SensorsUiEvent.StartScan)

        viewModel.onEvent(SensorsUiEvent.Pair(powerMeter, fallbackName = "Unnamed"))

        assertEquals(listOf("Unnamed"), viewModel.uiState.value.saved.map { it.name })
        assertEquals(listOf("AA:BB"), viewModel.uiState.value.discovered.map { it.address })

        val saved = viewModel.uiState.value.saved.single()
        viewModel.onEvent(SensorsUiEvent.AskForget(saved))
        viewModel.onEvent(SensorsUiEvent.DismissForget)
        assertEquals(1, viewModel.uiState.value.saved.size)

        viewModel.onEvent(SensorsUiEvent.AskForget(saved))
        viewModel.onEvent(SensorsUiEvent.ConfirmForget)
        assertTrue(viewModel.uiState.value.saved.isEmpty())
        assertNull(viewModel.uiState.value.forgetCandidate)
    }
}
