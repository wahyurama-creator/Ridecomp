package com.ridecomp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Tabular figures so digits do not "jump" when values change (02-DESIGN §4.2). */
private const val TABULAR_NUMBERS = "tnum"

/** Text styles of the design system. System font only (no downloaded fonts). */
@Immutable
data class RideCompTypography(
    val dataValue: TextStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_NUMBERS,
        fontSize = RideCompDimens.dataValueMaxSize,
    ),
    val dataLabel: TextStyle = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = RideCompDimens.dataLabelSize,
        letterSpacing = RideCompDimens.dataLabelLetterSpacing,
    ),
    val body: TextStyle = TextStyle(fontSize = RideCompDimens.bodySize),
    val title: TextStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = RideCompDimens.titleSize),
    val button: TextStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = RideCompDimens.buttonTextSize),
    val buttonLarge: TextStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = RideCompDimens.buttonTextSizeLarge,
    ),
)

val LocalRideCompTypography = staticCompositionLocalOf { RideCompTypography() }
