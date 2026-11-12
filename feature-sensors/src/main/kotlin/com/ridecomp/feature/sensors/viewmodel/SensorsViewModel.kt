package com.ridecomp.feature.sensors.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.withScanResult
import com.ridecomp.core.usecase.ForgetSensorUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.PairSensorUseCase
import com.ridecomp.core.usecase.ScanBlocker
import com.ridecomp.core.usecase.ScanSensorsUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
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
    /** Sensor awaiting "forget" confirmation. */
    val forgetCandidate: SavedSensor? = null,
    val isLoaded: Boolean = false,
)

sealed interface SensorsUiEvent {
    data object StartScan : SensorsUiEvent

    data object StopScan : SensorsUiEvent

    /** [fallbackName] is shown when the device does not advertise a name. */
    data class Pair(val sensor: DiscoveredSensor, val fallbackName: String) : SensorsUiEvent

    data class AskForget(val sensor: SavedSensor) : SensorsUiEvent

    data object ConfirmForget : SensorsUiEvent

    data object DismissForget : SensorsUiEvent
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
            is SensorsUiEvent.Pair -> viewModelScope.launch { pairSensor(event.sensor, event.fallbackName) }
            is SensorsUiEvent.AskForget -> updateState { copy(forgetCandidate = event.sensor) }
            SensorsUiEvent.DismissForget -> updateState { copy(forgetCandidate = null) }
            SensorsUiEvent.ConfirmForget -> confirmForget()
        }
    }

    private fun startScan() {
        val blocker = scanSensors.blocker()
        updateState { copy(blocker = blocker, hasScanFailed = false) }
        if (blocker != null || scanJob?.isActive == true) return
        updateState { copy(isScanning = true, discovered = persistentListOf()) }
        scanJob = viewModelScope.launch {
            scanSensors()
                .catch { updateState { copy(hasScanFailed = true) } }
                .onCompletion { updateState { copy(isScanning = false) } }
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
    }

    private fun confirmForget() {
        val sensor = currentState.forgetCandidate ?: return
        updateState { copy(forgetCandidate = null) }
        viewModelScope.launch { forgetSensor(sensor.address) }
    }
}
