package com.ridecomp.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every size used by the UI, in one place (UI-05, 02-DESIGN §3–4, 13-UI-SCREENS §1). */
object RideCompDimens {
    // Spacing scale
    val none = 0.dp
    val spacingXs = 4.dp
    val spacingS = 8.dp
    val spacingSm = 12.dp
    val spacingM = 16.dp
    val spacingL = 24.dp
    val spacingXl = 32.dp

    // Screen layout (13-UI-SCREENS §1.1)
    val screenPaddingHorizontal = 20.dp
    val screenPaddingTop = 24.dp
    val screenPaddingBottom = 24.dp
    val blockGap = 16.dp
    val cardGap = 10.dp
    val cardPaddingHorizontal = 16.dp
    val cardPaddingVertical = 14.dp

    // Touch targets (UI-07): usable with gloves. UI-07 (56 dp) wins over the design's 44 dp icon buttons (D-54).
    val touchTargetMin = 56.dp
    val touchTargetPrimary = 72.dp
    val touchTargetIncident = 96.dp

    // Button heights (13-UI-SCREENS §1.4)
    val buttonHeightHero = 88.dp
    val buttonHeightPrimary = 72.dp
    val buttonHeightSecondary = 64.dp
    val buttonHeightTertiary = 56.dp
    val buttonHeightIncident = 120.dp
    val lapButtonWidth = 120.dp
    val chipHeight = 48.dp

    // Corners
    val cornerRadius = 8.dp
    val cornerRadiusSmall = 12.dp
    val cornerRadiusCard = 16.dp
    val cornerRadiusButton = 18.dp
    val cornerRadiusHero = 22.dp

    // Borders
    val borderThin = 1.dp
    val borderSelected = 2.dp

    // Ride screen chrome (02-DESIGN §3.4, 13-UI-SCREENS §2)
    val rideTopBarHeight = 40.dp
    val statusIconSize = 16.dp
    val recDotSize = 8.dp
    val pageDotSize = 6.dp
    val pageDotActiveWidth = 20.dp
    val rideBottomBarHeight = 64.dp
    val dividerThickness = 1.dp
    val dividerThicknessLight = 2.dp
    val iconSize = 24.dp
    val iconSizeLarge = 48.dp
    val bannerMinHeight = 56.dp
    val bannerIconSize = 32.dp
    val contentMaxWidth = 600.dp
    val chartHeight = 140.dp
    val onboardingProgressHeight = 4.dp
    val gpsWaitOuter = 200.dp
    val gpsWaitInner = 140.dp

    // Toggle (13-UI-SCREENS §1.4): 56 × 34
    val toggleWidth = 56.dp
    val toggleHeight = 34.dp
    val toggleKnobSize = 26.dp
    val toggleKnobInset = 4.dp

    /** Width / height of the data page preview in the editor (portrait phone data area). */
    const val DATA_PAGE_PREVIEW_ASPECT_RATIO = 3f / 4f
    val chartStroke = 2.dp

    // Data field typography (02-DESIGN §4.2)
    val dataValueMinSize = 32.sp
    val dataValueMaxSize = 120.sp
    val dataValueStep = 2.sp
    val dataLabelSize = 12.sp
    val dataLabelHeroSize = 14.sp
    val dataLabelLetterSpacing = 0.5.sp

    // Text sizes (13-UI-SCREENS §1.3)
    val hugeNumberSize = 96.sp
    val pauseTimeSize = 64.sp
    val screenTitleSize = 26.sp
    val titleSize = 22.sp
    val cardTitleSize = 16.sp
    val bodySize = 15.sp
    val bodyLineHeight = 22.sp
    val captionSize = 12.sp
    val navLabelSize = 11.sp
    val attributionSize = 10.sp
    val buttonTextSize = 20.sp
    val buttonTextSizeLarge = 24.sp
    val buttonTextSizeSmall = 15.sp
    val chipCompactTextSize = 13.sp
    val buttonLetterSpacing = 1.sp
}
