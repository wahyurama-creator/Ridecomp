package com.ridecomp.core.testing

import com.ridecomp.core.source.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher

/** Routes every dispatcher to one test dispatcher so `runTest` controls all coroutines (TEST-03). */
public class TestDispatcherProvider(public val testDispatcher: CoroutineDispatcher = StandardTestDispatcher()) :
    DispatcherProvider {
    override val main: CoroutineDispatcher get() = testDispatcher
    override val default: CoroutineDispatcher get() = testDispatcher
    override val io: CoroutineDispatcher get() = testDispatcher
}
