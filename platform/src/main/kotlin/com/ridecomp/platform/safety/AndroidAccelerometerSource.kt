package com.ridecomp.platform.safety

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.ridecomp.core.engine.safety.AccelSample
import com.ridecomp.core.source.AccelerometerSource
import com.ridecomp.core.source.Clock
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Accelerometer magnitude at `SENSOR_DELAY_GAME` (about 50 Hz, 12-FEATURE-SPECS §5.2). Registered only
 * while collected (recording with incident detection on); unregistered in `awaitClose` (09 §1). The
 * callback only forwards (CONC-02); when the collector is slow, samples are dropped, never queued unbounded.
 */
class AndroidAccelerometerSource(context: Context, private val clock: Clock) : AccelerometerSource {
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val hasAccelerometer: Boolean get() = sensor != null

    override fun samples(): Flow<AccelSample> {
        val manager = sensorManager
        val accelerometer = sensor
        if (manager == null || accelerometer == null) return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val values = event.values
                    if (values.size < AXES) return
                    val (x, y, z) = values
                    trySend(AccelSample(clock.nowMs(), sqrt((x * x + y * y + z * z).toDouble())))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { manager.unregisterListener(listener) }
        }
    }

    private companion object {
        const val AXES = 3
    }
}
