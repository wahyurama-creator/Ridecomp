package com.ridecomp.ui.util

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps Android autofill away from this screen while it is shown (SEC-STO-02): a password manager would
 * offer to save an API secret to its cloud. The window's previous setting comes back on leaving.
 */
@Composable
fun ExcludeFromAutofill() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.importantForAutofill
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { view.importantForAutofill = previous }
    }
}
