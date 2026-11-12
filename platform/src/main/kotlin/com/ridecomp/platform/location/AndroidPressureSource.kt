package com.ridecomp.platform.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.ridecomp.core.model.PressureReading
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.PressureSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Barometer readings at ~1 Hz when the phone has one (05-DATA-MODEL §3.5). Registered only while
 * collected; unregistered in `awaitClose` (resource table 09 §1).
 */
class AndroidPressureSource(context: Context, private val clock: Clock) : PressureSource {
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    override val hasBarometer: Boolean get() = sensor != null

    override fun readings(): Flow<PressureReading> {
        val manager = sensorManager
        val pressure = sensor
        if (manager == null || pressure == null) return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    event.values.firstOrNull()?.let { trySend(PressureReading(clock.nowMs(), it.toDouble())) }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            manager.registerListener(listener, pressure, SAMPLING_PERIOD_US)
            awaitClose { manager.unregisterListener(listener) }
        }
    }

    private companion object {
        /** 1 Hz is enough for altitude and saves battery. */
        const val SAMPLING_PERIOD_US = 1_000_000
    }
}
