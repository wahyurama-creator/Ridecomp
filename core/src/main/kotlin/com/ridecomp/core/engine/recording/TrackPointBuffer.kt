package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.RecordingConfig
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.util.RingBuffer

/**
 * In-memory queue between the 1 Hz pipeline and the database (03-ARCHITECTURE §4.5): flushed every 5 s
 * or when [RecordingConfig.MAX_BUFFERED_POINTS] are waiting. If writes keep failing (STB-08) points stay
 * queued up to [RecordingConfig.MAX_PENDING_POINTS]; older ones are then dropped and counted in
 * [droppedCount], keeping memory bounded (MEM-05).
 */
public class TrackPointBuffer(
    private val flushIntervalMs: Long = RecordingConfig.FLUSH_INTERVAL_MS,
    private val flushSize: Int = RecordingConfig.MAX_BUFFERED_POINTS,
    capacity: Int = RecordingConfig.MAX_PENDING_POINTS,
) {
    private val pending = RingBuffer<RideSample>(capacity)
    private var lastFlushMs: Long? = null

    /** Points lost because the database was unavailable for too long. */
    public var droppedCount: Int = 0
        private set

    public val size: Int get() = pending.size

    public fun add(sample: RideSample) {
        if (pending.add(sample) != null) droppedCount++
    }

    /** True when a flush is due at [nowMs]. */
    public fun isFlushDue(nowMs: Long): Boolean {
        // The flush clock starts with the first check, so the first write happens one interval later.
        val last = lastFlushMs ?: nowMs.also { lastFlushMs = it }
        return !pending.isEmpty && (nowMs - last >= flushIntervalMs || pending.size >= flushSize)
    }

    /** Points to write now (oldest first). Call [onFlushed] after a successful write. */
    public fun peekAll(): List<RideSample> = pending.toList()

    /** Removes the written points. On a failed write simply do not call this: points are retried. */
    public fun onFlushed(writtenCount: Int, nowMs: Long) {
        val remaining = pending.toList().drop(writtenCount)
        pending.clear()
        remaining.forEach(pending::add)
        lastFlushMs = nowMs
    }
}
