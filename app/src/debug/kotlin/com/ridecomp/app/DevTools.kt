package com.ridecomp.app

import android.content.Context
import android.os.StrictMode
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.SensorConnector
import com.ridecomp.core.source.LocationSource
import com.ridecomp.platform.ble.AndroidBleScanner
import com.ridecomp.platform.ble.AndroidSensorConnector
import com.ridecomp.platform.location.AndroidLocationSource
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.compose.koinInject
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Debug-only diagnostics and developer tools. This file exists only in the `debug` source set, so nothing
 * here can be active in a release build (SEC-BLD-04): StrictMode (STB-01), LeakCanary as a debug
 * dependency (MEM-07), and the GPS simulator (07-TEST-PLAN §2, TEST-08).
 */
object DevTools {
    const val IS_DEVELOPER_MENU_AVAILABLE = true
    internal const val PREFERENCES = "ridecomp_developer"
    internal const val KEY_SIMULATE_GPS = "simulate_gps"
    internal const val KEY_SIMULATE_SENSORS = "simulate_sensors"

    fun install() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }

    /** Replaces the location source with the switchable real/simulated one. */
    val koinOverrides: List<Module> = listOf(
        module {
            single<LocationSource> {
                DebugLocationSource(
                    real = AndroidLocationSource(androidContext(), get()),
                    simulated = SimulatedLocationSource(get()),
                    settings = get(),
                )
            }
            single { DeveloperSettings(androidContext()) }
            single<BleScanner> { DebugBleScanner(AndroidBleScanner(androidContext()), get()) }
            single<SensorConnector> {
                DebugSensorConnector(AndroidSensorConnector(androidContext(), get(), get()), get())
            }
        },
    )

    @Composable
    fun DeveloperScreen(modifier: Modifier = Modifier) {
        val settings = koinInject<DeveloperSettings>()
        val isSimulating by settings.isSimulatingGpsFlow.collectAsStateWithLifecycle()
        val isSimulatingSensors by settings.isSimulatingSensorsFlow.collectAsStateWithLifecycle()
        ScreenScaffold(modifier = modifier, title = stringResource(R.string.developer_title)) {
            Column {
                SwitchRow(
                    label = stringResource(R.string.developer_simulate_gps),
                    description = stringResource(R.string.developer_simulate_gps_description),
                    isChecked = isSimulating,
                    onCheckedChange = { checked -> settings.isSimulatingGps = checked },
                )
                SwitchRow(
                    label = stringResource(R.string.developer_simulate_sensors),
                    description = stringResource(R.string.developer_simulate_sensors_description),
                    isChecked = isSimulatingSensors,
                    onCheckedChange = { checked -> settings.isSimulatingSensors = checked },
                )
            }
        }
    }
}

/**
 * Developer switches, kept in memory and read from / written to SharedPreferences on a background thread:
 * opening preferences touches the disk, and the switch is read on the main thread (StrictMode, STB-01).
 */
class DeveloperSettings(context: Context) {
    private val io = Executors.newSingleThreadExecutor()
    private val preferences by lazy {
        context.applicationContext.getSharedPreferences(DevTools.PREFERENCES, Context.MODE_PRIVATE)
    }

    private val gps = Switch(DevTools.KEY_SIMULATE_GPS)
    private val sensors = Switch(DevTools.KEY_SIMULATE_SENSORS)

    /** Observed by the developer screen, so it shows the stored value once loaded. */
    val isSimulatingGpsFlow: StateFlow<Boolean> = gps.flow
    val isSimulatingSensorsFlow: StateFlow<Boolean> = sensors.flow

    var isSimulatingGps: Boolean
        get() = gps.value
        set(value) {
            gps.value = value
        }

    /** Virtual HR, speed/cadence and power sensors in scans and rides ([SimulatedSensors]). */
    var isSimulatingSensors: Boolean
        get() = sensors.value
        set(value) {
            sensors.value = value
        }

    /** One stored switch; a change made before the initial load is not overwritten by it. */
    private inner class Switch(private val key: String) {
        private val state = MutableStateFlow(false)
        val flow: StateFlow<Boolean> = state.asStateFlow()

        @Volatile
        private var isChanged = false

        init {
            io.execute {
                val stored = preferences.getBoolean(key, false)
                if (!isChanged) state.value = stored
            }
        }

        var value: Boolean
            get() = state.value
            set(value) {
                isChanged = true
                state.value = value
                io.execute { preferences.edit { putBoolean(key, value) } }
            }
    }
}
