package com.ridecomp.core.engine.sensors

import com.ridecomp.core.config.Units
import kotlinx.coroutines.flow.Flow

/** BLE scan/connection limits (04-INTEGRATIONS §A.5, BAT-04). */
public object BleConfig {
    /** A scan always stops after this long. */
    public const val SCAN_TIMEOUT_MS: Long = 15_000L

    /** Battery level is read on connect and then this often (§A.4). */
    public const val BATTERY_READ_INTERVAL_MS: Long = 10L * Units.MS_PER_MINUTE

    /** One GATT operation must finish within this time, otherwise the connection is reset (CO-09). */
    public const val GATT_OPERATION_TIMEOUT_MS: Long = 5_000L

    /** Connecting gives up after this long and retries with backoff. */
    public const val CONNECT_TIMEOUT_MS: Long = 20_000L

    /** Reconnect delays 2 s, 4 s, 8 s, then every 15 s during the ride (03-ARCHITECTURE §4.4). */
    public val RECONNECT_DELAYS_MS: List<Long> = listOf(2_000L, 4_000L, 8_000L)
    public const val RECONNECT_STEADY_DELAY_MS: Long = 15_000L

    /** At most this many scan results are kept (bounded memory, MEM-02); the strongest signals win. */
    public const val MAX_SCAN_RESULTS: Int = 20
}

/** Reconnect backoff for one sensor: never gives up during a ride, never retries faster than this. */
public class ReconnectBackoff(
    private val delaysMs: List<Long> = BleConfig.RECONNECT_DELAYS_MS,
    private val steadyDelayMs: Long = BleConfig.RECONNECT_STEADY_DELAY_MS,
) {
    private var attempt = 0

    /** Delay before the next attempt. */
    public fun nextDelayMs(): Long = (delaysMs.getOrNull(attempt) ?: steadyDelayMs).also { attempt++ }

    /** A connection succeeded: start from the shortest delay again. */
    public fun reset() {
        attempt = 0
    }
}

/** A paired sensor (05-DATA-MODEL `sensors`). */
public data class SavedSensor(
    val address: String,
    val name: String,
    val kinds: Set<SensorKind>,
    val lastBatteryPercent: Int? = null,
    val isAutoConnect: Boolean = true,
    val createdAtMs: Long = 0,
)

/** True when the last known battery level is below the warning threshold (§A.4). */
public val SavedSensor.isBatteryLow: Boolean
    get() = lastBatteryPercent?.let { it < SensorLimits.LOW_BATTERY_PERCENT } == true

/** A device found by a scan (02-DESIGN §3.8). */
public data class DiscoveredSensor(val address: String, val name: String?, val kinds: Set<SensorKind>, val rssiDbm: Int)

/**
 * Adds [result] to a scan list: one entry per address (latest RSSI wins), already-paired devices left
 * out, strongest signal first, at most [maxSize] entries.
 */
public fun List<DiscoveredSensor>.withScanResult(
    result: DiscoveredSensor,
    pairedAddresses: Set<String>,
    maxSize: Int = BleConfig.MAX_SCAN_RESULTS,
): List<DiscoveredSensor> {
    if (result.address in pairedAddresses) return this
    return (filterNot { it.address == result.address } + result)
        .sortedByDescending { it.rssiDbm }
        .take(maxSize)
}

/** Scans for HR / CSC / CP devices with a service filter; the flow ends after the timeout (BAT-04). */
public interface BleScanner {
    public fun isBluetoothEnabled(): Boolean

    public fun hasPermission(): Boolean

    public fun scan(timeoutMs: Long = BleConfig.SCAN_TIMEOUT_MS): Flow<DiscoveredSensor>
}

/**
 * Connects to [sensors] while collected and emits parsed readings, including connection and battery
 * events. Reconnects with [ReconnectBackoff] and never ends the ride; cancelling the collection disconnects
 * and closes every GATT (resource table 09 §1).
 */
public interface SensorConnector {
    public fun readings(sensors: List<SavedSensor>): Flow<SensorReading>
}

/** Stored paired sensors. */
public interface SensorRepository {
    public fun observeAll(): Flow<List<SavedSensor>>

    public suspend fun getAutoConnect(): List<SavedSensor>

    public suspend fun save(sensor: SavedSensor)

    public suspend fun updateBattery(address: String, percent: Int)

    public suspend fun forget(address: String)
}
