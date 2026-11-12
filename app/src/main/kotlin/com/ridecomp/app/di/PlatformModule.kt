package com.ridecomp.app.di

import com.ridecomp.app.BuildConfig
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.platform.crash.CrashLogConfig
import com.ridecomp.platform.crash.CrashLogStore
import com.ridecomp.platform.system.AndroidClock
import com.ridecomp.platform.system.AndroidLogger
import com.ridecomp.platform.system.StandardDispatcherProvider
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Binds :core source interfaces to their :platform implementations (ARCH-03). */
val platformModule = module {
    single<Clock> { AndroidClock() }
    single<DispatcherProvider> { StandardDispatcherProvider() }
    single<Logger> { AndroidLogger(isDebugEnabled = BuildConfig.DEBUG) }
    single { CrashLogStore(File(androidContext().filesDir, CrashLogConfig.DIRECTORY_NAME), get()) }
}
