package com.ridecomp.core.usecase

import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.source.Clock
import kotlinx.coroutines.flow.Flow

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

    /** Raw results (the same device repeats with new RSSI); the caller keeps one entry per address. */
    public operator fun invoke(): Flow<DiscoveredSensor> = scanner.scan()
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
            ),
        )
    }
}

public class ForgetSensorUseCase(private val repository: SensorRepository) {
    public suspend operator fun invoke(address: String) {
        repository.forget(address)
    }
}
