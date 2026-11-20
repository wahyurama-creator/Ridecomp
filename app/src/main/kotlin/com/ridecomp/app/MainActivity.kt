package com.ridecomp.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.ridecomp.app.navigation.IncomingGpx
import com.ridecomp.app.navigation.IncomingStravaAuth
import com.ridecomp.app.navigation.IncomingWidget
import com.ridecomp.app.widget.RideCompWidget
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.ObserveAppThemeUseCase
import com.ridecomp.platform.locale.AppLocale
import com.ridecomp.platform.theme.ThemeCache
import com.ridecomp.ui.map.LocalMapIoContext
import org.koin.android.ext.android.inject

/** Single activity hosting the Compose UI. */
class MainActivity : ComponentActivity() {
    private val dispatchers: DispatcherProvider by inject()
    private val observeTheme: ObserveAppThemeUseCase by inject()

    /** The chosen app language on Android 8–12 (D-88). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The cached theme colors the window and bars before the first frame (T11.9, D-126).
        val theme = ThemeCache.read(this)
        applyWindowTheme(isDark(theme))
        if (savedInstanceState == null) {
            IncomingGpx.offer(intent)
            IncomingStravaAuth.offer(intent)
            IncomingWidget.offer(intent)
        }
        setContent {
            // Maps load their native library on the IO dispatcher, not the main thread (T11.2a).
            CompositionLocalProvider(LocalMapIoContext provides dispatchers.io) {
                AppThemeRoot(initial = theme, observeTheme = observeTheme) {
                    RideCompApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        IncomingGpx.offer(intent)
        IncomingStravaAuth.offer(intent)
        IncomingWidget.offer(intent)
    }

    /** Leaving the app: the widget shows the latest plan, ride and week (T9.2). */
    override fun onStop() {
        super.onStop()
        RideCompWidget.refresh(this)
    }
}
