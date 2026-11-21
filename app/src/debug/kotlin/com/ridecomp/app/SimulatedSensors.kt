package com.ridecomp.app

import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.sensors.BatteryLevelParser
import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.CscParser
import com.ridecomp.core.engine.sensors.CyclingPowerParser
import com.ridecomp.core.engine.sensors.DiscoveredSensor
import com.ridecomp.core.engine.sensors.ErgConfig
import com.ridecomp.core.engine.sensors.HeartRateParser
import com.ridecomp.core.engine.sensors.IndoorBikeDataParser
import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorConnector
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.SensorLimits
import com.ridecomp.core.engine.sensors.SensorReading
import com.ridecomp.core.engine.sensors.TrainerControl
import com.ridecomp.core.source.Clock
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Debug-only virtual BLE sensors (owner request: test HR, cadence and power without real sensors). They
 * follow [SimulatedRide] (stopped = no cadence or power, HR settles) and send real BLE packets: Heart Rate
 * 0x2A37, CSC 0x2A5B, Cycling Power 0x2A63 and Battery 0x2A19 bytes go through the app's own parsers, so
 * the whole path from bytes to the FIT file is exercised. The HR strap drops out briefly now and then to
 * test the "sensor lost" alert and the reconnect. Debug source set only (SEC-BLD-04).
 */
object SimulatedSensors {
    const val ADDRESS_PREFIX = "SIM:"
    val HEART_RATE = DiscoveredSensor("${ADDRESS_PREFIX}01", "SIM HR Strap", setOf(SensorKind.HEART_RATE), -48)
    val SPEED_CADENCE =
        DiscoveredSensor("${ADDRESS_PREFIX}02", "SIM Speed/Cadence", setOf(SensorKind.SPEED_CADENCE), -55)
    val POWER = DiscoveredSensor("${ADDRESS_PREFIX}03", "SIM Power Meter", setOf(SensorKind.POWER), -61)
    val TRAINER = DiscoveredSensor(
        "${ADDRESS_PREFIX}04",
        "SIM Smart Trainer",
        setOf(SensorKind.POWER, SensorKind.SPEED_CADENCE),
        -52,
        isTrainer = true,
    )
    val ALL = listOf(HEART_RATE, SPEED_CADENCE, POWER, TRAINER)

    /** One device shows up after another, like a real scan. */
    const val FOUND_EVERY_MS = 700L
    const val TICK_MS = 1_000L
    const val BATTERY_PERCENT = 82

    /** The HR strap is silent for [DROP_S] seconds every [DROP_EVERY_S] seconds. */
    const val DROP_EVERY_S = 300L
    const val DROP_S = 12L
    const val DROP_AT_S = 90L

    const val REST_HR_BPM = 95.0
    const val HR_PER_MPS = 7.0
    const val HR_SWING_BPM = 6.0
    const val BASE_CADENCE_RPM = 86.0
    const val CADENCE_SWING_RPM = 6.0
    const val POWER_PER_MPS = 26.0
    const val POWER_SWING_W = 35.0
    const val SWING_PERIOD_S = 23.0

    /** Virtual trainer: follows the ERG target with a few watts of noise, speed from power. */
    const val TRAINER_SWING_W = 6.0
    const val TRAINER_BASE_KMH = 15.0
    const val TRAINER_KMH_PER_W = 0.08

    fun isSimulated(address: String): Boolean = address.startsWith(ADDRESS_PREFIX)
}

/** Scanner that finds the virtual sensors while the developer switch is on, else the real one. */
class DebugBleScanner(private val real: BleScanner, private val settings: DeveloperSettings) : BleScanner {
    override fun isBluetoothEnabled(): Boolean = settings.isSimulatingSensors || real.isBluetoothEnabled()

    override fun hasPermission(): Boolean = settings.isSimulatingSensors || real.hasPermission()

    override fun scan(timeoutMs: Long, isWide: Boolean): Flow<DiscoveredSensor> = if (settings.isSimulatingSensors) {
        flow {
            SimulatedSensors.ALL.forEach { sensor ->
                delay(SimulatedSensors.FOUND_EVERY_MS)
                emit(sensor)
            }
        }
    } else {
        real.scan(timeoutMs, isWide)
    }
}

/**
 * Virtual sensors stream simulated packets while the developer switch is on (paired virtual sensors stay
 * silent when it is off, like a device that is not there); real sensors keep using the real connector.
 */
