package com.ridecomp.app.di

import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin

/** Guards the composition root: every core abstraction must be resolvable. */
class AppModulesTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `core sources resolve from the app graph`() {
        startKoin { modules(appModules) }

        val koin = getKoin()

        assertNotNull(koin.get<Clock>())
        assertNotNull(koin.get<DispatcherProvider>())
        assertNotNull(koin.get<Logger>())
    }
}
