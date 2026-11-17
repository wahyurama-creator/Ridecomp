package com.ridecomp.core.engine.sensors

import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanSession
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One Indoor Bike Data notification of a smart trainer (FTMS 0x2AD2, T9.1); absent fields are null. */
public data class IndoorBikeData(
    val speedKmh: Double?,
    val cadenceRpm: Double?,
    val powerW: Int?,
    val heartRateBpm: Int?,
)

/**
 * Indoor Bike Data 0x2AD2 (Bluetooth FTMS 1.0 §4.9). Fields follow the flags in spec order; bit 0 is
 * "More Data", so instantaneous speed is present when it is 0. Out-of-range values are dropped (STB-09).
 */
public object IndoorBikeDataParser {
    private const val FLAG_MORE_DATA = 0
    private const val FLAG_AVG_SPEED = 1
    private const val FLAG_CADENCE = 2
    private const val FLAG_AVG_CADENCE = 3
    private const val FLAG_DISTANCE = 4
    private const val FLAG_RESISTANCE = 5
    private const val FLAG_POWER = 6
    private const val FLAG_AVG_POWER = 7
    private const val FLAG_ENERGY = 8
    private const val FLAG_HEART_RATE = 9
    private const val SPEED_SCALE = 100.0
    private const val CADENCE_SCALE = 2.0
    private const val UINT16_SIZE = 2
    private const val UINT24_SIZE = 3

    /** Total, per hour and per minute energy. */
    private const val ENERGY_SIZE = 5
    private const val KMH_LIMIT = 120.0

    @Suppress("ReturnCount", "CyclomaticComplexMethod") // Field-by-field early-out (SEC-IN-03).
    public fun parse(bytes: ByteArray): IndoorBikeData? {
        val reader = ByteReader(bytes)
        val flags = reader.uint16() ?: return null
        val speed = if (!flags.hasBit(FLAG_MORE_DATA)) (reader.uint16() ?: return null) / SPEED_SCALE else null
        if (flags.hasBit(FLAG_AVG_SPEED) && !reader.skip(UINT16_SIZE)) return null
        val cadence = if (flags.hasBit(FLAG_CADENCE)) (reader.uint16() ?: return null) / CADENCE_SCALE else null
        if (flags.hasBit(FLAG_AVG_CADENCE) && !reader.skip(UINT16_SIZE)) return null
        if (flags.hasBit(FLAG_DISTANCE) && !reader.skip(UINT24_SIZE)) return null
        if (flags.hasBit(FLAG_RESISTANCE) && !reader.skip(UINT16_SIZE)) return null
        val power = if (flags.hasBit(FLAG_POWER)) reader.sint16() ?: return null else null
        if (flags.hasBit(FLAG_AVG_POWER) && !reader.skip(UINT16_SIZE)) return null
        if (flags.hasBit(FLAG_ENERGY) && !reader.skip(ENERGY_SIZE)) return null
        val heartRate = if (flags.hasBit(FLAG_HEART_RATE)) reader.uint8() ?: return null else null
        return IndoorBikeData(
            speedKmh = speed?.takeIf { it in 0.0..KMH_LIMIT },
            cadenceRpm = cadence?.takeIf { it in 0.0..SensorLimits.MAX_CADENCE_RPM },
            powerW = power?.takeIf { it in 0..SensorLimits.MAX_POWER_W },
            heartRateBpm = heartRate?.takeIf { it in 1..SensorLimits.MAX_HEART_RATE_BPM },
        )
    }

    private fun Int.hasBit(bit: Int): Boolean = (this shr bit) and 1 == 1
}

/** Fitness Machine Control Point 0x2AD9 commands (FTMS 1.0 §4.16). */
public object FtmsCommands {
    private const val OP_REQUEST_CONTROL: Byte = 0x00
    private const val OP_START: Byte = 0x07
    private const val OP_SET_TARGET_POWER: Byte = 0x05
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    public val REQUEST_CONTROL: ByteArray = byteArrayOf(OP_REQUEST_CONTROL)
    public val START: ByteArray = byteArrayOf(OP_START)

    /** ERG: the trainer holds this power whatever the cadence (sint16 W, little endian). */
    public fun setTargetPower(watts: Int): ByteArray = byteArrayOf(
        OP_SET_TARGET_POWER,
        (watts and BYTE_MASK).toByte(),
        ((watts shr BITS_PER_BYTE) and BYTE_MASK).toByte(),
    )
}

/** ERG limits and the power of each training zone (T9.1, D-95). */
public object ErgConfig {
    public const val MIN_W: Int = 50
    public const val MAX_W: Int = 1_000
    public const val STEP_W: Int = 10

    /** Without FTP the trainer starts here; the rider adjusts with − / +. */
    public const val DEFAULT_W: Int = 100

    /** Middle of each power zone in % FTP (zones of 05-DATA-MODEL §3.9). */
    private val MID_PERCENT_FTP = mapOf(
        Intensity.RECOVERY to 50,
        Intensity.ENDURANCE to 65,
        Intensity.TEMPO to 83,
        Intensity.THRESHOLD to 98,
        Intensity.VO2MAX to 113,
    )
    private const val PERCENT = 100.0

    /** Highest zone held for a whole session; harder sessions are intervals and start easy. */
    public const val STEADY_UP_TO_ZONE: Int = 3

    public fun watts(intensity: Intensity, ftpW: Int): Int =
        (ftpW * requireNotNull(MID_PERCENT_FTP[intensity]) / PERCENT).roundToInt().coerceIn(MIN_W, MAX_W)

    /**
     * Where ERG starts for today's session: steady sessions (zone 1–3) at their zone; interval sessions
     * (zone 4–5) in zone 2, and the rider switches to the hard zone for each interval. Null without FTP.
     */
    public fun startWatts(session: PlanSession?, ftpW: Int?): Int? {
        if (ftpW == null) return null
        val intensity = session?.intensity?.takeIf { it.zone <= STEADY_UP_TO_ZONE } ?: Intensity.ENDURANCE
        return watts(intensity, ftpW)
    }
}

/** A smart trainer as the ride sees it: connected, under app control, and the ERG target. */
public data class TrainerState(
    val isConnected: Boolean = false,
    val isControlled: Boolean = false,
    val targetW: Int? = null,
)

/**
 * The ERG target shared by the screen (−/+), the recording service (start target) and the BLE layer,
 * which writes it to the trainer whenever it changes. One instance for the app.
 */
public class TrainerControl {
    private val mutableState = MutableStateFlow(TrainerState())
    public val state: StateFlow<TrainerState> = mutableState.asStateFlow()

    /** Null releases the trainer (no target). */
    public fun setTarget(watts: Int?) {
        mutableState.update { it.copy(targetW = watts?.coerceIn(ErgConfig.MIN_W, ErgConfig.MAX_W)) }
    }

    public fun adjust(deltaW: Int) {
        mutableState.update { state ->
            state.copy(
                targetW = ((state.targetW ?: ErgConfig.DEFAULT_W) + deltaW).coerceIn(ErgConfig.MIN_W, ErgConfig.MAX_W),
            )
        }
    }

    /** From the BLE layer: link and control state. */
    public fun onLink(isConnected: Boolean, isControlled: Boolean) {
        mutableState.update { it.copy(isConnected = isConnected, isControlled = isConnected && isControlled) }
    }
}
