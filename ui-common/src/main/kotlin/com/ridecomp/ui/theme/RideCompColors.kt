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
    /** Yellow fills (auto-pause block, warning banners) with [onWarn] text; never text or icons on the page. */
    val warn: Color,
    val onWarn: Color,
    /** Warning text, icons and rings on the page: yellow on dark, deep amber on light. */
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
    /** Offline map palette (13-UI-SCREENS #7, #16): dark land, muted roads, so the route stands out. */
    val map: MapColors,
    val isDark: Boolean,
)

/** Colors of the offline map style; the route uses `accent`, the ridden track `track`. */
@Immutable
data class MapColors(
    val land: Color,
    val water: Color,
    val park: Color,
    val building: Color,
    val roadMinor: Color,
    val roadMajor: Color,
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
    val AccentLight = Color(0xFFB3390A)
    val Track = Color(0xFF2FA8FF)
    val TrackLight = Color(0xFF0B6BB8)
    val Ok = Color(0xFF3DDC84)
    val OkLight = Color(0xFF17683A)
    val Warn = Color(0xFFFFC400)
    val WarnTextLight = Color(0xFF755200)
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
    val MapLand = Color(0xFF0E0E10)
    val MapWater = Color(0xFF0B1A2A)
    val MapPark = Color(0xFF14201A)
    val MapBuilding = Color(0xFF17171C)
    val MapRoadMinor = Color(0xFF3A3A42)
    val MapRoadMajor = Color(0xFF5A5A63)
    val MapLandLight = Color(0xFFF2F2F2)
    val MapWaterLight = Color(0xFFB9D3EA)
    val MapParkLight = Color(0xFFD4E8D0)
    val MapBuildingLight = Color(0xFFE0E0E0)
    val MapRoadMinorLight = Color(0xFFFFFFFF)
    val MapRoadMajorLight = Color(0xFFBDBDBD)
    val GradeEasy = Color(0xFFFFF0C2)
    val GradeModerate = Color(0xFFFFE08A)
    val GradeHard = Color(0xFFFFB020)
    val GradeSteep = Color(0xFFF0672A)
    val GradeExtreme = Color(0xFFA8341A)

    // Light theme: the same hues, deep enough for 3:1 on white and light cards (T11.10, D-127).
    val ZoneGreyLight = Color(0xFF757575)
    val ZoneBlueLight = Color(0xFF1E78D2)
    val ZoneGreenLight = Color(0xFF2E8B3E)
    val ZoneYellowLight = Color(0xFFA87400)
    val ZoneOrangeLight = Color(0xFFD9530F)
    val ZoneRedLight = Color(0xFFC62828)
    val ZonePurpleLight = Color(0xFF8E44C9)
    val GradeEasyLight = Color(0xFF948200)
    val GradeModerateLight = Color(0xFFC27C00)
    val GradeHardLight = Color(0xFFD25E00)
    val GradeSteepLight = Color(0xFFC8431A)
    val GradeExtremeLight = Color(0xFF8C2410)
}

private val GradeBandColors = persistentListOf(
    Palette.GradeEasy,
    Palette.GradeModerate,
    Palette.GradeHard,
    Palette.GradeSteep,
    Palette.GradeExtreme,
)

private val LightGradeBandColors = persistentListOf(
    Palette.GradeEasyLight,
    Palette.GradeModerateLight,
    Palette.GradeHardLight,
    Palette.GradeSteepLight,
    Palette.GradeExtremeLight,
)

private val DarkMapColors = MapColors(
    land = Palette.MapLand,
    water = Palette.MapWater,
    park = Palette.MapPark,
    building = Palette.MapBuilding,
    roadMinor = Palette.MapRoadMinor,
    roadMajor = Palette.MapRoadMajor,
)

private val LightMapColors = MapColors(
    land = Palette.MapLandLight,
    water = Palette.MapWaterLight,
    park = Palette.MapParkLight,
    building = Palette.MapBuildingLight,
    roadMinor = Palette.MapRoadMinorLight,
    roadMajor = Palette.MapRoadMajorLight,
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

private val LightZoneColors = persistentListOf(
    Palette.ZoneGreyLight,
    Palette.ZoneBlueLight,
    Palette.ZoneGreenLight,
    Palette.ZoneYellowLight,
    Palette.ZoneOrangeLight,
    Palette.ZoneRedLight,
    Palette.ZonePurpleLight,
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
    map = DarkMapColors,
    isDark = true,
)

/**
 * Light theme for LCD screens in strong sunlight (T11.9): text ≥ 4.5:1 on every surface (≥ 7:1 for the
 * ride data), lines, bars and icons ≥ 3:1; checked by `ThemeContrastTest` (T11.10, D-127).
 */
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
    track = Palette.TrackLight,
    onTrack = Palette.White,
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
    zones = LightZoneColors,
    gradeBands = LightGradeBandColors,
    map = LightMapColors,
    isDark = false,
)

val LocalRideCompColors = staticCompositionLocalOf { DarkRideCompColors }

/**
 * Colours of share card images (T4.5b), not of the app screens: poster gradients, the dark checkerboard shown
 * behind transparent cards in the preview only, and the shadow that keeps sticker text readable on any photo.
 */
object ShareCardColors {
    val SunsetTop = Color(0xFFFF6A13)
    val SunsetBottom = Color(0xFF6A1B9A)
    val OceanTop = Color(0xFF00B4DB)
    val OceanBottom = Color(0xFF0D3B66)
    val ForestTop = Color(0xFF2ECC71)
    val ForestBottom = Color(0xFF0B3D2E)
    val NightTop = Color(0xFF3A3D40)
    val NightBottom = Color(0xFF0B0F14)
    val PosterText = Color(0xFFFFFFFF)
    val PosterSoftText = Color(0xCCFFFFFF)
    val PosterTrack = Color(0x33FFFFFF)
    val PhotoScrim = Color(0x99000000)
    val TextShadow = Color(0xB3000000)
    val CheckerDark = Color(0xFF1E1E1E)
    val CheckerLight = Color(0xFF2E2E2E)
}

/** Partner brand colors, used only next to the partner's name (Strava brand guidelines, D-82). */
object BrandColors {
    val StravaOrange = Color(0xFFFC5200)
    val OnStravaOrange = Color(0xFFFFFFFF)
}
