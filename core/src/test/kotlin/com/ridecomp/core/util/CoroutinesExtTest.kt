package com.ridecomp.core.util

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoroutinesExtTest {
    @Test
    fun `success is wrapped`() {
        assertEquals(Result.success(1), runCatchingCancellable { 1 })
    }

    @Test
    fun `failure is wrapped`() {
        val result = runCatchingCancellable { error("boom") }

        assertTrue(result.isFailure)
    }

    @Test
    fun `cancellation is rethrown, never swallowed`() {
        assertFailsWith<CancellationException> {
            runCatchingCancellable { throw CancellationException("cancelled") }
        }
    }
}
