package com.ridecomp.core.engine.sensors

import com.ridecomp.core.config.RecordingConfig
import com.ridecomp.core.config.Units

/** The three BLE sensor profiles (04-INTEGRATIONS §A). */
public enum class SensorKind { HEART_RATE, SPEED_CADENCE, POWER }

/** Which sensor provides cadence when both do (03-ARCHITECTURE §4.3; default power meter). */
public enum class CadencePriority { POWER_METER, SPEED_CADENCE }

/** Data from the BLE layer, already parsed. [receivedAtMs] uses the same clock as the ride ticks. */
public sealed interface SensorReading {
    public val receivedAtMs: Long

    public data class HeartRate(val measurement: HeartRateMeasurement, override val receivedAtMs: Long) : SensorReading

    public data class SpeedCadence(val measurement: CscMeasurement, override val receivedAtMs: Long) : SensorReading

    public data class Power(val measurement: PowerMeasurement, override val receivedAtMs: Long) : SensorReading

    /** A smart trainer (FTMS, T9.1): power, cadence and speed directly. */
    public data class Trainer(val data: IndoorBikeData, override val receivedAtMs: Long) : SensorReading

    public data class Battery(
        val kind: SensorKind,
        val percent: Int,
        override val receivedAtMs: Long,
        /** BLE address, to store the last battery level of the paired sensor. */
        val address: String? = null,
    ) : SensorReading

    public data class Connection(val kind: SensorKind, val isConnected: Boolean, override val receivedAtMs: Long) :
        SensorReading
}

/** Status of one sensor for the status bar and alerts (T2.9). */
public data class SensorState(val isConnected: Boolean, val batteryPercent: Int?)

/** Sensor values usable for this second (null = missing or older than 3 s). */
public data class SensorSnapshot(
    val heartRateBpm: Int? = null,
    val cadenceRpm: Int? = null,
    val powerW: Int? = null,
    val wheelSpeedMps: Double? = null,
    val hasCadenceSensor: Boolean = false,
    val hasPowerMeter: Boolean = false,
    val states: Map<SensorKind, SensorState> = emptyMap(),
)

/**
 * Merges sensor notifications into one value per second (03-ARCHITECTURE §4.3): a value is used only when
 * it is at most 3 s old; speed = wheel sensor > GPS; cadence = power meter > CSC (configurable); power from
 * the power meter only. Confine to one coroutine (CONC-01).
 */
