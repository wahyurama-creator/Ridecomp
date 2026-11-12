package com.ridecomp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Theme choice offered in settings (02-DESIGN §4.1). */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Root theme: provides RideComp tokens and a matching Material 3 color scheme. */
@Composable
fun RideCompTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (isDark) DarkRideCompColors else LightRideCompColors
    CompositionLocalProvider(
        LocalRideCompColors provides colors,
        LocalRideCompTypography provides RideCompTypography(),
    ) {
        MaterialTheme(colorScheme = colors.toMaterialColorScheme(), content = content)
    }
}

/** Short accessors: `RideCompTheme.colors.accent`, `RideCompTheme.typography.dataValue`. */
object RideCompTheme {
    val colors: RideCompColors
        @Composable @ReadOnlyComposable
        get() = LocalRideCompColors.current

    val typography: RideCompTypography
        @Composable @ReadOnlyComposable
        get() = LocalRideCompTypography.current
}

private fun RideCompColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = track,
        background = bg,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        error = danger,
        onError = onDanger,
        outline = divider,
        outlineVariant = divider,
    )
}
