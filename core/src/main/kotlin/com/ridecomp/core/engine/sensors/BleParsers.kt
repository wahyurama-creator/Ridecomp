package com.ridecomp.core.engine.sensors

import com.ridecomp.core.config.Units

/** Limits of plausible sensor values (04-INTEGRATIONS §A); everything else is dropped (STB-09). */
public object SensorLimits {
    public const val MAX_HEART_RATE_BPM: Int = 240
    public const val MAX_CADENCE_RPM: Double = 200.0
    public val MAX_WHEEL_SPEED_MPS: Double = Units.kmhToMps(120.0)
    public const val MAX_POWER_W: Int = 3_000
    public const val MAX_BATTERY_PERCENT: Int = 100
    public const val LOW_BATTERY_PERCENT: Int = 15

    /** Event times in CSC/CP crank data and CSC wheel data tick at 1/1024 s; CP wheel data at 1/2048 s. */
    public const val TICKS_PER_SECOND_1024: Int = 1_024
    public const val TICKS_PER_SECOND_2048: Int = 2_048

    /** Without a new revolution event for this long, cadence/speed is 0 (§A.2). */
    public const val NO_EVENT_TIMEOUT_MS: Long = 3_000L
}

/**
 * Bounds-checked little-endian reader (SEC-IN-03): every read checks the remaining length first and
 * returns null on a short packet — packets from nearby devices can be anything.
 */
public class ByteReader(private val bytes: ByteArray) {
    private var offset = 0

    public fun uint8(): Int? = read(UINT8_SIZE)?.toInt()

    public fun uint16(): Int? = read(UINT16_SIZE)?.toInt()

    public fun sint16(): Int? = read(UINT16_SIZE)?.toInt()?.let { if (it >= SINT16_SIGN) it - UINT16_RANGE else it }

    public fun uint32(): Long? = read(UINT32_SIZE)

    /** Skips [count] bytes; false when the packet is too short. */
    public fun skip(count: Int): Boolean = (offset + count <= bytes.size).also { if (it) offset += count }

    private fun read(size: Int): Long? {
        if (offset + size > bytes.size) return null
        var value = 0L
        repeat(size) { index ->
            value =
                value or ((bytes[offset + index].toLong() and BYTE_MASK) shl (BITS_PER_BYTE * index))
        }
        offset += size
        return value
    }

    private companion object {
        const val UINT8_SIZE = 1
        const val UINT16_SIZE = 2
        const val UINT32_SIZE = 4
        const val SINT16_SIGN = 0x8000
        const val UINT16_RANGE = 0x1_0000
        const val BYTE_MASK = 0xFFL
        const val BITS_PER_BYTE = 8
    }
}

private fun Int.hasBit(bit: Int): Boolean = (this shr bit) and 1 == 1

/** Heart Rate Measurement 0x2A37 (04-INTEGRATIONS §A.1). */
public data class HeartRateMeasurement(val bpm: Int, val rrIntervalsMs: List<Int>)

public object HeartRateParser {
    private const val FLAG_UINT16 = 0
    private const val FLAG_ENERGY = 3
    private const val FLAG_RR = 4
    private const val ENERGY_SIZE = 2
    private const val MS_PER_SECOND = 1_000

    /** Parses one notification; null when short or implausible (0 or > 240 bpm). */
    @Suppress("ReturnCount") // One early-out per field of a short or invalid packet (SEC-IN-03).
    public fun parse(bytes: ByteArray): HeartRateMeasurement? {
        val reader = ByteReader(bytes)
        val flags = reader.uint8() ?: return null
        val bpm = (if (flags.hasBit(FLAG_UINT16)) reader.uint16() else reader.uint8()) ?: return null
        if (bpm !in 1..SensorLimits.MAX_HEART_RATE_BPM) return null
        if (flags.hasBit(FLAG_ENERGY) && !reader.skip(ENERGY_SIZE)) return null
        val rr = buildList {
            if (flags.hasBit(FLAG_RR)) {
                while (true) {
                    val ticks = reader.uint16() ?: break
                    add(ticks * MS_PER_SECOND / SensorLimits.TICKS_PER_SECOND_1024)
                }
            }
        }
        return HeartRateMeasurement(bpm, rr)
    }
}

