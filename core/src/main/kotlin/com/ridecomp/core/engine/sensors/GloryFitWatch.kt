package com.ridecomp.core.engine.sensors

import com.ridecomp.core.config.Units

/**
 * Watches paired with the GloryFit app, such as the Aolon GTR 3 (T12.5b, experimental): they share heart rate
 * only through their own protocol (04-INTEGRATIONS §A.5a, D-135). Learnt clean-room from the owner's watch;
 * a firmware update may change it.
 */
public object GloryFitConfig {
    /** The watch notifies only when the value changes (longest gap seen 19.8 s): the last value is held this long. */
    public const val HEART_RATE_HOLD_MS: Long = 30_000L

    /** The held value is repeated and the workout clock sent this often, like the GloryFit app. */
    public const val TICK_INTERVAL_MS: Long = 1_000L

    /** Stopping the workout when the ride ends may take this long before the link is closed anyway. */
    public const val STOP_TIMEOUT_MS: Long = 2_000L

    /** Plausible heart rate from the watch (15-PHASE12-PLAN T12.5b); the first packet is 0 while it warms up. */
    public const val MIN_HEART_RATE_BPM: Int = 30
    public const val MAX_HEART_RATE_BPM: Int = 230
}

/** Commands written to the watch's command characteristic (0x33F1). */
public object GloryFitCommands {
    private const val PREFIX = 0xFD.toByte()
    private const val OP_STOP: Byte = 0x00
    private const val OP_START: Byte = 0x11
    private const val OP_CLOCK: Byte = 0x44
    private const val WORKOUT: Byte = 0x02
    private const val ACTION: Byte = 0x01

    /** Bytes after hours, minutes and seconds in the workout clock (distance and energy, unused). */
    private const val CLOCK_PADDING = 6

    /** Starts a workout: the watch then measures heart rate continuously and notifies it. */
    public val START_WORKOUT: ByteArray = byteArrayOf(PREFIX, OP_START, WORKOUT, ACTION)

    /** Ends the workout: the watch stops measuring (saves its battery). */
    public val STOP_WORKOUT: ByteArray = byteArrayOf(PREFIX, OP_STOP, WORKOUT, ACTION)

    /** The workout clock the GloryFit app sends every second: `FD 44 02 01 hh mm ss` and zeros. */
    public fun workoutClock(elapsedMs: Long): ByteArray {
        val totalS = (elapsedMs / Units.MS_PER_SECOND).coerceAtLeast(0)
        val hours = totalS / Units.SECONDS_PER_HOUR
        val minutes = totalS % Units.SECONDS_PER_HOUR / Units.SECONDS_PER_MINUTE
        val seconds = totalS % Units.SECONDS_PER_MINUTE
        return byteArrayOf(
            PREFIX,
            OP_CLOCK,
            WORKOUT,
            ACTION,
            hours.coerceAtMost(Byte.MAX_VALUE.toLong()).toByte(),
            minutes.toByte(),
            seconds.toByte(),
        ) + ByteArray(CLOCK_PADDING)
    }
}

/** Heart rate notifications of the data characteristic (0x33F2): `FD 02 <bpm>` and zeros. */
public object GloryFitParser {
    private const val PREFIX = 0xFD
    private const val OP_HEART_RATE = 0x02

    /** Other notifications (echoes, history, summaries) and implausible values give null (SEC-IN-03). */
    public fun parseHeartRate(bytes: ByteArray): HeartRateMeasurement? {
        val reader = ByteReader(bytes)
        val isHeartRate = reader.uint8() == PREFIX && reader.uint8() == OP_HEART_RATE
        return reader.uint8()
            ?.takeIf { isHeartRate && it in GloryFitConfig.MIN_HEART_RATE_BPM..GloryFitConfig.MAX_HEART_RATE_BPM }
            ?.let { HeartRateMeasurement(it, emptyList()) }
    }
}

/**
 * The watch's last heart rate: it notifies only changes, while a ride sample is stale after 3 s, so the value
 * is repeated until [holdMs] after the last notification (then the watch is treated as silent).
 */
public class HeldHeartRate(private val holdMs: Long = GloryFitConfig.HEART_RATE_HOLD_MS) {
    private var last: HeartRateMeasurement? = null
    private var lastAtMs = 0L

    public fun update(measurement: HeartRateMeasurement, nowMs: Long) {
        last = measurement
        lastAtMs = nowMs
    }

    /** The held value, or null when nothing came in for longer than [holdMs]. */
    public fun current(nowMs: Long): HeartRateMeasurement? = last?.takeIf { nowMs - lastAtMs <= holdMs }
}
