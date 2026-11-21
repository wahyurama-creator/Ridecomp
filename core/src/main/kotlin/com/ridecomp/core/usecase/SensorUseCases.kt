package com.ridecomp.core.usecase

import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorProbe
import com.ridecomp.core.engine.sensors.SensorProbeResult
import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.engine.sensors.TrainerControl
import com.ridecomp.core.engine.sensors.TrainerState
import com.ridecomp.core.source.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Paired sensors for the Sensors screen. */
public class ObserveSavedSensorsUseCase(private val repository: SensorRepository) {
    public operator fun invoke(): Flow<List<SavedSensor>> = repository.observeAll()
}

/** Scan state for the UI (02-DESIGN §3.8). */
public enum class ScanBlocker { NO_PERMISSION, BLUETOOTH_OFF }

/** Scans for nearby sensors, bounded by the scan timeout. */
public class ScanSensorsUseCase(private val scanner: BleScanner) {
    /** Why a scan cannot start, or null when it can. */
    public fun blocker(): ScanBlocker? = when {
        !scanner.hasPermission() -> ScanBlocker.NO_PERMISSION
        !scanner.isBluetoothEnabled() -> ScanBlocker.BLUETOOTH_OFF
        else -> null
    }

    /**
     * Raw results (the same device repeats with new RSSI); the caller keeps one entry per address. [isWide]
     * lists every named device in reach (T12.2).
     */
    public operator fun invoke(isWide: Boolean = false): Flow<DiscoveredSensor> = scanner.scan(isWide = isWide)
}

/**
 * Checks what a device found by the wide scan offers before it is paired (T12.2): with a supported
 * service it becomes a normal sensor of those kinds; otherwise it is not saved.
 */
public class ProbeSensorUseCase(private val probe: SensorProbe) {
    public suspend operator fun invoke(sensor: DiscoveredSensor): SensorProbeResult = probe.probe(sensor.address)
}

/** Pairs = saves a discovered sensor with auto-connect on (§A.5). */
public class PairSensorUseCase(private val repository: SensorRepository, private val clock: Clock) {
    public suspend operator fun invoke(sensor: DiscoveredSensor, fallbackName: String) {
        repository.save(
            SavedSensor(
                address = sensor.address,
                name = sensor.name?.takeIf { it.isNotBlank() } ?: fallbackName,
                kinds = sensor.kinds,
                createdAtMs = clock.nowMs(),
                isTrainer = sensor.isTrainer,
                isGloryFitWatch = sensor.isGloryFitWatch,
            ),
        )
    }
}

public class ForgetSensorUseCase(private val repository: SensorRepository) {
    public suspend operator fun invoke(address: String) {
        repository.forget(address)
    }
}

/** The smart trainer's link and ERG target (T9.1). */
public class ObserveTrainerUseCase(private val control: TrainerControl) {
    public operator fun invoke(): StateFlow<TrainerState> = control.state
}

/** ERG − / + on the ride screen, or a zone button (watts). */
public class SetTrainerTargetUseCase(private val control: TrainerControl) {
    public fun adjust(deltaW: Int) {
        control.adjust(deltaW)
    }

    public fun set(watts: Int) {
        control.setTarget(watts)
    }
}
