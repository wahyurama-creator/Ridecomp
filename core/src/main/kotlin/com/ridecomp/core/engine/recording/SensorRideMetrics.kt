package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.CalorieConfig
import com.ridecomp.core.config.EstimatedPowerConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.metrics.AverageMax
import com.ridecomp.core.engine.metrics.CalorieProfile
import com.ridecomp.core.engine.metrics.EstimatedPowerModel
import com.ridecomp.core.engine.metrics.ExponentialSmoother
import com.ridecomp.core.engine.metrics.KeytelCalories
import com.ridecomp.core.engine.metrics.PowerMetrics
import com.ridecomp.core.engine.metrics.PowerModelInput
import com.ridecomp.core.engine.metrics.Zones
import com.ridecomp.core.engine.sensors.SensorFusion
import com.ridecomp.core.engine.sensors.SensorReading
import com.ridecomp.core.engine.sensors.SensorSnapshot
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderProfile
import kotlin.math.roundToInt

/** Sensor values shown on the ride screen. */
public data class LiveSensors(
    val heartRateBpm: Int? = null,
    val heartRateZone: Int? = null,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val cadenceRpm: Int? = null,
    val avgCadenceRpm: Int? = null,
    val powerW: Int? = null,
    val power3sW: Int? = null,
    val power10sW: Int? = null,
    val avgPowerW: Int? = null,
    val maxPowerW: Int? = null,
    val normalizedPowerW: Int? = null,
    val powerZone: Int? = null,
    val wattsPerKg: Double? = null,
    val estimatedPowerW: Int? = null,
    /** Average of the estimate over moving time (summary "POWER EST", never written to FIT power). */
    val avgEstimatedPowerW: Int? = null,
    val calories: Int? = null,
    val snapshot: SensorSnapshot = SensorSnapshot(),
)

/**
 * Sensor-based metrics of one ride (T2.5–T2.7): fusion, power (3 s/10 s/avg/max/NP, W/kg, zones), HR
 * (avg/max/zone), cadence (avg of pedalling seconds), estimated power without a power meter (never written
 * as `power`), and calories by priority power meter → HR (Keytel) → estimated power (05-DATA-MODEL §3.10).
 */
public class SensorRideMetrics(private val profile: RiderProfile, private val ageYears: Int?) {
    private val fusion = SensorFusion(profile.wheelCircumferenceMm / Units.MM_PER_METER)
    private val power = PowerMetrics()
    private val heartRate = AverageMax()
    private val cadence = AverageMax()
    private val estimated = AverageMax()
    private val acceleration = ExponentialSmoother(EstimatedPowerConfig.ACCELERATION_EMA_ALPHA)
    private var previousSpeedMps: Double? = null
    private var heartRateKcal = 0.0
    private var hasHeartRateCalories = false
    private var estimatedKj = 0.0
    private var lastEstimateW: Int? = null

    public fun onReading(reading: SensorReading) {
        fusion.onReading(reading)
    }

    public fun snapshot(nowMs: Long): SensorSnapshot = fusion.snapshot(nowMs)

    /** One moving second: aggregates and the per-second estimate. Returns the estimated power (or null). */
    public fun onMovingSecond(snapshot: SensorSnapshot, speedMps: Double?, gradePercent: Double?): Int? {
        val estimate = if (snapshot.hasPowerMeter) null else estimate(snapshot, speedMps, gradePercent)
        accumulate(snapshot.heartRateBpm, snapshot.cadenceRpm, snapshot.powerW, snapshot.hasPowerMeter, estimate)
        lastEstimateW = estimate
        return estimate
    }

    /**
     * One stored moving second, replayed after a crash/kill so averages, NP, best efforts and calories
     * include the part of the ride before it (03-ARCHITECTURE §4.5). A stored power value means a power meter.
     */
    public fun restoreSecond(sample: RideSample) {
        accumulate(
            sample.heartRateBpm,
            sample.cadenceRpm,
            sample.powerW,
            hasPowerMeter = sample.powerW != null,
            estimate = sample.estPowerW,
        )
    }

    private fun accumulate(heartRateBpm: Int?, cadenceRpm: Int?, powerW: Int?, hasPowerMeter: Boolean, estimate: Int?) {
        if (hasPowerMeter) power.addMovingSecond(powerW)
        heartRate.add(heartRateBpm)
        cadence.add(cadenceRpm)
        heartRateBpm?.let { bpm ->
            KeytelCalories.kcalPerSecond(bpm, CalorieProfile(profile.weightKg, ageYears, profile.sex))?.let {
                heartRateKcal += it
                hasHeartRateCalories = true
            }
        }
        estimate?.let {
            estimated.add(it)
            estimatedKj += it / CalorieConfig.JOULES_PER_KJ
        }
    }

    public fun live(snapshot: SensorSnapshot): LiveSensors = LiveSensors(
        heartRateBpm = snapshot.heartRateBpm,
        heartRateZone = Zones.heartRate(snapshot.heartRateBpm, profile.hrMaxBpm),
        avgHeartRateBpm = heartRate.average(),
        maxHeartRateBpm = heartRate.max,
        cadenceRpm = snapshot.cadenceRpm,
        avgCadenceRpm = cadence.average(),
        powerW = snapshot.powerW,
        power3sW = power.threeSecondW().takeIf { power.hasData },
        power10sW = power.tenSecondW().takeIf { power.hasData },
        avgPowerW = power.averageW(),
        maxPowerW = power.maxW,
        normalizedPowerW = power.normalizedW(),
        powerZone = Zones.power(power.threeSecondW().takeIf { power.hasData }, profile.ftpW),
        wattsPerKg = power.threeSecondW()?.takeIf { power.hasData }?.let { it / profile.weightKg },
        estimatedPowerW = lastEstimateW,
        avgEstimatedPowerW = estimated.average(),
        calories = calories(),
        snapshot = snapshot,
    )

    /** Adds the sensor totals to [base] (session/activity). */
    public fun totals(base: RideTotals): RideTotals = base.copy(
        avgHeartRateBpm = heartRate.average(),
        maxHeartRateBpm = heartRate.max,
        avgCadenceRpm = cadence.average(),
        avgPowerW = power.averageW(),
        maxPowerW = power.maxW,
        normalizedPowerW = power.normalizedW(),
        estAvgPowerW = estimated.average(),
        calories = calories(),
    )

    private fun estimate(snapshot: SensorSnapshot, speedMps: Double?, gradePercent: Double?): Int? {
        val previous = previousSpeedMps
        previousSpeedMps = speedMps
        val accel = if (speedMps != null && previous != null) acceleration.add(speedMps - previous) else 0.0
        // Coasting with a cadence sensor: legs are not pushing (05-DATA-MODEL §3.7).
        val isCoasting = snapshot.hasCadenceSensor && snapshot.cadenceRpm == 0
        return when {
            speedMps == null -> null
            isCoasting -> 0
            else -> EstimatedPowerModel.watts(
                PowerModelInput(
                    totalMassKg = profile.weightKg + profile.bikeWeightKg,
                    speedMps = speedMps,
                    accelerationMps2 = accel,
                    gradePercent = gradePercent ?: 0.0,
                    surface = profile.surface,
                    position = profile.ridingPosition,
                ),
            )
        }
    }

    private fun calories(): Int? = when {
        power.hasData -> (power.workKj() * CalorieConfig.KCAL_PER_KJ).roundToInt()
        hasHeartRateCalories -> heartRateKcal.roundToInt()
        estimatedKj > 0 -> (estimatedKj * CalorieConfig.KCAL_PER_KJ).roundToInt()
        else -> null
    }
}
