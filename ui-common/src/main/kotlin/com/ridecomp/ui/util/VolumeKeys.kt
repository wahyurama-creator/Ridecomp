package com.ridecomp.ui.util

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Hardware volume keys used as ride controls. */
enum class VolumeKey { UP, DOWN }

/**
 * When [isEnabled], volume key presses call [onPress] once per press (held keys do not repeat) and do not
 * change the volume; otherwise the keys keep their normal behavior. The node takes focus to receive keys.
 */
@Composable
fun Modifier.volumeKeys(isEnabled: Boolean, onPress: (VolumeKey) -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    val currentOnPress = rememberUpdatedState(onPress)
    LaunchedEffect(isEnabled) { if (isEnabled) focusRequester.requestFocus() }
    return this
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            val key = when (event.key) {
                Key.VolumeUp -> VolumeKey.UP
                Key.VolumeDown -> VolumeKey.DOWN
                else -> null
            }
            if (!isEnabled || key == null) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                currentOnPress.value(key)
            }
            // Down and up are both consumed so the system volume does not change.
            true
        }
}
