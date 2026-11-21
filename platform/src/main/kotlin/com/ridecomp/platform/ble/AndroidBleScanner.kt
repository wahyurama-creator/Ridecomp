package com.ridecomp.platform.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.isWideScanCandidate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** True when the app may scan and connect to BLE devices on this Android version. */
internal fun Context.hasBlePermission(): Boolean {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // Android 8–11: BLE scans need location permission (03-ARCHITECTURE §2).
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
}

/**
 * BLE scan filtered on HR / CSC / CP services (04-INTEGRATIONS §A.5), or without a filter for "show all
 * Bluetooth devices" (T12.2: named devices in reach, nameless ones only right next to the phone). The scan
 * stops after [timeoutMs] or when the collector stops (`stopScan` in `awaitClose`, BAT-04, resource table 09 §1).
 */
class AndroidBleScanner(private val context: Context) : BleScanner {
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun isBluetoothEnabled(): Boolean = bluetooth?.isEnabled == true

    override fun hasPermission(): Boolean = context.hasBlePermission()

    // Permission is checked through hasPermission() before scanning (ScanSensorsUseCase.blocker).
    @SuppressLint("MissingPermission")
    override fun scan(timeoutMs: Long, isWide: Boolean): Flow<DiscoveredSensor> = callbackFlow {
        val scanner = requireNotNull(bluetooth?.bluetoothLeScanner) { "Bluetooth is off" }
        if (!hasPermission()) throw SecurityException("Bluetooth permission not granted")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val sensor = result.toDiscovered()
                if (!isWide || isWideScanCandidate(sensor)) trySend(sensor)
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }
        // No filter for the wide scan: watches often share heart rate without advertising the service.
        val filters = if (isWide) {
            emptyList()
        } else {
            (BleUuids.SERVICE_BY_KIND.values + BleUuids.FITNESS_MACHINE_SERVICE).map {
                ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
        launch {
            delay(timeoutMs)
            close()
        }
        awaitClose { scanner.stopScan(callback) }
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toDiscovered(): DiscoveredSensor {
        val uuids = scanRecord?.serviceUuids.orEmpty().map { it.uuid }
        // A smart trainer gives power, cadence and speed through the Fitness Machine service (T9.1).
        val isTrainer = BleUuids.FITNESS_MACHINE_SERVICE in uuids
        val trainerKinds = if (isTrainer) setOf(SensorKind.POWER, SensorKind.SPEED_CADENCE) else emptySet()
        return DiscoveredSensor(
            address = device.address,
            name = scanRecord?.deviceName ?: device.name,
            kinds = uuids.mapNotNull(BleUuids::kindOf).toSet() + trainerKinds,
            rssiDbm = rssi,
            isTrainer = isTrainer,
        )
    }
}
