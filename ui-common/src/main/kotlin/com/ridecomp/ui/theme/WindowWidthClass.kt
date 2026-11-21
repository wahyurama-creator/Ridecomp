package com.ridecomp.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Width class of the window (16-PHASE13-PLAN §4.1): computed once in [RideCompTheme], changes only with the
 * configuration. Screens read it through [RideCompTheme.widthClass] instead of measuring the screen.
 */
enum class WindowWidthClass {
    /** Narrower than [COMPACT_MAX_WIDTH_DP]: 320 dp phones get [AdaptiveDimens.Compact]. */
    COMPACT,

    /** 360 dp and wider: the original sizes, unchanged (R1, D-124). */
    REGULAR,
    ;

    val dimens: AdaptiveDimens
        get() = when (this) {
            COMPACT -> AdaptiveDimens.Compact
            REGULAR -> AdaptiveDimens.Regular
        }

    companion object {
        /** The compact sizes apply below this width (R1). */
        const val COMPACT_MAX_WIDTH_DP = 360

        fun fromWidthDp(widthDp: Int): WindowWidthClass = if (widthDp < COMPACT_MAX_WIDTH_DP) COMPACT else REGULAR
    }
}

/** Regular by default: previews without a width, share images and anything outside [RideCompTheme]. */
val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.REGULAR }
