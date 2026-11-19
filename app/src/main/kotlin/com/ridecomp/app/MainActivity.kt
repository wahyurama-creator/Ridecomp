package com.ridecomp.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.ridecomp.app.navigation.IncomingGpx
import com.ridecomp.app.navigation.IncomingStravaAuth
import com.ridecomp.app.navigation.IncomingWidget
import com.ridecomp.app.widget.RideCompWidget
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.platform.locale.AppLocale
import com.ridecomp.ui.map.LocalMapIoContext
import com.ridecomp.ui.theme.RideCompTheme
import org.koin.android.ext.android.inject

/** Single activity hosting the Compose UI. */
class MainActivity : ComponentActivity() {
    private val dispatchers: DispatcherProvider by inject()

    /** The chosen app language on Android 8–12 (D-88). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            IncomingGpx.offer(intent)
            IncomingStravaAuth.offer(intent)
            IncomingWidget.offer(intent)
        }
        setContent {
            // Maps load their native library on the IO dispatcher, not the main thread (T11.2a).
            CompositionLocalProvider(LocalMapIoContext provides dispatchers.io) {
                RideCompTheme {
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
