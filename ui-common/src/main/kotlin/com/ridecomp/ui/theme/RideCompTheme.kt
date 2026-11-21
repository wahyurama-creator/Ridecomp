package com.ridecomp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/** Theme choice offered in settings (02-DESIGN §4.1). */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/**
 * Root theme: provides RideComp tokens and a matching Material 3 color scheme. The width class comes from the
 * window width (Phase 13); images that must look the same on every phone (share cards) pass
 * [WindowWidthClass.REGULAR].
 */
@Composable
fun RideCompTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    widthClass: WindowWidthClass = WindowWidthClass.fromWidthDp(LocalConfiguration.current.screenWidthDp),
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (isDark) DarkRideCompColors else LightRideCompColors
    CompositionLocalProvider(
        LocalRideCompColors provides colors,
        LocalRideCompTypography provides RideCompTypography(),
        LocalWindowWidthClass provides widthClass,
    ) {
        MaterialTheme(colorScheme = colors.toMaterialColorScheme(), content = content)
    }
}

/** Short accessors: `RideCompTheme.colors.accent`, `RideCompTheme.typography.dataValue`, `RideCompTheme.dimens`. */
object RideCompTheme {
    val colors: RideCompColors
        @Composable @ReadOnlyComposable
        get() = LocalRideCompColors.current

    val typography: RideCompTypography
        @Composable @ReadOnlyComposable
        get() = LocalRideCompTypography.current

    /** COMPACT below 360 dp, else REGULAR (16-PHASE13-PLAN §4.1). */
    val widthClass: WindowWidthClass
        @Composable @ReadOnlyComposable
        get() = LocalWindowWidthClass.current

    /** The sizes that follow [widthClass]: `RideCompTheme.dimens.cardPaddingHorizontal`. */
    val dimens: AdaptiveDimens
        @Composable @ReadOnlyComposable
        get() = LocalWindowWidthClass.current.dimens
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
