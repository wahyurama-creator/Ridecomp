package com.ridecomp.app.navigation

import android.content.Intent
import com.ridecomp.app.widget.WidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The home screen widget's button (T9.2): open the pre-ride check, or the ride already running. */
object IncomingWidget {
    enum class Target { PRE_RIDE, RIDE }

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    /** Only our own two actions; anything else is ignored (SEC-PLT: MainActivity is exported). */
    fun offer(intent: Intent?) {
        when (intent?.action) {
            WidgetConfig.ACTION_START_RIDE -> _target.value = Target.PRE_RIDE
            WidgetConfig.ACTION_OPEN_RIDE -> _target.value = Target.RIDE
        }
    }

    fun consumed() {
        _target.value = null
    }
}
