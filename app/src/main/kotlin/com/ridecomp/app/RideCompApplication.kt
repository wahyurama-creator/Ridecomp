package com.ridecomp.app

import android.app.Application
import com.ridecomp.app.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/** Application entry point: composition root for dependency injection. */
class RideCompApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@RideCompApplication)
            modules(appModules)
        }
    }
}
