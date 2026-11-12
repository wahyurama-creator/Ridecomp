package com.ridecomp.app

import android.content.Context
import android.content.SharedPreferences
import android.os.StrictMode
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.ridecomp.core.source.LocationSource
import com.ridecomp.platform.location.AndroidLocationSource
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SwitchRow
import org.koin.android.ext.koin.androidContext
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
                    preferences = androidContext().developerPreferences(),
                )
            }
        },
    )

    @Composable
    fun DeveloperScreen(modifier: Modifier = Modifier) {
        val preferences = LocalContext.current.developerPreferences()
        var isSimulating by remember { mutableStateOf(preferences.getBoolean(KEY_SIMULATE_GPS, false)) }
        ScreenScaffold(modifier = modifier, title = stringResource(R.string.developer_title)) {
            Column {
                SwitchRow(
                    label = stringResource(R.string.developer_simulate_gps),
                    description = stringResource(R.string.developer_simulate_gps_description),
                    isChecked = isSimulating,
                    onCheckedChange = { checked ->
                        isSimulating = checked
                        preferences.edit { putBoolean(KEY_SIMULATE_GPS, checked) }
                    },
                )
            }
        }
    }
}

private fun Context.developerPreferences(): SharedPreferences =
    getSharedPreferences(DevTools.PREFERENCES, Context.MODE_PRIVATE)
