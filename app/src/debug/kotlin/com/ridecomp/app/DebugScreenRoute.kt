package com.ridecomp.app

import android.content.Intent

/**
 * Opens one screen directly for the comparison screenshots (T13.2, `scripts/screens/`):
 * `adb shell am start -n com.ridecomp.app.debug/com.ridecomp.app.MainActivity --es com.ridecomp.debug.SCREEN settings`.
 * Debug source set only (SEC-BLD-04): the release [DevTools] ignores the extra.
 */
object DebugScreenRoute {
    const val EXTRA_SCREEN = "com.ridecomp.debug.SCREEN"

    /** A route name, optionally with one id or enum argument (`help/WATCHES`, `activity/12`). */
    private val ROUTE_PATTERN = Regex("[a-z_]{1,32}(/[A-Za-z0-9_]{1,32})?")

    /** The route in [intent], or null when absent or not shaped like a route (SEC-IN: MainActivity is exported). */
    fun from(intent: Intent?): String? = parse(intent?.getStringExtra(EXTRA_SCREEN))

    fun parse(value: String?): String? = value?.takeIf { ROUTE_PATTERN.matches(it) }
}
