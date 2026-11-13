package com.ridecomp.platform.crash

import com.ridecomp.core.testing.FakeClock
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CrashLogStoreTest {
    private val directory: File = Files.createTempDirectory("crash").toFile()
    private val clock = FakeClock()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `crash is written with thread and stack trace`() {
        val store = CrashLogStore({ directory }, clock)

        val file = store.write("main", IllegalStateException("boom"))

        val text = file.readText()
        assertTrue(text.contains("thread=main"))
        assertTrue(text.contains("IllegalStateException: boom"))
    }

    @Test
    fun `only the newest files are kept`() {
        val store = CrashLogStore({ directory }, clock, maxFiles = 3)

        repeat(5) {
            store.write("main", RuntimeException("crash $it"))
            clock.advanceBy(1_000)
        }

        val logs = store.list()
        assertEquals(3, logs.size)
        assertTrue(logs.first().readText().contains("crash 4"))
    }

    @Test
    fun `coordinates are removed from the report`() {
        val sanitized = CrashLogStore.sanitize("at -7.7956123, 110.3694567 (accuracy 5.0 m)")

        assertFalse(sanitized.contains("7.7956123"))
        assertFalse(sanitized.contains("110.3694567"))
        assertTrue(sanitized.contains("accuracy 5.0 m"))
    }

    @Test
    fun `clear deletes all logs`() {
        val store = CrashLogStore({ directory }, clock)
        store.write("main", RuntimeException())

        store.clear()

        assertEquals(emptyList(), store.list())
    }

    @Test
    fun `handler stores the crash and delegates to the previous handler`() {
        val store = CrashLogStore({ directory }, clock)
        var delegated: Throwable? = null
        val error = RuntimeException("fatal")
        val handler = CrashLogHandler(store) { _, throwable -> delegated = throwable }

        handler.uncaughtException(Thread.currentThread(), error)

        assertSame(error, delegated)
        assertEquals(1, store.list().size)
    }
}
