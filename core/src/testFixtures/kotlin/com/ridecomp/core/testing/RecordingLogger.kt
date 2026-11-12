package com.ridecomp.core.testing

import com.ridecomp.core.source.Logger

/** [Logger] that keeps every entry in memory so tests can assert on logging (e.g. ERR-04 "dropped and logged"). */
public class RecordingLogger : Logger {
    public enum class Level { DEBUG, INFO, WARN, ERROR }

    public data class Entry(val level: Level, val tag: String, val message: String, val throwable: Throwable?)

    private val mutableEntries = mutableListOf<Entry>()
    public val entries: List<Entry> get() = mutableEntries.toList()

    override fun debug(tag: String, message: () -> String) {
        mutableEntries += Entry(Level.DEBUG, tag, message(), null)
    }

    override fun info(tag: String, message: () -> String) {
        mutableEntries += Entry(Level.INFO, tag, message(), null)
    }

    override fun warn(tag: String, throwable: Throwable?, message: () -> String) {
        mutableEntries += Entry(Level.WARN, tag, message(), throwable)
    }

    override fun error(tag: String, throwable: Throwable?, message: () -> String) {
        mutableEntries += Entry(Level.ERROR, tag, message(), throwable)
    }
}
