package com.ridecomp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Design tokens from 02-DESIGN §4.1. Composables read colors only from here (UI-05). */
@Immutable
data class RideCompColors(
    val bg: Color,
    val surface: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val track: Color,
    val ok: Color,
    val warn: Color,
    val onWarn: Color,
    val warnText: Color,
    val danger: Color,
    val onDanger: Color,
    /** Zone colors 1..7 (HR uses the first 5, power all 7): grey, blue, green, yellow, orange, red, purple. */
    val zones: ImmutableList<Color>,
    /** Climb grade bands 0–3, 3–6, 6–9, 9–12, > 12 % (12-FEATURE-SPECS §6.2): brightness falls as grade rises. */
    val gradeBands: ImmutableList<Color>,
    val isDark: Boolean,
)

/** Raw palette values, kept in one place. */
private object Palette {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Surface = Color(0xFF121212)
    val SurfaceLight = Color(0xFFF2F2F2)
    val Divider = Color(0xFF2A2A2A)
    val DividerLight = Color(0xFFBDBDBD)
    val TextSecondary = Color(0xFFA0A0A0)
    val TextSecondaryLight = Color(0xFF4A4A4A)
    val Accent = Color(0xFFFC6A1E)
    val AccentLight = Color(0xFFC2410C)
    val Track = Color(0xFF2FA8FF)
    val Ok = Color(0xFF3DDC84)
    val OkLight = Color(0xFF1B7A3E)
    val Warn = Color(0xFFFFC400)
    val WarnTextLight = Color(0xFF8A6100)
    val Danger = Color(0xFFFF4D4D)
    val DangerLight = Color(0xFFB3261E)
    val ZoneGrey = Color(0xFF9E9E9E)
    val ZonePurple = Color(0xFFB388FF)
    val GradeEasy = Color(0xFFA5E8B8)
    val GradeModerate = Color(0xFFFFD54F)
    val GradeHard = Color(0xFFFF8F00)
    val GradeSteep = Color(0xFFD32F2F)
    val GradeExtreme = Color(0xFF7B0A0A)
}

private val GradeBandColors = persistentListOf(
    Palette.GradeEasy,
    Palette.GradeModerate,
    Palette.GradeHard,
    Palette.GradeSteep,
    Palette.GradeExtreme,
)

private val ZoneColors = persistentListOf(
    Palette.ZoneGrey,
    Palette.Track,
    Palette.Ok,
    Palette.Warn,
    Palette.Accent,
    Palette.Danger,
    Palette.ZonePurple,
)

/** Default theme: pure black saves battery on AMOLED and reduces glare (02-DESIGN §1). */
val DarkRideCompColors = RideCompColors(
    bg = Palette.Black,
    surface = Palette.Surface,
    divider = Palette.Divider,
    textPrimary = Palette.White,
    textSecondary = Palette.TextSecondary,
    accent = Palette.Accent,
    onAccent = Palette.Black,
    track = Palette.Track,
    ok = Palette.Ok,
    warn = Palette.Warn,
    onWarn = Palette.Black,
    warnText = Palette.Warn,
    danger = Palette.Danger,
    onDanger = Palette.White,
    zones = ZoneColors,
    gradeBands = GradeBandColors,
    isDark = true,
)

/** Optional high-contrast light theme (≥ 7:1) for LCD screens in strong sunlight. */
val LightRideCompColors = RideCompColors(
    bg = Palette.White,
    surface = Palette.SurfaceLight,
    divider = Palette.DividerLight,
    textPrimary = Palette.Black,
    textSecondary = Palette.TextSecondaryLight,
    accent = Palette.AccentLight,
    onAccent = Palette.White,
    track = Palette.Track,
    ok = Palette.OkLight,
    warn = Palette.Warn,
    onWarn = Palette.Black,
    warnText = Palette.WarnTextLight,
    danger = Palette.DangerLight,
    onDanger = Palette.White,
    zones = ZoneColors,
    gradeBands = GradeBandColors,
    isDark = false,
)

val LocalRideCompColors = staticCompositionLocalOf { DarkRideCompColors }
