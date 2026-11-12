package com.ridecomp.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutcomeTest {
    private val success: Outcome<Int, String> = 2.asSuccess()
    private val failure: Outcome<Int, String> = "broken".asFailure()

    @Test
    fun `map transforms success only`() {
        assertEquals(Outcome.Success(4), success.map { it * 2 })
        assertEquals<Outcome<Int, String>>(failure, failure.map { it * 2 })
    }

    @Test
    fun `flatMap chains success and short-circuits failure`() {
        assertEquals(Outcome.Success("2"), success.flatMap { it.toString().asSuccess() })
        assertEquals<Outcome<String, String>>(Outcome.Failure("broken"), failure.flatMap { it.toString().asSuccess() })
    }

    @Test
    fun `mapError transforms failure only`() {
        assertEquals(Outcome.Failure(6), failure.mapError { it.length })
        assertEquals<Outcome<Int, Int>>(Outcome.Success(2), success.mapError { it.length })
    }

    @Test
    fun `accessors expose the right branch`() {
        assertTrue(success.isSuccess)
        assertFalse(failure.isSuccess)
        assertEquals(2, success.getOrNull())
        assertNull(failure.getOrNull())
        assertEquals("broken", failure.errorOrNull())
        assertNull(success.errorOrNull())
    }

    @Test
    fun `fold reduces both branches`() {
        assertEquals("ok 2", success.fold({ "ok $it" }, { "err $it" }))
        assertEquals("err broken", failure.fold({ "ok $it" }, { "err $it" }))
    }

    @Test
    fun `callbacks run only for their branch`() {
        val seen = mutableListOf<String>()

        success.onSuccess { seen += "s$it" }.onFailure { seen += "f$it" }
        failure.onSuccess { seen += "s$it" }.onFailure { seen += "f$it" }

        assertEquals(listOf("s2", "fbroken"), seen)
    }
}
