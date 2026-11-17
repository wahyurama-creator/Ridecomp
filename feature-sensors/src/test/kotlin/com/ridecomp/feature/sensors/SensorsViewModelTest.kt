package com.ridecomp.feature.sensors

import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.testing.FakeBleScanner
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeSensorRepository
import com.ridecomp.core.usecase.ForgetSensorUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.PairSensorUseCase
import com.ridecomp.core.usecase.ScanBlocker
import com.ridecomp.core.usecase.ScanSensorsUseCase
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
    )

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
