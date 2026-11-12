package com.ridecomp.feature.ride.ui.component

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import com.ridecomp.core.model.ScreenMode
import kotlinx.coroutines.delay

/** Screen behaviour constants of the ride screen (02-DESIGN §6). */
object RideScreenConfig {
    /** Auto-dim kicks in after this long without a touch or an alert. */
    const val AUTO_DIM_DELAY_MS: Long = 15_000L

    /** Window brightness while dimmed (0..1). */
    const val DIM_BRIGHTNESS: Float = 0.05f
}

/**
 * Applies the ride screen mode (T1.11):
 * - ALWAYS_ON: the screen stays on;
 * - AUTO_DIM: stays on but dims after 15 s; [wakeKey] changes (touch, alert, lap) restore brightness;
 * - OFF: the screen may sleep normally; data is in the notification.
 *
 * `keepScreenOn` and the brightness override are removed when the ride screen leaves (resource table 09 §1,
 * BAT-08).
 */
@Composable
fun RideScreenModeEffect(mode: ScreenMode, wakeKey: Any?) {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(mode) {
        view.keepScreenOn = mode != ScreenMode.OFF
        onDispose {
            view.keepScreenOn = false
            window?.setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }
    LaunchedEffect(mode, wakeKey) {
        window?.setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        if (mode == ScreenMode.AUTO_DIM) {
            delay(RideScreenConfig.AUTO_DIM_DELAY_MS)
            window?.setBrightness(RideScreenConfig.DIM_BRIGHTNESS)
        }
    }
}

private fun android.view.Window.setBrightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}
