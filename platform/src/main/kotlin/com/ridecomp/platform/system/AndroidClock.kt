package com.ridecomp.platform.system

import android.os.SystemClock
import com.ridecomp.core.source.Clock

/** Production [Clock]: wall time from the system, monotonic time from [SystemClock.elapsedRealtime]. */
class AndroidClock : Clock {
    // The one legitimate place to read wall-clock time directly (KT-12).
    @Suppress("ForbiddenMethodCall")
    override fun nowMs(): Long = System.currentTimeMillis()

    // elapsedRealtime keeps counting in deep sleep, which matters for rides with the screen off.
    override fun monotonicMs(): Long = SystemClock.elapsedRealtime()
}
