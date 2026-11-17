package com.ridecomp.platform.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.ridecomp.core.engine.sensors.BleConfig
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What the GATT callback reports; consumed by one coroutine (CONC-02: callbacks only forward). */
sealed interface GattEvent {
    data object Connected : GattEvent

    data object Disconnected : GattEvent

    class Value(val characteristic: UUID, val bytes: ByteArray) : GattEvent
}

/**
 * One BLE connection with a serial operation queue (CONC-03): each GATT operation waits for its callback
 * (or a timeout) before the next one starts — the classic source of Android BLE bugs (03-ARCHITECTURE §4.4).
 * [close] always disconnects **and** closes the GATT (resource table 09 §1).
 */
// Callers check the Bluetooth permission before creating a client (AndroidSensorConnector).
@SuppressLint("MissingPermission")
class GattClient(private val context: Context, private val device: BluetoothDevice) {
    val events: Channel<GattEvent> = Channel(Channel.UNLIMITED)
    private val operationLock = Mutex()
    private var pending: CompletableDeferred<Boolean>? = null
    private var gatt: BluetoothGatt? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val isConnected = status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED
            events.trySend(if (isConnected) GattEvent.Connected else GattEvent.Disconnected)
            if (!isConnected) pending?.complete(false)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            onRead(characteristic.uuid, value, status)
        }

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") // Android 8–12 delivers reads here.
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            onRead(characteristic.uuid, characteristic.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            events.trySend(GattEvent.Value(characteristic.uuid, value))
        }

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") // Android 8–12 delivers notifications here.
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let { events.trySend(GattEvent.Value(characteristic.uuid, it.copyOf())) }
        }
    }

    private fun onRead(uuid: UUID, value: ByteArray, status: Int) {
        val isSuccess = status == BluetoothGatt.GATT_SUCCESS
        if (isSuccess) events.trySend(GattEvent.Value(uuid, value))
        pending?.complete(isSuccess)
    }

    /** Connects and waits for the link (bounded by [BleConfig.CONNECT_TIMEOUT_MS]). */
    suspend fun connect(): Boolean {
        // The only LE-transport overload available down to minSdk 26; its replacement needs a newer API level.
        @Suppress("DEPRECATION")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        val event = withTimeoutOrNull(BleConfig.CONNECT_TIMEOUT_MS) { events.receive() }
        return event == GattEvent.Connected
    }

    suspend fun discoverServices(): Boolean = operation { it.discoverServices() }

    /** Services of the device after discovery. */
    fun hasService(uuid: UUID): Boolean = gatt?.getService(uuid) != null

    /** Enables notifications of [characteristic] in [service]; false when absent or rejected. */
    suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean =
        subscribe(service, characteristic, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)

    /** Enables indications (acknowledged), as a control point needs for its responses (T9.1). */
    suspend fun enableIndications(service: UUID, characteristic: UUID): Boolean =
        subscribe(service, characteristic, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)

    private suspend fun subscribe(service: UUID, characteristic: UUID, value: ByteArray): Boolean {
        val target = gatt?.getService(service)?.getCharacteristic(characteristic)
        val descriptor = target?.getDescriptor(BleUuids.CCCD)
        if (target == null || descriptor == null) return false
        return operation { gatt ->
            gatt.setCharacteristicNotification(target, true) && gatt.writeEnable(descriptor, value)
        }
    }

    /** Writes [value] with a response, e.g. an FTMS control point command (T9.1). */
    suspend fun write(service: UUID, characteristic: UUID, value: ByteArray): Boolean {
        val target = gatt?.getService(service)?.getCharacteristic(characteristic) ?: return false
        return operation { gatt -> gatt.writeWithResponse(target, value) }
    }

    suspend fun read(service: UUID, characteristic: UUID): Boolean {
        val target = gatt?.getService(service)?.getCharacteristic(characteristic) ?: return false
        return operation { it.readCharacteristic(target) }
    }

    /** Disconnect then close: the sensor becomes available to other apps again. */
    fun close() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        pending?.complete(false)
        events.close()
    }

    /** Runs one GATT operation and waits for its callback (CONC-03, CO-09). */
    private suspend fun operation(start: (BluetoothGatt) -> Boolean): Boolean = operationLock.withLock {
        val current = gatt ?: return@withLock false
        val result = CompletableDeferred<Boolean>()
        pending = result
        if (!start(current)) return@withLock false
        withTimeoutOrNull(BleConfig.GATT_OPERATION_TIMEOUT_MS) { result.await() } ?: false
    }

    private fun BluetoothGatt.writeEnable(descriptor: BluetoothGattDescriptor, enable: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeDescriptor(descriptor, enable) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = enable
            @Suppress("DEPRECATION")
            writeDescriptor(descriptor)
        }

    private fun BluetoothGatt.writeWithResponse(target: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeCharacteristic(target, value, type) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            target.writeType = type
            @Suppress("DEPRECATION")
            target.value = value
            @Suppress("DEPRECATION")
            writeCharacteristic(target)
        }
    }
}
