package com.ridecomp.platform.crash

/**
 * Uncaught exception handler that saves a local crash log, then delegates to the previous handler
 * so the system still terminates the process normally (ERR-06).
 */
class CrashLogHandler(private val store: CrashLogStore, private val previous: Thread.UncaughtExceptionHandler?) :
    Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Writing the log must never prevent the original crash handling.
        runCatching { store.write(thread.name, throwable) }
        previous?.uncaughtException(thread, throwable)
    }

    companion object {
        /** Installs the handler as the process-wide default. */
        fun install(store: CrashLogStore) {
            Thread.setDefaultUncaughtExceptionHandler(
                CrashLogHandler(store, Thread.getDefaultUncaughtExceptionHandler()),
            )
        }
    }
}
