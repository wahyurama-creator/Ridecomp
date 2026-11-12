package com.ridecomp.ui.util

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import com.ridecomp.ui.theme.RideCompDimens

/** Applies [block] only when [condition] is true, keeping modifier chains readable. */
inline fun Modifier.thenIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier =
    if (condition) this.block() else this

/** Guarantees the glove-friendly minimum touch target (UI-07). */
fun Modifier.minTouchTarget(): Modifier =
    defaultMinSize(minWidth = RideCompDimens.touchTargetMin, minHeight = RideCompDimens.touchTargetMin)

/** Guarantees the primary-action touch target (≥ 72 dp, UI-07). */
fun Modifier.primaryTouchTarget(): Modifier =
    defaultMinSize(minWidth = RideCompDimens.touchTargetPrimary, minHeight = RideCompDimens.touchTargetPrimary)
