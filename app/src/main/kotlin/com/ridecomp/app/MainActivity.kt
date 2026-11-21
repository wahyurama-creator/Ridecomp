package com.ridecomp.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.app.navigation.IncomingDebugScreen
import com.ridecomp.app.navigation.IncomingGpx
import com.ridecomp.app.navigation.IncomingStravaAuth
import com.ridecomp.app.navigation.IncomingWidget
import com.ridecomp.app.widget.RideCompWidget
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.MapPackDownloads
import com.ridecomp.core.usecase.ObserveAppThemeUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.platform.locale.AppLocale
import com.ridecomp.platform.theme.ThemeCache
import com.ridecomp.ui.map.LocalMapCacheLimitBytes
import com.ridecomp.ui.map.LocalMapIoContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject

/** Single activity hosting the Compose UI. */
class MainActivity : ComponentActivity() {
    private val dispatchers: DispatcherProvider by inject()
    private val observeTheme: ObserveAppThemeUseCase by inject()
    private val observeSettings: ObserveRideSettingsUseCase by inject()
    private val mapPacks: MapPackDownloads by inject()

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
            IncomingDebugScreen.offer(DevTools.screenRoute(intent))
        }
        setContent {
            // Maps load their native library on the IO dispatcher, not the main thread (T11.2a).
            // The tile cache limit from Settings, given to MapLibre when a map is prepared (T11.11).
            val cacheLimit = remember { observeSettings().map { it.mapCacheLimit.bytes }.distinctUntilChanged() }
            val cacheLimitBytes by cacheLimit.collectAsStateWithLifecycle(initialValue = null)
            CompositionLocalProvider(
                LocalMapIoContext provides dispatchers.io,
                LocalMapCacheLimitBytes provides cacheLimitBytes,
            ) {
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
        IncomingDebugScreen.offer(DevTools.screenRoute(intent))
    }

    /**
     * Leaving the app: the widget shows the latest plan, ride and week (T9.2); a map pack download pauses
     * (only while the app is open, BAT-05, T11.12) and resumes from its partial file when started again.
     */
    override fun onStop() {
        super.onStop()
        RideCompWidget.refresh(this)
        if (!isChangingConfigurations) mapPacks.pause()
    }
}
