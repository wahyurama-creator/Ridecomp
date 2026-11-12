package com.ridecomp.ui.udf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects one-time [effects] only while the screen is at least STARTED (CO-05), so effects are not
 * handled while the app is in the background and nothing leaks after the screen leaves composition.
 */
@Composable
fun <F> ObserveEffects(effects: Flow<F>, onEffect: (F) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect = rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { currentOnEffect.value(it) }
        }
    }
}
