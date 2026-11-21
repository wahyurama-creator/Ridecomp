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

    /** Moving time on the pause screen (T13.4): smaller so FINISH stays on a 320 × 569 dp screen. */
    val pauseTimeSize: TextUnit,

    /** Rings of the "waiting for GPS" indicator on the pre-ride check (T13.4). */
    val gpsWaitOuter: Dp,
    val gpsWaitInner: Dp,

    /** Incident countdown ring (T13.4); the cancel button keeps its 96 dp target. */
    val countdownRing: Dp,
) {
    companion object {
        /** 360 dp and wider: exactly the values of [RideCompDimens], so nothing changes there (R1). */
        val Regular = AdaptiveDimens(
            cardPaddingHorizontal = RideCompDimens.cardPaddingHorizontal,
            messageTitleSize = RideCompDimens.titleSize,
            pauseTimeSize = RideCompDimens.pauseTimeSize,
            gpsWaitOuter = RideCompDimens.gpsWaitOuter,
            gpsWaitInner = RideCompDimens.gpsWaitInner,
            countdownRing = RideCompDimens.countdownRing,
        )

        /** Below 360 dp. Text stays ≥ 12 sp and contrast is untouched (§4.4). */
        val Compact = AdaptiveDimens(
            cardPaddingHorizontal = 12.dp,
            messageTitleSize = 18.sp,
            pauseTimeSize = 48.sp,
            gpsWaitOuter = 152.dp,
            gpsWaitInner = 104.dp,
            countdownRing = 168.dp,
        )
    }
}
