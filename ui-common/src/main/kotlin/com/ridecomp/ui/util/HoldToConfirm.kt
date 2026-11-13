package com.ridecomp.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Calls [onConfirm] only when the element is held for [durationMs] without lifting the finger: for actions
 * that must never happen by accident (manual SOS, 12-FEATURE-SPECS §5.3).
 */
@Composable
fun Modifier.holdToConfirm(durationMs: Long, onConfirm: () -> Unit): Modifier {
    val currentOnConfirm = rememberUpdatedState(onConfirm)
    return pointerInput(durationMs) {
        awaitEachGesture {
            awaitFirstDown()
            val released = withTimeoutOrNull(durationMs) { waitForUpOrCancellation() }
            if (released == null) currentOnConfirm.value()
        }
    }
}
