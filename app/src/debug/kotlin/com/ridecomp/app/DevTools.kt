package com.ridecomp.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
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
import com.ridecomp.platform.system.AndroidLogger
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import leakcanary.LeakCanary
import org.koin.android.ext.koin.androidContext
import org.koin.compose.koinInject
import org.koin.core.module.Module
import org.koin.dsl.module
import shark.AndroidReferenceMatchers

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

    /** The screen asked for by the comparison screenshot script, if any ([DebugScreenRoute]). */
    fun screenRoute(intent: Intent?): String? = DebugScreenRoute.from(intent)

    fun install() {
        // Android 9+: our listener logs each violation with its stack; older phones keep the plain log.
        val hasListener = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .apply { if (hasListener) penaltyListener(executor, ::report) else penaltyLog() }
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .apply { if (hasListener) penaltyListener(executor, ::report) else penaltyLog() }
                .build(),
        )
        // RULE-EXCEPTION: OEM framework leak, not app code. realme/OPPO ColorOS adds a static
        // android.gesture.UniGestureDetector that keeps the last destroyed window's context (seen on the
        // realme RMX3830, Android 15). It is reported as a library leak so app leaks stay visible (D-87).
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults + AndroidReferenceMatchers.staticFieldLeak(
                className = OEM_GESTURE_DETECTOR,
                fieldName = OEM_GESTURE_DETECTOR_FIELD,
                description = "ColorOS keeps the last window context in a static gesture detector",
            ),
        )
    }

    /** One thread for StrictMode reports, off the thread that broke the policy. */
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Every violation with its type and full stack under one tag: some OEM logcats (ColorOS) drop the stack
     * lines that `penaltyLog` writes, which hid the cause of earlier findings.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun report(violation: Violation) {
        // RULE-EXCEPTION: violations raised inside a system service during a Binder call (realme/ColorOS
        // CabcService writes a backlight node whenever an activity resumes) are reported to the calling app
        // but are not app code; they are logged quietly so app findings stay visible (D-132).
        if (violation.stackTrace.any { it.className.startsWith(SYSTEM_SERVER_PACKAGE) }) {
            logger.debug(STRICT_MODE_TAG) { "System service, not app code: ${violation.javaClass.simpleName}" }
        } else {
            logger.warn(STRICT_MODE_TAG, violation) { violation.javaClass.simpleName }
        }
    }

    private const val SYSTEM_SERVER_PACKAGE = "com.android.server."

    /** Logcat tag `RideComp/StrictMode` (the app logger adds the prefix). */
    private const val STRICT_MODE_TAG = "StrictMode"

    /** Debug build: stacks included. */
    private val logger = AndroidLogger(isDebugEnabled = true)
    private const val OEM_GESTURE_DETECTOR = "android.gesture.UniGestureDetector"
    private const val OEM_GESTURE_DETECTOR_FIELD = "mUniGestureDetector"

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
                DebugSensorConnector(AndroidSensorConnector(androidContext(), get(), get(), get()), get(), get(), get())
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
