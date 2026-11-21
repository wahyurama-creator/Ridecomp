package com.ridecomp.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A screen opened by the comparison screenshot script (T13.2); always null in release builds. */
object IncomingDebugScreen {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    fun offer(route: String?) {
        if (route != null) _route.value = route
    }

    fun consumed() {
        _route.value = null
    }
}
