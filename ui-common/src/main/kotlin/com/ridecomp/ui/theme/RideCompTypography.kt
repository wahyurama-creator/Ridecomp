package com.ridecomp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Tabular figures so digits do not "jump" when values change (02-DESIGN §4.2). */
private const val TABULAR_NUMBERS = "tnum"

/**
 * Text styles of the design system (13-UI-SCREENS §1.3). System font only (02-DESIGN §4.2: no
 * downloaded fonts; the design's Inter is not bundled, D-54); every number style uses tabular figures.
 */
@Immutable
data class RideCompTypography(
    val dataValue: TextStyle = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = TABULAR_NUMBERS,
        fontSize = RideCompDimens.dataValueMaxSize,
    ),
    val dataLabel: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = RideCompDimens.dataLabelSize,
        letterSpacing = RideCompDimens.dataLabelLetterSpacing,
    ),
    val dataLabelHero: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = RideCompDimens.dataLabelHeroSize,
        letterSpacing = RideCompDimens.dataLabelLetterSpacing,
    ),
    /** Incident countdown, segment gap (96 sp, black weight). */
    val hugeNumber: TextStyle = TextStyle(
        fontWeight = FontWeight.Black,
        fontFeatureSettings = TABULAR_NUMBERS,
        fontSize = RideCompDimens.hugeNumberSize,
    ),
    /** Numbers inside cards (summary tiles, last ride). */
    val statValue: TextStyle = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = TABULAR_NUMBERS,
        fontSize = RideCompDimens.titleSize,
    ),
    /** Distance on the turn card (13-UI-SCREENS #16). */
    val turnDistance: TextStyle = TextStyle(
        fontWeight = FontWeight.Black,
        fontFeatureSettings = TABULAR_NUMBERS,
        fontSize = RideCompDimens.turnDistanceSize,
    ),
    /** Welcome headline (T10.12). */
    val display: TextStyle = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = RideCompDimens.displaySize,
        lineHeight = RideCompDimens.displayLineHeight,
    ),

    /** The speed on the welcome's sample ride card. */
    val welcomeSpeed: TextStyle = TextStyle(
        fontWeight = FontWeight.Black,
        fontFeatureSettings = TABULAR_NUMBERS,
        fontSize = RideCompDimens.welcomeSpeedSize,
    ),
    val screenTitle: TextStyle =
        TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = RideCompDimens.screenTitleSize),
    val title: TextStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = RideCompDimens.titleSize),
    val cardTitle: TextStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = RideCompDimens.cardTitleSize),
    val body: TextStyle = TextStyle(fontSize = RideCompDimens.bodySize, lineHeight = RideCompDimens.bodyLineHeight),
    val bodyStrong: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = RideCompDimens.bodySize,
        lineHeight = RideCompDimens.bodyLineHeight,
    ),
    val caption: TextStyle = TextStyle(fontSize = RideCompDimens.captionSize),
    val navLabel: TextStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = RideCompDimens.navLabelSize),
    val attribution: TextStyle = TextStyle(fontSize = RideCompDimens.attributionSize),
    val button: TextStyle = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = RideCompDimens.buttonTextSize,
        letterSpacing = RideCompDimens.buttonLetterSpacing,
    ),
    val buttonLarge: TextStyle = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = RideCompDimens.buttonTextSizeLarge,
        letterSpacing = RideCompDimens.buttonLetterSpacing,
    ),
    val buttonSmall: TextStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = RideCompDimens.buttonTextSizeSmall),
    /** Equal-width option chips (five per row on a 360 dp phone). */
    val chipCompact: TextStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = RideCompDimens.chipCompactTextSize),
)

val LocalRideCompTypography = staticCompositionLocalOf { RideCompTypography() }
