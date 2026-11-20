package com.ridecomp.app

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.AppTheme
import com.ridecomp.core.usecase.ObserveAppThemeUseCase
import com.ridecomp.platform.theme.ThemeCache
import com.ridecomp.ui.theme.DarkRideCompColors
import com.ridecomp.ui.theme.LightRideCompColors
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.ThemeMode

/**
 * The app theme at the root (T11.9): starts from the cached copy ([initial]), then follows the settings table
 * and keeps the copy equal to it. The status and navigation bars and the window background follow the app
 * theme, not the phone's.
 */
@Composable
fun ComponentActivity.AppThemeRoot(
    initial: AppTheme,
    observeTheme: ObserveAppThemeUseCase,
    content: @Composable () -> Unit,
) {
    val flow = remember(observeTheme) { observeTheme() }
    val theme by flow.collectAsStateWithLifecycle(initialValue = initial)
    val isDark = when (theme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    LaunchedEffect(theme) { ThemeCache.write(this@AppThemeRoot, theme) }
    LaunchedEffect(isDark) { applyWindowTheme(isDark) }
    RideCompTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT, content = content)
}

/** Whether [theme] is dark now, outside composition (before the first frame). */
fun ComponentActivity.isDark(theme: AppTheme): Boolean = when (theme) {
    AppTheme.DARK -> true
    AppTheme.LIGHT -> false
    AppTheme.SYSTEM ->
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}

/** Window background and system bar icons matching the app theme. */
fun ComponentActivity.applyWindowTheme(isDark: Boolean) {
    val colors = if (isDark) DarkRideCompColors else LightRideCompColors
    window.setBackgroundDrawable(colors.bg.toArgb().toDrawable())
    enableEdgeToEdge(
        statusBarStyle = if (isDark) {
            SystemBarStyle.dark(TRANSPARENT)
        } else {
            SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
        },
        navigationBarStyle = if (isDark) {
            SystemBarStyle.dark(DARK_NAV_SCRIM)
        } else {
            SystemBarStyle.light(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM)
        },
    )
}

private const val TRANSPARENT = 0x00000000

/** AndroidX's default navigation bar scrims (3-button navigation on older phones). */
private const val LIGHT_NAV_SCRIM = 0xE6FFFFFF.toInt()
private const val DARK_NAV_SCRIM = 0x801B1B1B.toInt()
