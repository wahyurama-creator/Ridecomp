package com.ridecomp.platform.system

import android.util.Log
import com.ridecomp.core.source.Logger

/**
 * [Logger] backed by logcat. Debug output is only produced when [isDebugEnabled] is true
 * (debug builds), so release logs never contain sensitive details (ERR-05, SEC-STO-04).
 */
class AndroidLogger(private val isDebugEnabled: Boolean) : Logger {
    override fun debug(tag: String, message: () -> String) {
        if (isDebugEnabled) Log.d(tag.toLogTag(), message())
    }

    override fun info(tag: String, message: () -> String) {
        Log.i(tag.toLogTag(), message())
    }

    override fun warn(tag: String, throwable: Throwable?, message: () -> String) {
        Log.w(tag.toLogTag(), message(), throwable.takeIf { isDebugEnabled })
    }

    override fun error(tag: String, throwable: Throwable?, message: () -> String) {
        Log.e(tag.toLogTag(), message(), throwable.takeIf { isDebugEnabled })
    }

    private fun String.toLogTag(): String = LOG_TAG_PREFIX + this

    private companion object {
        const val LOG_TAG_PREFIX = "RideComp/"
    }
}