class DebugSensorConnector(
    private val real: SensorConnector,
    private val clock: Clock,
    private val settings: DeveloperSettings,
    private val trainer: TrainerControl,
) : SensorConnector {
    override fun readings(sensors: List<SavedSensor>): Flow<SensorReading> {
        val (simulated, physical) = sensors.partition { SimulatedSensors.isSimulated(it.address) }
        val realFlow = if (physical.isEmpty()) emptyFlow() else real.readings(physical)
        val simulatedFlow = if (settings.isSimulatingSensors) simulatedReadings(simulated) else emptyFlow()
        return merge(realFlow, simulatedFlow)
    }

    private fun simulatedReadings(sensors: List<SavedSensor>): Flow<SensorReading> = channelFlow {
        sensors.forEach { sensor -> launch { stream(sensor) } }
    }

    private suspend fun SendChannel<SensorReading>.stream(sensor: SavedSensor) {
        if (sensor.isTrainer) return streamTrainer(sensor)
        val packets = PacketBuilder()
        connect(sensor, isConnected = true)
        BatteryLevelParser.parse(byteArrayOf(SimulatedSensors.BATTERY_PERCENT.toByte()))?.let { percent ->
            sensor.kinds.forEach { send(SensorReading.Battery(it, percent, clock.nowMs(), sensor.address)) }
        }
        var second = 0L
        var isConnected = true
        while (true) {
            delay(SimulatedSensors.TICK_MS)
            second++
            val isDropped = SensorKind.HEART_RATE in sensor.kinds &&
                (second - SimulatedSensors.DROP_AT_S).mod(SimulatedSensors.DROP_EVERY_S) < SimulatedSensors.DROP_S &&
                second >= SimulatedSensors.DROP_AT_S
            if (isDropped != !isConnected) {
                isConnected = !isDropped
                connect(sensor, isConnected)
            }
            if (isConnected) packets.next(second, sensor.kinds, clock.nowMs()).forEach { send(it) }
        }
    }

    /** A controllable trainer: Indoor Bike Data at the ERG target, through the real FTMS parser (T9.1). */
    private suspend fun SendChannel<SensorReading>.streamTrainer(sensor: SavedSensor) {
        connect(sensor, isConnected = true)
        trainer.onLink(isConnected = true, isControlled = true)
        try {
            var second = 0L
            while (true) {
                delay(SimulatedSensors.TICK_MS)
                second++
                val swing = sin(2 * PI * second / SimulatedSensors.SWING_PERIOD_S)
                val watts =
                    (trainer.state.value.targetW ?: ErgConfig.DEFAULT_W) + SimulatedSensors.TRAINER_SWING_W * swing
                val kmh = SimulatedSensors.TRAINER_BASE_KMH + SimulatedSensors.TRAINER_KMH_PER_W * watts
                val cadence = SimulatedSensors.BASE_CADENCE_RPM + SimulatedSensors.CADENCE_SWING_RPM * swing
                IndoorBikeDataParser.parse(indoorBikePacket(kmh, cadence, watts.roundToInt()))
                    ?.let { send(SensorReading.Trainer(it, clock.nowMs())) }
            }
        } finally {
            trainer.onLink(isConnected = false, isControlled = false)
        }
    }

    private suspend fun SendChannel<SensorReading>.connect(sensor: SavedSensor, isConnected: Boolean) {
        sensor.kinds.forEach { send(SensorReading.Connection(it, isConnected, clock.nowMs())) }
    }
}

/** Builds one second of BLE notification bytes and parses them like the real connector does. */
private class PacketBuilder {
    private var wheelRevolutions = 0.0
    private var crankRevolutions = 0.0
    private var lastWheelTicks = 0
    private var lastCrankTicks = 0
    private val wheelM = ProfileDefaults.WHEEL_CIRCUMFERENCE_MM / Units.MM_PER_METER

