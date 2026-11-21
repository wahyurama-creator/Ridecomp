package com.ridecomp.core.testing

import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorConnector
import com.ridecomp.core.engine.sensors.SensorProbe
import com.ridecomp.core.engine.sensors.SensorProbeResult
import com.ridecomp.core.engine.sensors.SensorReading
import com.ridecomp.core.engine.sensors.SensorRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

public class FakeSensorRepository : SensorRepository {
    private val sensors = MutableStateFlow<Map<String, SavedSensor>>(emptyMap())

    override fun observeAll(): Flow<List<SavedSensor>> = sensors.map { it.values.sortedBy(SavedSensor::createdAtMs) }

    override suspend fun getAutoConnect(): List<SavedSensor> = sensors.value.values.filter { it.isAutoConnect }

    override suspend fun save(sensor: SavedSensor) {
        sensors.value = sensors.value + (sensor.address to sensor)
    }

    override suspend fun updateBattery(address: String, percent: Int) {
        sensors.value[address]?.let { save(it.copy(lastBatteryPercent = percent)) }
    }

    override suspend fun forget(address: String) {
        sensors.value = sensors.value - address
    }
}

/** Emits [results] once per scan. */
public class FakeBleScanner(
    public var results: List<DiscoveredSensor> = emptyList(),
    public var isEnabled: Boolean = true,
    public var isPermitted: Boolean = true,
    /** Extra devices only the wide scan finds (T12.2). */
    public var wideResults: List<DiscoveredSensor> = emptyList(),
    /** Keeps the scan running after its results until the collector stops, like a real scan before its timeout. */
    public var isEndless: Boolean = false,
) : BleScanner {
    override fun isBluetoothEnabled(): Boolean = isEnabled

    override fun hasPermission(): Boolean = isPermitted

    override fun scan(timeoutMs: Long, isWide: Boolean): Flow<DiscoveredSensor> = flow {
        emitAll((if (isWide) results + wideResults else results).asFlow())
        if (isEndless) awaitCancellation()
    }
}

/** Answers probes from [results] by address; unknown addresses are unreachable (T12.2). */
public class FakeSensorProbe(public val results: MutableMap<String, SensorProbeResult> = mutableMapOf()) : SensorProbe {
    public val probed: MutableList<String> = mutableListOf()

    override suspend fun probe(address: String): SensorProbeResult {
        probed += address
        return results[address] ?: SensorProbeResult.Unreachable
    }
}

/** Tests push readings into [readingsFlow]; [activeCollectors] proves GATT is released when not collected. */
public class FakeSensorConnector : SensorConnector {
    public val readingsFlow: MutableSharedFlow<SensorReading> = MutableSharedFlow(extraBufferCapacity = 64)
    public var activeCollectors: Int = 0
        private set
    public var lastSensors: List<SavedSensor> = emptyList()
        private set

    override fun readings(sensors: List<SavedSensor>): Flow<SensorReading> = readingsFlow
        .onStart {
            lastSensors = sensors
            activeCollectors++
        }
        .onCompletion { activeCollectors-- }
}