/** Cumulative revolution data: counts and the last event time in sensor ticks. */
public data class RevolutionData(val revolutions: Long, val eventTimeTicks: Int, val ticksPerSecond: Int)

/** CSC Measurement 0x2A5B (04-INTEGRATIONS §A.2): wheel and/or crank data, detected by flags. */
public data class CscMeasurement(val wheel: RevolutionData?, val crank: RevolutionData?)

public object CscParser {
    private const val FLAG_WHEEL = 0
    private const val FLAG_CRANK = 1

    @Suppress("ReturnCount") // One early-out per field of a short packet (SEC-IN-03).
    public fun parse(bytes: ByteArray): CscMeasurement? {
        val reader = ByteReader(bytes)
        val flags = reader.uint8() ?: return null
        val wheel = if (flags.hasBit(FLAG_WHEEL)) {
            val revs = reader.uint32() ?: return null
            val time = reader.uint16() ?: return null
            RevolutionData(revs, time, SensorLimits.TICKS_PER_SECOND_1024)
        } else {
            null
        }
        val crank = if (flags.hasBit(FLAG_CRANK)) {
            val revs = reader.uint16() ?: return null
            val time = reader.uint16() ?: return null
            RevolutionData(revs.toLong(), time, SensorLimits.TICKS_PER_SECOND_1024)
        } else {
            null
        }
        return CscMeasurement(wheel, crank)
    }
}

/** Cycling Power Measurement 0x2A63 (04-INTEGRATIONS §A.3). */
public data class PowerMeasurement(
    val powerW: Int,
    /** Percent of power from the reference pedal, when present. */
    val pedalBalancePercent: Double?,
    val wheel: RevolutionData?,
    val crank: RevolutionData?,
)

public object CyclingPowerParser {
    private const val FLAG_BALANCE = 0
    private const val FLAG_TORQUE = 2
    private const val FLAG_WHEEL = 4
    private const val FLAG_CRANK = 5
    private const val BALANCE_SCALE = 2.0
    private const val TORQUE_SIZE = 2

    /** Parses one notification, skipping optional fields in spec order; negative power is rejected. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod") // Field-by-field early-out (SEC-IN-03).
    public fun parse(bytes: ByteArray): PowerMeasurement? {
        val reader = ByteReader(bytes)
        val flags = reader.uint16() ?: return null
        val power = reader.sint16() ?: return null
        if (power !in 0..SensorLimits.MAX_POWER_W) return null
        val balance = if (flags.hasBit(FLAG_BALANCE)) (reader.uint8() ?: return null) / BALANCE_SCALE else null
        if (flags.hasBit(FLAG_TORQUE) && !reader.skip(TORQUE_SIZE)) return null
        val wheel = if (flags.hasBit(FLAG_WHEEL)) {
            val revs = reader.uint32() ?: return null
            val time = reader.uint16() ?: return null
            RevolutionData(revs, time, SensorLimits.TICKS_PER_SECOND_2048)
        } else {
            null
        }
        val crank = if (flags.hasBit(FLAG_CRANK)) {
            val revs = reader.uint16() ?: return null
            val time = reader.uint16() ?: return null
            RevolutionData(revs.toLong(), time, SensorLimits.TICKS_PER_SECOND_1024)
        } else {
            null
        }
        // Later optional fields (extreme forces/angles, dead spots, energy) are not needed.
        return PowerMeasurement(power, balance, wheel, crank)
    }
}

/** Battery Level 0x2A19 (04-INTEGRATIONS §A.4). */
public object BatteryLevelParser {
    public fun parse(bytes: ByteArray): Int? =
        ByteReader(bytes).uint8()?.takeIf { it in 0..SensorLimits.MAX_BATTERY_PERCENT }
}