    fun next(second: Long, kinds: Set<SensorKind>, nowMs: Long): List<SensorReading> {
        val speed = SimulatedRide.speedMps(second)
        val swing = sin(2 * PI * second / SimulatedSensors.SWING_PERIOD_S)
        val isMoving = speed > 0
        val cadence = if (isMoving) {
            SimulatedSensors.BASE_CADENCE_RPM + SimulatedSensors.CADENCE_SWING_RPM * swing
        } else {
            0.0
        }
        if (isMoving) {
            val wheelPerS = speed / wheelM
            val crankPerS = cadence / SECONDS_PER_MINUTE
            wheelRevolutions += wheelPerS
            crankRevolutions += crankPerS
            // Like a real sensor: the event time is when the last whole revolution finished, not "now".
            lastWheelTicks = eventTicks(second, wheelRevolutions, wheelPerS)
            lastCrankTicks = eventTicks(second, crankRevolutions, crankPerS)
        }
        return buildList {
            if (SensorKind.HEART_RATE in kinds) {
                val bpm = SimulatedSensors.REST_HR_BPM + SimulatedSensors.HR_PER_MPS * speed +
                    SimulatedSensors.HR_SWING_BPM * swing
                HeartRateParser.parse(byteArrayOf(0, bpm.roundToInt().toByte()))
                    ?.let { add(SensorReading.HeartRate(it, nowMs)) }
            }
            if (SensorKind.SPEED_CADENCE in kinds) {
                CscParser.parse(cscPacket())?.let { add(SensorReading.SpeedCadence(it, nowMs)) }
            }
            if (SensorKind.POWER in kinds) {
                val watts = if (isMoving) {
                    SimulatedSensors.POWER_PER_MPS * speed + SimulatedSensors.POWER_SWING_W * swing
                } else {
                    0.0
                }
                CyclingPowerParser.parse(powerPacket(watts.roundToInt().coerceAtLeast(0)))
                    ?.let { add(SensorReading.Power(it, nowMs)) }
            }
        }
    }

    /** Flags: wheel + crank; wheel revs uint32, wheel time uint16, crank revs uint16, crank time uint16. */
    private fun cscPacket(): ByteArray = byteArrayOf(CSC_WHEEL_AND_CRANK) +
        le(wheelRevolutions.toLong(), UINT32_BYTES) + le(lastWheelTicks.toLong(), UINT16_BYTES) +
        le(crankRevolutions.toLong() and UINT16_MASK.toLong(), UINT16_BYTES) + le(lastCrankTicks.toLong(), UINT16_BYTES)

    /** Flags uint16 (crank data present), power sint16, crank revs uint16, crank time uint16. */
    private fun powerPacket(watts: Int): ByteArray = le(POWER_CRANK_FLAG, UINT16_BYTES) +
        le(watts.toLong(), UINT16_BYTES) +
        le(crankRevolutions.toLong() and UINT16_MASK.toLong(), UINT16_BYTES) + le(lastCrankTicks.toLong(), UINT16_BYTES)

    private fun eventTicks(second: Long, revolutions: Double, perSecond: Double): Int {
        val sinceLastS = (revolutions - floor(revolutions)) / perSecond
        return ((second - sinceLastS) * SensorLimits.TICKS_PER_SECOND_1024).toInt() and UINT16_MASK
    }

    private fun le(value: Long, size: Int): ByteArray =
        ByteArray(size) { index -> (value shr (BITS_PER_BYTE * index)).toByte() }

    private companion object {
        const val CSC_WHEEL_AND_CRANK: Byte = 0x03
        const val POWER_CRANK_FLAG = 0x20L
        const val UINT16_MASK = 0xFFFF
        const val UINT16_BYTES = 2
        const val UINT32_BYTES = 4
        const val BITS_PER_BYTE = 8
        const val SECONDS_PER_MINUTE = 60.0
    }
}

/** FTMS Indoor Bike Data: flags (cadence + power, speed present), speed 0.01 km/h, cadence 0.5 rpm, power W. */
private fun indoorBikePacket(kmh: Double, cadenceRpm: Double, watts: Int): ByteArray {
    val flags = (1 shl INDOOR_FLAG_CADENCE) or (1 shl INDOOR_FLAG_POWER)
    return le16(flags) + le16((kmh * INDOOR_SPEED_SCALE).roundToInt()) +
        le16((cadenceRpm * INDOOR_CADENCE_SCALE).roundToInt()) + le16(watts)
}

private fun le16(value: Int): ByteArray = byteArrayOf(
    (value and BYTE_MASK).toByte(),
    (
        (value shr BYTE_BITS) and
            BYTE_MASK
        ).toByte(),
)

private const val INDOOR_FLAG_CADENCE = 2
private const val INDOOR_FLAG_POWER = 6
private const val INDOOR_SPEED_SCALE = 100.0
private const val INDOOR_CADENCE_SCALE = 2.0
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
