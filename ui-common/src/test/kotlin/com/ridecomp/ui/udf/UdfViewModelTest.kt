package com.ridecomp.ui.udf

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class UdfViewModelTest {
    private data class CounterState(val count: Int = 0)

    private sealed interface CounterEvent {
        data object Increment : CounterEvent

        data object Finish : CounterEvent
    }

    private sealed interface CounterEffect {
        data class Done(val finalCount: Int) : CounterEffect
    }

    private class CounterViewModel : UdfViewModel<CounterState, CounterEvent, CounterEffect>(CounterState()) {
        override fun onEvent(event: CounterEvent) {
            when (event) {
                CounterEvent.Increment -> updateState { copy(count = count + 1) }
                CounterEvent.Finish -> sendEffect(CounterEffect.Done(currentState.count))
            }
        }
    }

    @Test
    fun `initial state is exposed`() {
        assertEquals(CounterState(0), CounterViewModel().uiState.value)
    }

    @Test
    fun `events reduce state`() {
        val viewModel = CounterViewModel()

        viewModel.onEvent(CounterEvent.Increment)
        viewModel.onEvent(CounterEvent.Increment)

        assertEquals(CounterState(2), viewModel.uiState.value)
    }

    @Test
    fun `effects are delivered once and buffered until collected`() = runTest {
        val viewModel = CounterViewModel()
        viewModel.onEvent(CounterEvent.Increment)

        viewModel.onEvent(CounterEvent.Finish)

        viewModel.effects.test {
            assertEquals(CounterEffect.Done(1), awaitItem())
            expectNoEvents()
        }
    }
}
