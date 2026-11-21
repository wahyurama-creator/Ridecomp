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

    /**
     * "Show all Bluetooth devices" (T12.2) leaves out devices weaker than this: too far away to be the
     * rider's own watch or sensor.
     */
    public const val WIDE_SCAN_MIN_RSSI_DBM: Int = -90

    /**
     * A device without a name is still listed when it is this close (practically touching the phone, like a
     * watch on the wrist holding it): many watches advertise without a name (device test of T12.2).
     */
    public const val WIDE_SCAN_NAMELESS_MIN_RSSI_DBM: Int = -60
}

/** Whether the wide scan lists [sensor] (T12.2): named and in reach, or nameless but right next to the phone. */
public fun isWideScanCandidate(sensor: DiscoveredSensor): Boolean = if (sensor.name.isNullOrBlank()) {
    sensor.rssiDbm >= BleConfig.WIDE_SCAN_NAMELESS_MIN_RSSI_DBM
} else {
    sensor.rssiDbm >= BleConfig.WIDE_SCAN_MIN_RSSI_DBM
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
    /** A smart trainer (FTMS): power/cadence/speed and ERG control (T9.1). */
    val isTrainer: Boolean = false,
    /** A GloryFit watch (Aolon GTR 3): heart rate through its own protocol, experimental (T12.5b). */
    val isGloryFitWatch: Boolean = false,
)

/** True when the last known battery level is below the warning threshold (§A.4). */
public val SavedSensor.isBatteryLow: Boolean
    get() = lastBatteryPercent?.let { it < SensorLimits.LOW_BATTERY_PERCENT } == true

/** A device found by a scan (02-DESIGN §3.8). */
public data class DiscoveredSensor(
    val address: String,
    val name: String?,
    val kinds: Set<SensorKind>,
    val rssiDbm: Int,
    /** Advertises the Fitness Machine service (T9.1). */
    val isTrainer: Boolean = false,
    /** Turned out to be a GloryFit watch when checked (T12.5b). */
    val isGloryFitWatch: Boolean = false,
)

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

    /**
     * HR / CSC / CP / FTMS devices; with [isWide] every named device in reach (T12.2), whose kinds are
     * unknown until [SensorProbe] checks it. Always ends after [timeoutMs].
     */
    public fun scan(timeoutMs: Long = BleConfig.SCAN_TIMEOUT_MS, isWide: Boolean = false): Flow<DiscoveredSensor>
}

/** What a device turned out to offer once connected (T12.2). */
public sealed interface SensorProbeResult {
    /**
     * At least one service RideComp reads (heart rate, speed/cadence, power, smart trainer), or the heart rate
     * of a GloryFit watch (T12.5b).
     */
    public data class Supported(
        val kinds: Set<SensorKind>,
        val isTrainer: Boolean,
        val isGloryFitWatch: Boolean = false,
    ) : SensorProbeResult

    /** Connected, but none of those services: e.g. a watch that keeps its heart rate to its own app. */
    public data object NoSupportedService : SensorProbeResult

    /** Could not connect or list its services in time. */
    public data object Unreachable : SensorProbeResult
}

/**
 * Connects to a device that did not advertise its services, lists them, and always disconnects and closes
 * the GATT afterwards (resource table 09 §1), bounded by the connect and operation timeouts.
 */
public interface SensorProbe {
    public suspend fun probe(address: String): SensorProbeResult
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
