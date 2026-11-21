package com.ridecomp.platform.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.SensorProbe
import com.ridecomp.core.engine.sensors.SensorProbeResult
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import kotlinx.coroutines.withContext

/**
 * Lists the services of a device found by the wide scan (T12.2): connect, discover, check for heart rate,
 * speed/cadence, power, the Fitness Machine service and a GloryFit watch (T12.5b), then always
 * `disconnect()` + `close()` so the device is free again (resource table 09 §1). Bounded by the connect and
 * GATT operation timeouts of [GattClient].
 */
class AndroidSensorProbe(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : SensorProbe {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    override suspend fun probe(address: String): SensorProbeResult = withContext(dispatchers.io) {
        val device = adapter?.takeIf { it.isEnabled && context.hasBlePermission() }?.getRemoteDevice(address)
            ?: return@withContext SensorProbeResult.Unreachable
        val client = GattClient(context, device)
        try {
            if (!client.connect() || !client.discoverServices()) {
                SensorProbeResult.Unreachable
            } else {
                val kinds = BleUuids.SERVICE_BY_KIND.filterValues { client.hasService(it) }.keys
                val isTrainer = client.hasService(BleUuids.FITNESS_MACHINE_SERVICE)
                // A smart trainer gives power, cadence and speed through the Fitness Machine service (T9.1).
                val allKinds = kinds + if (isTrainer) setOf(SensorKind.POWER, SensorKind.SPEED_CADENCE) else emptySet()
                when {
                    allKinds.isNotEmpty() -> SensorProbeResult.Supported(allKinds, isTrainer)
                    // Standard heart rate wins; otherwise a GloryFit watch shares it its own way (T12.5b).
                    client.hasService(BleUuids.GLORYFIT_SERVICE) ->
                        SensorProbeResult.Supported(
                            setOf(SensorKind.HEART_RATE),
                            isTrainer = false,
                            isGloryFitWatch = true,
                        )
                    else -> SensorProbeResult.NoSupportedService
                }
            }
        } finally {
            client.close()
            logger.debug(TAG) { "Probed a device found by the wide scan" }
        }
    }

    private companion object {
        const val TAG = "SensorProbe"
    }
}
