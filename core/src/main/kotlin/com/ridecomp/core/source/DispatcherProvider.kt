package com.ridecomp.core.source

import kotlinx.coroutines.CoroutineDispatcher

/** Injected coroutine dispatchers (CO-03): CPU work on [default], DB/file on [io], UI only on [main]. */
public interface DispatcherProvider {
    public val main: CoroutineDispatcher
    public val default: CoroutineDispatcher
    public val io: CoroutineDispatcher
}
