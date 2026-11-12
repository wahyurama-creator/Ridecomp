package com.ridecomp.ui.udf

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Base class for every screen ViewModel, implementing Unidirectional Data Flow (ARCH-06):
 *
 * UI → [onEvent] → ViewModel calls UseCases → [updateState] → [uiState] → UI renders.
 * One-time effects (navigation, toast) go through [effects] (ARCH-07), never through state.
 *
 * @param S immutable UI state (`…UiState`), @param E UI events (`…UiEvent`), @param F one-time effects.
 */
abstract class UdfViewModel<S : Any, E : Any, F : Any>(initialState: S) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)

    /** Read-only state for the UI (CO-06). Collect with `collectAsStateWithLifecycle()` (CO-05). */
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _effects = Channel<F>(Channel.BUFFERED)

    /** One-time effects; each is delivered to a single collector exactly once. */
    val effects: Flow<F> = _effects.receiveAsFlow()

    /** The only entry point for UI input. */
    abstract fun onEvent(event: E)

    /** Latest state snapshot. */
    protected val currentState: S get() = _uiState.value

    /** Atomically derives a new state from the current one (CO-06). */
    protected fun updateState(reducer: S.() -> S) {
        _uiState.update(reducer)
    }

    /** Emits a one-time effect. Buffered, so it is not lost while the UI is in the background. */
    protected fun sendEffect(effect: F) {
        _effects.trySend(effect)
    }
}

/** Marker for screens without one-time effects. */
data object NoEffect