public class SensorFusion(
    wheelCircumferenceM: Double,
    private val cadencePriority: CadencePriority = CadencePriority.POWER_METER,
) {
    private val wheelSpeed = WheelSpeedCalculator(wheelCircumferenceM)
    private val cscCadence = CadenceCalculator()
    private val powerCadence = CadenceCalculator()
    private var heartRate: Pair<Int, Long>? = null
    private var power: Pair<Int, Long>? = null
    private var trainerPower: Pair<Int, Long>? = null
    private var trainerCadence: Pair<Double, Long>? = null
    private var trainerSpeedMps: Pair<Double, Long>? = null
    private var lastWheelAtMs: Long? = null
    private var lastCscCrankAtMs: Long? = null
    private var lastPowerCrankAtMs: Long? = null
    private val states = mutableMapOf<SensorKind, SensorState>()

    public fun onReading(reading: SensorReading) {
        val at = reading.receivedAtMs
        when (reading) {
            is SensorReading.HeartRate -> heartRate = reading.measurement.bpm to at
            is SensorReading.SpeedCadence -> {
                reading.measurement.wheel?.let { wheelSpeed.add(it, at).also { lastWheelAtMs = at } }
                reading.measurement.crank?.let { cscCadence.add(it, at).also { lastCscCrankAtMs = at } }
            }
            is SensorReading.Power -> {
                power = reading.measurement.powerW to at
                reading.measurement.crank?.let { powerCadence.add(it, at).also { lastPowerCrankAtMs = at } }
            }
            is SensorReading.Trainer -> onTrainer(reading.data, at)
            is SensorReading.Battery -> states[reading.kind] =
                state(reading.kind).copy(batteryPercent = reading.percent)
            is SensorReading.Connection -> onConnection(reading)
        }
    }

    public fun snapshot(nowMs: Long): SensorSnapshot {
        val csc = cscCadence.current(nowMs)?.takeIf { lastCscCrankAtMs.isFresh(nowMs) }
        val pm = powerCadence.current(nowMs)?.takeIf { lastPowerCrankAtMs.isFresh(nowMs) }
        val trainer = trainerCadence.fresh(nowMs)
        val cadence = when (cadencePriority) {
            CadencePriority.POWER_METER -> pm ?: csc
            CadencePriority.SPEED_CADENCE -> csc ?: pm
        } ?: trainer
        return SensorSnapshot(
            heartRateBpm = heartRate?.takeIf { it.second.isFresh(nowMs) }?.first,
            cadenceRpm = cadence?.toInt(),
            // A power meter on the bike wins over the trainer's own estimate.
            powerW = power.fresh(nowMs) ?: trainerPower.fresh(nowMs),
            wheelSpeedMps =
            wheelSpeed.current(nowMs)?.takeIf { lastWheelAtMs.isFresh(nowMs) } ?: trainerSpeedMps.fresh(nowMs),
            hasCadenceSensor = lastCscCrankAtMs != null || lastPowerCrankAtMs != null || trainerCadence != null,
            hasPowerMeter = power != null || trainerPower != null,
            states = states.toMap(),
        )
    }

    private fun onConnection(reading: SensorReading.Connection) {
        states[reading.kind] = state(reading.kind).copy(isConnected = reading.isConnected)
        // Counters may restart after a reconnect: never compute a rate across the gap.
        if (!reading.isConnected) {
            when (reading.kind) {
                SensorKind.HEART_RATE -> Unit
                SensorKind.SPEED_CADENCE -> {
                    wheelSpeed.reset()
                    cscCadence.reset()
                }
                SensorKind.POWER -> powerCadence.reset()
            }
        }
    }

    private fun onTrainer(data: IndoorBikeData, at: Long) {
        data.powerW?.let { trainerPower = it to at }
        data.cadenceRpm?.let { trainerCadence = it to at }
        data.speedKmh?.let { trainerSpeedMps = Units.kmhToMps(it) to at }
        data.heartRateBpm?.let { if (heartRate?.second?.isFresh(at) != true) heartRate = it to at }
    }

    private fun <T> Pair<T, Long>?.fresh(nowMs: Long): T? = this?.takeIf { it.second.isFresh(nowMs) }?.first

    private fun state(kind: SensorKind): SensorState =
        states[kind] ?: SensorState(isConnected = false, batteryPercent = null)

    private fun Long?.isFresh(nowMs: Long): Boolean = this != null && nowMs - this <= RecordingConfig.SAMPLE_MAX_AGE_MS
}

/** Sensor problems shown as ride banners (T2.9, F-14). */
public enum class SensorAlertType { LOST, LOW_BATTERY }

public data class SensorAlert(val kind: SensorKind, val type: SensorAlertType, val batteryPercent: Int? = null)

/**
 * Current sensor alerts, one per sensor at most: a sensor that was connected and dropped is LOST (it is
 * reconnecting); a connected sensor below [SensorLimits.LOW_BATTERY_PERCENT] has LOW_BATTERY. A sensor
 * that never connected during this ride is not reported.
 */
public fun SensorSnapshot.alerts(): List<SensorAlert> = SensorKind.entries.mapNotNull { kind ->
    val state = states[kind]
    val battery = state?.batteryPercent
    when {
        state == null -> null
        !state.isConnected -> SensorAlert(kind, SensorAlertType.LOST)
        battery != null && battery < SensorLimits.LOW_BATTERY_PERCENT ->
            SensorAlert(kind, SensorAlertType.LOW_BATTERY, battery)
        else -> null
    }
}
