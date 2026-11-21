package com.ridecomp.feature.sensors.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorProbeResult
import com.ridecomp.core.engine.sensors.withScanResult
import com.ridecomp.core.usecase.ForgetSensorUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.PairSensorUseCase
import com.ridecomp.core.usecase.ProbeSensorUseCase
import com.ridecomp.core.usecase.ScanBlocker
import com.ridecomp.core.usecase.ScanSensorsUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

@Immutable
data class SensorsUiState(
    val saved: ImmutableList<SavedSensor> = persistentListOf(),
    val discovered: ImmutableList<DiscoveredSensor> = persistentListOf(),
    val isScanning: Boolean = false,
    /** Why scanning is impossible right now (permission, Bluetooth off). */
    val blocker: ScanBlocker? = null,
    val hasScanFailed: Boolean = false,
    /** The last scan ran to its end and found nothing new (release test: the list just went quiet). */
    val hasFoundNothing: Boolean = false,
    /** Sensor awaiting "forget" confirmation. */
    val forgetCandidate: SavedSensor? = null,
    val isLoaded: Boolean = false,
    /** "Show all Bluetooth devices" (T12.2): unfiltered scan, devices are checked before pairing. */
    val isWideScan: Boolean = false,
    /** The device being checked for supported services. */
    val probingAddress: String? = null,
    /** The last check found nothing RideComp can read, or could not connect. */
    val probeFailure: ProbeFailure? = null,
)

/** Why a device from the wide scan was not paired (T12.2). */
data class ProbeFailure(val name: String, val isUnreachable: Boolean)

sealed interface SensorsUiEvent {
    data object StartScan : SensorsUiEvent

    data object StopScan : SensorsUiEvent

    /** [fallbackName] is shown when the device does not advertise a name. */
    data class Pair(val sensor: DiscoveredSensor, val fallbackName: String) : SensorsUiEvent

    data class AskForget(val sensor: SavedSensor) : SensorsUiEvent

    data object ConfirmForget : SensorsUiEvent

    data object DismissForget : SensorsUiEvent

    /** Turns "Show all Bluetooth devices" on or off; a running scan restarts with the new mode (T12.2). */
    data class WideScan(val isOn: Boolean) : SensorsUiEvent
}

/**
 * Sensors screen (T2.1, 02-DESIGN §3.8): paired sensors with battery, a bounded scan (stops by itself after
 * the scan timeout and when the screen leaves — `viewModelScope`), pairing and forgetting.
 */
class SensorsViewModel(
    observeSaved: ObserveSavedSensorsUseCase,
    private val scanSensors: ScanSensorsUseCase,
    private val pairSensor: PairSensorUseCase,
    private val forgetSensor: ForgetSensorUseCase,
    private val probeSensor: ProbeSensorUseCase,
) : UdfViewModel<SensorsUiState, SensorsUiEvent, NoEffect>(SensorsUiState()) {
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            observeSaved().collect { saved ->
                val pairedAddresses = saved.map { it.address }.toSet()
                updateState {
                    copy(
                        saved = saved.toImmutableList(),
                        discovered = discovered.filterNot { it.address in pairedAddresses }.toImmutableList(),
                        isLoaded = true,
                    )
                }
            }
        }
    }

    override fun onEvent(event: SensorsUiEvent) {
        when (event) {
            SensorsUiEvent.StartScan -> startScan()
            SensorsUiEvent.StopScan -> stopScan()
            is SensorsUiEvent.Pair -> pair(event.sensor, event.fallbackName)
            is SensorsUiEvent.AskForget -> updateState { copy(forgetCandidate = event.sensor) }
            SensorsUiEvent.DismissForget -> updateState { copy(forgetCandidate = null) }
            SensorsUiEvent.ConfirmForget -> confirmForget()
            is SensorsUiEvent.WideScan -> setWideScan(event.isOn)
        }
    }

    /**
     * A device that advertised its sensor kinds is paired at once; one from the wide scan is checked first
     * and paired with the kinds it really offers (T12.2).
     */
    private fun pair(sensor: DiscoveredSensor, fallbackName: String) {
        if (sensor.kinds.isNotEmpty()) {
            viewModelScope.launch { pairSensor(sensor, fallbackName) }
            return
        }
        if (currentState.probingAddress != null) return
        // The scan would compete with the connection for the radio.
        stopScan()
        updateState { copy(probingAddress = sensor.address, probeFailure = null) }
        viewModelScope.launch {
            val name = sensor.name ?: fallbackName
            when (val result = probeSensor(sensor)) {
                is SensorProbeResult.Supported -> {
                    pairSensor(
                        sensor.copy(
                            kinds = result.kinds,
                            isTrainer = result.isTrainer,
                            isGloryFitWatch = result.isGloryFitWatch,
                        ),
                        fallbackName,
                    )
                    updateState { copy(probingAddress = null) }
                }
                SensorProbeResult.NoSupportedService ->
                    updateState {
                        copy(probingAddress = null, probeFailure = ProbeFailure(name, isUnreachable = false))
                    }
                SensorProbeResult.Unreachable ->
                    updateState { copy(probingAddress = null, probeFailure = ProbeFailure(name, isUnreachable = true)) }
            }
        }
    }

    private fun setWideScan(isOn: Boolean) {
        if (isOn == currentState.isWideScan) return
        val wasScanning = scanJob?.isActive == true
        stopScan()
        updateState { copy(isWideScan = isOn, discovered = persistentListOf(), probeFailure = null) }
        if (wasScanning) startScan()
    }

    private fun startScan() {
        val blocker = scanSensors.blocker()
        updateState { copy(blocker = blocker, hasScanFailed = false, hasFoundNothing = false, probeFailure = null) }
        if (blocker != null || scanJob?.isActive == true) return
        updateState { copy(isScanning = true, discovered = persistentListOf()) }
        scanJob = viewModelScope.launch {
            scanSensors(isWide = currentState.isWideScan)
                .catch { updateState { copy(hasScanFailed = true) } }
                .onCompletion { cause ->
                    // Cancelled scans are settled by stopScan: a late completion must not end a newer scan.
                    if (cause is CancellationException) return@onCompletion
                    // Failed: no "nothing found" note.
                    updateState { copy(isScanning = false, hasFoundNothing = cause == null && discovered.isEmpty()) }
                }
                .collect { result ->
                    updateState {
                        val paired = saved.map { it.address }.toSet()
                        copy(discovered = discovered.withScanResult(result, paired).toImmutableList())
                    }
                }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        updateState { copy(isScanning = false) }
    }

    private fun confirmForget() {
        val sensor = currentState.forgetCandidate ?: return
        updateState { copy(forgetCandidate = null) }
        viewModelScope.launch { forgetSensor(sensor.address) }
    }
}
