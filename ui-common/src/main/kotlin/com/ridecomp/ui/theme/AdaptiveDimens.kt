package com.ridecomp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sizes that change on small screens (Phase 13, D-143), read through `RideCompTheme.dimens`. Every other
 * size stays in [RideCompDimens]; touch targets never shrink (UI-07, 16-PHASE13-PLAN §4.4), so they are not here.
 * A token is added by the task that needs it.
 */
@Immutable
data class AdaptiveDimens(
    /** Side padding inside cards (`RideCompCard`, tips). */
    val cardPaddingHorizontal: Dp,

    /** Sentence of an empty or error state (`MessageState`). */
    val messageTitleSize: TextUnit,
) {
    companion object {
        /** 360 dp and wider: exactly the values of [RideCompDimens], so nothing changes there (R1). */
        val Regular = AdaptiveDimens(
            cardPaddingHorizontal = RideCompDimens.cardPaddingHorizontal,
            messageTitleSize = RideCompDimens.titleSize,
        )

        /** Below 360 dp. Text stays ≥ 12 sp and contrast is untouched (§4.4). */
        val Compact = AdaptiveDimens(
            cardPaddingHorizontal = 12.dp,
            messageTitleSize = 18.sp,
        )
    }
}
