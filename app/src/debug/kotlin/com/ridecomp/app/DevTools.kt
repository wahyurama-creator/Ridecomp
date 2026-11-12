package com.ridecomp.app

import android.os.StrictMode

/**
 * Debug-only diagnostics (STB-01). This file exists only in the `debug` source set, so nothing here can
 * be active in a release build (SEC-BLD-04). LeakCanary installs itself as a debug-only dependency (MEM-07).
 */
object DevTools {
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
}
