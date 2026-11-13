package com.ridecomp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Design tokens from 02-DESIGN §4.1 and 13-UI-SCREENS §1.2. Composables read colors only from here (UI-05). */
@Immutable
data class RideCompColors(
    val bg: Color,
    val surface: Color,
    /** Bottom navigation and map background. */
    val surfaceAlt: Color,
    /** Small buttons placed on a [surface] card (e.g. remove route). */
    val surfaceRaised: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Paragraphs and unselected chips. */
    val textTertiary: Color,
    /** Inactive values (speed 0.0 while paused, `--`). */
    val disabled: Color,
    /** Inactive page indicator dots. */
    val indicatorInactive: Color,
    val accent: Color,
    val onAccent: Color,
    /** Background of a selected chip or option (with a 2 dp [accent] border). */
    val accentTint: Color,
    val track: Color,
    /** Info banners (drink reminder) use [track] with this text color. */
    val onTrack: Color,
    val ok: Color,
    val onOk: Color,
    /** Background of positive info cards (map available offline). */
    val okTint: Color,
    val warn: Color,
    val onWarn: Color,
    val warnText: Color,
    /** Background of warning cards (1 dp [warn] border). */
    val warnTint: Color,
    val danger: Color,
    val onDanger: Color,
    /** Text on a [danger] banner: dark on the bright dark-theme red, white on the deep light-theme red. */
    val onDangerBanner: Color,
    /** Full-screen incident countdown background, with [onDanger] text. */
    val dangerDeep: Color,
    /** Zone colors 1..7 (HR uses the first 5, power all 7): grey, blue, green, yellow, orange, red, purple. */
    val zones: ImmutableList<Color>,
    /**
     * Climb grade bands 0–3, 3–6, 6–9, 9–12, > 12 % (12-FEATURE-SPECS §6.2), colours from 13-UI-SCREENS §1.2
     * (the design merges 0–6 %; 0–3 % gets a lighter shade, D-54): brightness falls as grade rises.
     */
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
    val SurfaceAlt = Color(0xFF0A0A0A)
    val SurfaceAltLight = Color(0xFFEAEAEA)
    val SurfaceRaised = Color(0xFF1E1E1E)
    val SurfaceRaisedLight = Color(0xFFDDDDDD)
    val TextTertiary = Color(0xFFC8C8C8)
    val TextTertiaryLight = Color(0xFF2E2E2E)
    val Disabled = Color(0xFF6E6E6E)
    val DisabledLight = Color(0xFF8A8A8A)
    val IndicatorInactive = Color(0xFF4A4A4A)
    val AccentTint = Color(0xFF1F1208)
    val AccentTintLight = Color(0xFFFDE8DC)
    val OkTint = Color(0xFF0B1F13)
    val OkTintLight = Color(0xFFE3F5EA)
    val WarnTint = Color(0xFF1A1500)
    val WarnTintLight = Color(0xFFFFF5CC)
    val DangerDeep = Color(0xFF7A0E0E)
    val GradeEasy = Color(0xFFFFF0C2)
    val GradeModerate = Color(0xFFFFE08A)
    val GradeHard = Color(0xFFFFB020)
    val GradeSteep = Color(0xFFF0672A)
    val GradeExtreme = Color(0xFFA8341A)
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
    surfaceAlt = Palette.SurfaceAlt,
    surfaceRaised = Palette.SurfaceRaised,
    divider = Palette.Divider,
    textPrimary = Palette.White,
    textSecondary = Palette.TextSecondary,
    textTertiary = Palette.TextTertiary,
    disabled = Palette.Disabled,
    indicatorInactive = Palette.IndicatorInactive,
    accent = Palette.Accent,
    onAccent = Palette.Black,
    accentTint = Palette.AccentTint,
    track = Palette.Track,
    onTrack = Palette.Black,
    ok = Palette.Ok,
    onOk = Palette.Black,
    okTint = Palette.OkTint,
    warn = Palette.Warn,
    onWarn = Palette.Black,
    warnText = Palette.Warn,
    warnTint = Palette.WarnTint,
    danger = Palette.Danger,
    onDanger = Palette.White,
    onDangerBanner = Palette.Black,
    dangerDeep = Palette.DangerDeep,
    zones = ZoneColors,
    gradeBands = GradeBandColors,
    isDark = true,
)

/** Optional high-contrast light theme (≥ 7:1) for LCD screens in strong sunlight. */
val LightRideCompColors = RideCompColors(
    bg = Palette.White,
    surface = Palette.SurfaceLight,
    surfaceAlt = Palette.SurfaceAltLight,
    surfaceRaised = Palette.SurfaceRaisedLight,
    divider = Palette.DividerLight,
    textPrimary = Palette.Black,
    textSecondary = Palette.TextSecondaryLight,
    textTertiary = Palette.TextTertiaryLight,
    disabled = Palette.DisabledLight,
    indicatorInactive = Palette.DividerLight,
    accent = Palette.AccentLight,
    onAccent = Palette.White,
    accentTint = Palette.AccentTintLight,
    track = Palette.Track,
    onTrack = Palette.Black,
    ok = Palette.OkLight,
    onOk = Palette.White,
    okTint = Palette.OkTintLight,
    warn = Palette.Warn,
    onWarn = Palette.Black,
    warnText = Palette.WarnTextLight,
    warnTint = Palette.WarnTintLight,
    danger = Palette.DangerLight,
    onDanger = Palette.White,
    onDangerBanner = Palette.White,
    dangerDeep = Palette.DangerDeep,
    zones = ZoneColors,
    gradeBands = GradeBandColors,
    isDark = false,
)

val LocalRideCompColors = staticCompositionLocalOf { DarkRideCompColors }
