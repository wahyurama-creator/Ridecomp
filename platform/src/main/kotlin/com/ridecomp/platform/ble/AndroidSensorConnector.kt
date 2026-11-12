package com.ridecomp.platform.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import com.ridecomp.core.engine.sensors.BatteryLevelParser
import com.ridecomp.core.engine.sensors.BleConfig
import com.ridecomp.core.engine.sensors.CscParser
import com.ridecomp.core.engine.sensors.CyclingPowerParser
import com.ridecomp.core.engine.sensors.HeartRateParser
import com.ridecomp.core.engine.sensors.ReconnectBackoff
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorConnector
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.SensorReading
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.Logger
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps every paired sensor connected during a ride (T2.3): connect → discover → subscribe → read battery,
 * then forward parsed notifications. On loss it emits a disconnection, closes the GATT and reconnects with
 * [ReconnectBackoff]; it never stops the ride. Cancelling the flow closes every GATT (09 §1, BAT-01).
 */
class AndroidSensorConnector(private val context: Context, private val clock: Clock, private val logger: Logger) :
    SensorConnector {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun readings(sensors: List<SavedSensor>): Flow<SensorReading> = channelFlow {
        sensors.forEach { sensor -> launch { keepConnected(sensor) } }
    }

    private suspend fun ProducerScope<SensorReading>.keepConnected(sensor: SavedSensor) {
        val backoff = ReconnectBackoff()
        while (isActive) {
            val device = adapter?.takeIf { it.isEnabled && context.hasBlePermission() }?.getRemoteDevice(sensor.address)
            if (device != null) connectOnce(GattClient(context, device), sensor, backoff)
            logger.debug(TAG) { "Sensor ${sensor.name} unavailable, retrying" }
            delay(backoff.nextDelayMs())
        }
    }

    /** One connection attempt; returns when the link is lost. The GATT is always closed (09 §1). */
    private suspend fun ProducerScope<SensorReading>.connectOnce(
        client: GattClient,
        sensor: SavedSensor,
        backoff: ReconnectBackoff,
    ) {
        var isConnected = false
        try {
            if (connectAndSubscribe(client, sensor)) {
                isConnected = true
                backoff.reset()
                emitConnection(sensor, isConnected = true)
                stream(client, sensor)
            }
        } finally {
            client.close()
            // Only a real loss is reported: failed reconnect attempts must not repeat SENSOR_LOST.
            if (isConnected) emitConnection(sensor, isConnected = false)
        }
    }

    private suspend fun connectAndSubscribe(client: GattClient, sensor: SavedSensor): Boolean {
        if (!client.connect() || !client.discoverServices()) return false
        // Only the measurements this device really offers (a power meter can also offer CSC, §A.5).
        val subscribed = BleUuids.MEASUREMENT_BY_SERVICE.count { (service, measurement) ->
            client.hasService(service) && client.enableNotifications(service, measurement)
        }
        if (client.hasService(BleUuids.BATTERY_SERVICE)) client.read(BleUuids.BATTERY_SERVICE, BleUuids.BATTERY_LEVEL)
        return subscribed > 0 || sensor.kinds.isEmpty()
    }

    /** Forwards notifications until the link drops; reads battery every 10 minutes (§A.4). */
    private suspend fun ProducerScope<SensorReading>.stream(client: GattClient, sensor: SavedSensor) {
        val batteryJob = launch { readBatteryPeriodically(client) }
        try {
            client.events.receiveAsFlow()
                .takeWhile { it != GattEvent.Disconnected }
                .filterIsInstance<GattEvent.Value>()
                .collect { event -> event.toReading(sensor)?.let { send(it) } }
        } finally {
            batteryJob.cancel()
        }
    }

    private suspend fun readBatteryPeriodically(client: GattClient) {
        while (currentCoroutineContext().isActive) {
            delay(BleConfig.BATTERY_READ_INTERVAL_MS)
            client.read(BleUuids.BATTERY_SERVICE, BleUuids.BATTERY_LEVEL)
        }
    }

    /** Parses raw bytes; malformed packets are dropped (STB-09, SEC-IN-03). */
    private fun GattEvent.Value.toReading(sensor: SavedSensor): SensorReading? {
        val now = clock.nowMs()
        return when (characteristic) {
            BleUuids.HEART_RATE_MEASUREMENT -> HeartRateParser.parse(bytes)?.let { SensorReading.HeartRate(it, now) }
            BleUuids.CSC_MEASUREMENT -> CscParser.parse(bytes)?.let { SensorReading.SpeedCadence(it, now) }
            BleUuids.CYCLING_POWER_MEASUREMENT -> CyclingPowerParser.parse(bytes)?.let { SensorReading.Power(it, now) }
            BleUuids.BATTERY_LEVEL -> BatteryLevelParser.parse(bytes)?.let { percent ->
                SensorReading.Battery(sensor.primaryKind(), percent, now, sensor.address)
            }
            else -> null
        }
    }

    private fun ProducerScope<SensorReading>.emitConnection(sensor: SavedSensor, isConnected: Boolean) {
        // trySend: also called from `finally` while being cancelled.
        sensor.kinds.forEach { kind -> trySend(SensorReading.Connection(kind, isConnected, clock.nowMs())) }
    }

    private fun SavedSensor.primaryKind(): SensorKind = when {
        SensorKind.POWER in kinds -> SensorKind.POWER
        SensorKind.SPEED_CADENCE in kinds -> SensorKind.SPEED_CADENCE
        else -> SensorKind.HEART_RATE
    }

    private companion object {
        const val TAG = "SensorConnector"
    }
}
