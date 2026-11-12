package com.ridecomp.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every size used by the UI, in one place (UI-05, 02-DESIGN §3–4). */
object RideCompDimens {
    // Spacing scale
    val spacingXs = 4.dp
    val spacingS = 8.dp
    val spacingM = 16.dp
    val spacingL = 24.dp
    val spacingXl = 32.dp

    // Touch targets (UI-07): usable with gloves
    val touchTargetMin = 56.dp
    val touchTargetPrimary = 72.dp
    val touchTargetIncident = 96.dp

    // Ride screen chrome (02-DESIGN §3.4)
    val rideTopBarHeight = 24.dp
    val rideBottomBarHeight = 64.dp
    val dividerThickness = 1.dp
    val iconSize = 24.dp
    val iconSizeLarge = 48.dp
    val cornerRadius = 8.dp
    val bannerMinHeight = 56.dp
    val contentMaxWidth = 600.dp
    val chartHeight = 140.dp

    /** Width / height of the data page preview in the editor (portrait phone data area). */
    const val DATA_PAGE_PREVIEW_ASPECT_RATIO = 3f / 4f
    val chartStroke = 2.dp

    // Data field typography (02-DESIGN §4.2)
    val dataValueMinSize = 32.sp
    val dataValueMaxSize = 120.sp
    val dataValueStep = 2.sp
    val dataLabelSize = 13.sp
    val dataLabelLetterSpacing = 0.5.sp
    val bodySize = 16.sp
    val titleSize = 22.sp
    val buttonTextSize = 20.sp
    val buttonTextSizeLarge = 24.sp
}
