package com.ridecomp.app

import android.app.Application
import android.content.Context
import com.ridecomp.app.di.KoinRecordingDependencies
import com.ridecomp.app.di.appModules
import com.ridecomp.platform.crash.CrashLogHandler
import com.ridecomp.platform.crash.CrashLogStore
import com.ridecomp.platform.locale.AppLocale
import com.ridecomp.platform.service.RecordingDependencies
import com.ridecomp.platform.service.RecordingDependenciesProvider
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/** Application entry point: composition root for dependency injection. */
class RideCompApplication :
    Application(),
    RecordingDependenciesProvider {
    override val recordingDependencies: RecordingDependencies by lazy { KoinRecordingDependencies(getKoin()) }

    /** Default ride names and other app-context texts in the chosen language (D-88). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        DevTools.install()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@RideCompApplication)
            allowOverride(true)
            modules(appModules + DevTools.koinOverrides)
        }
        CrashLogHandler.install(get<CrashLogStore>())
    }
}
