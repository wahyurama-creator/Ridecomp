package com.ridecomp.platform.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.ridecomp.core.engine.sensors.BatteryLevelParser
import com.ridecomp.core.engine.sensors.BleConfig
import com.ridecomp.core.engine.sensors.CscParser
import com.ridecomp.core.engine.sensors.CyclingPowerParser
import com.ridecomp.core.engine.sensors.FtmsCommands
import com.ridecomp.core.engine.sensors.GloryFitCommands
import com.ridecomp.core.engine.sensors.GloryFitConfig
import com.ridecomp.core.engine.sensors.GloryFitParser
import com.ridecomp.core.engine.sensors.HeartRateParser
import com.ridecomp.core.engine.sensors.HeldHeartRate
import com.ridecomp.core.engine.sensors.IndoorBikeDataParser
import com.ridecomp.core.engine.sensors.ReconnectBackoff
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorConnector
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.SensorReading
import com.ridecomp.core.engine.sensors.TrainerControl
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps every paired sensor connected during a ride (T2.3): connect → discover → subscribe → read battery,
 * then forward parsed notifications. On loss it emits a disconnection, closes the GATT and reconnects with
 * [ReconnectBackoff]; it never stops the ride. Cancelling the flow closes every GATT (09 §1, BAT-01).
 */
class AndroidSensorConnector(
    private val context: Context,
    private val clock: Clock,
    private val logger: Logger,
    private val trainer: TrainerControl,
) : SensorConnector {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun readings(sensors: List<SavedSensor>): Flow<SensorReading> = channelFlow {
        sensors.forEach { sensor -> launch { keepConnected(sensor) } }
    }

    private suspend fun ProducerScope<SensorReading>.keepConnected(sensor: SavedSensor) {
        // A stored address that is not a Bluetooth address (e.g. a debug simulated sensor restored into a
        // release build) would throw in getRemoteDevice and end every sensor's flow: skip it (ERR-04).
        if (!BluetoothAdapter.checkBluetoothAddress(sensor.address)) {
            logger.warn(TAG) { "Sensor ${sensor.name} has no valid Bluetooth address, skipped" }
            return
        }
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
            if (isConnected && sensor.isGloryFitWatch) stopWorkout(client)
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
        val hasTrainerData = sensor.isTrainer &&
            client.hasService(BleUuids.FITNESS_MACHINE_SERVICE) &&
            client.enableNotifications(BleUuids.FITNESS_MACHINE_SERVICE, BleUuids.INDOOR_BIKE_DATA)
        // A GloryFit watch measures heart rate only during a workout (T12.5b, 04-INTEGRATIONS §A.5a).
        val hasWatchData = sensor.isGloryFitWatch &&
            client.enableNotifications(BleUuids.GLORYFIT_SERVICE, BleUuids.GLORYFIT_DATA) &&
            client.write(BleUuids.GLORYFIT_SERVICE, BleUuids.GLORYFIT_COMMAND, GloryFitCommands.START_WORKOUT)
        if (client.hasService(BleUuids.BATTERY_SERVICE)) client.read(BleUuids.BATTERY_SERVICE, BleUuids.BATTERY_LEVEL)
        return subscribed > 0 || hasTrainerData || hasWatchData || sensor.kinds.isEmpty()
    }

    /** Forwards notifications until the link drops; reads battery every 10 minutes (§A.4). */
    private suspend fun ProducerScope<SensorReading>.stream(client: GattClient, sensor: SavedSensor) {
        val batteryJob = launch { readBatteryPeriodically(client) }
        val ergJob = if (sensor.isTrainer) launch { controlTrainer(client) } else null
        val heldHeartRate = HeldHeartRate()
        val watchJob = if (sensor.isGloryFitWatch) launch { tickWatch(client, heldHeartRate) } else null
        try {
            client.events.receiveAsFlow()
                .takeWhile { it != GattEvent.Disconnected }
                .filterIsInstance<GattEvent.Value>()
                .collect { event ->
                    if (event.characteristic == BleUuids.GLORYFIT_DATA) {
                        GloryFitParser.parseHeartRate(event.bytes)?.let { measurement ->
                            heldHeartRate.update(measurement, clock.nowMs())
                            send(SensorReading.HeartRate(measurement, clock.nowMs()))
                        }
                    } else {
                        event.toReading(sensor)?.let { send(it) }
                    }
                }
        } finally {
            batteryJob.cancel()
            ergJob?.cancel()
            watchJob?.cancel()
            if (sensor.isTrainer) trainer.onLink(isConnected = false, isControlled = false)
        }
    }

    /**
     * ERG (T9.1, D-95): takes control of the trainer (FTMS: indications on the control point, Request
     * Control, Start), then writes the target power whenever it changes. Without control the trainer
     * still works as a sensor.
     */
    private suspend fun controlTrainer(client: GattClient) {
        val service = BleUuids.FITNESS_MACHINE_SERVICE
        val point = BleUuids.FITNESS_MACHINE_CONTROL_POINT
        val isControlled = client.enableIndications(service, point) &&
            client.write(service, point, FtmsCommands.REQUEST_CONTROL) &&
            client.write(service, point, FtmsCommands.START)
        trainer.onLink(isConnected = true, isControlled = isControlled)
        if (!isControlled) return
        trainer.state.map { it.targetW }.distinctUntilChanged().filterNotNull().collect { watts ->
            if (!client.write(service, point, FtmsCommands.setTargetPower(watts))) {
                logger.warn(TAG) { "Trainer did not accept the ERG target" }
            }
        }
    }

    /**
     * Every second: the workout clock the GloryFit app also sends, and the held heart rate again, since the
     * watch notifies only changes while a ride sample is stale after 3 s (T12.5b, D-136).
     */
    private suspend fun ProducerScope<SensorReading>.tickWatch(client: GattClient, held: HeldHeartRate) {
        val startMs = clock.nowMs()
        while (currentCoroutineContext().isActive) {
            delay(GloryFitConfig.TICK_INTERVAL_MS)
            val nowMs = clock.nowMs()
            held.current(nowMs)?.let { send(SensorReading.HeartRate(it, nowMs)) }
            client.write(
                BleUuids.GLORYFIT_SERVICE,
                BleUuids.GLORYFIT_COMMAND,
                GloryFitCommands.workoutClock(
                    nowMs - startMs,
                ),
            )
        }
    }

    /** Ends the watch's workout so it stops measuring; runs while being cancelled, bounded by a timeout. */
    private suspend fun stopWorkout(client: GattClient) {
        withContext(NonCancellable) {
            withTimeoutOrNull(GloryFitConfig.STOP_TIMEOUT_MS) {
                client.write(BleUuids.GLORYFIT_SERVICE, BleUuids.GLORYFIT_COMMAND, GloryFitCommands.STOP_WORKOUT)
            }
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
            BleUuids.INDOOR_BIKE_DATA -> IndoorBikeDataParser.parse(bytes)?.let { SensorReading.Trainer(it, now) }
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
