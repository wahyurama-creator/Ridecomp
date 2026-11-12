package com.ridecomp.core.engine.metrics

import com.ridecomp.core.config.CalorieConfig
import com.ridecomp.core.config.EstimatedPowerConfig
import com.ridecomp.core.config.HeartRateConfig
import com.ridecomp.core.config.PowerConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.Sex
import com.ridecomp.core.model.Surface
import com.ridecomp.core.util.RollingAverage
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Power metrics from a power meter (05-DATA-MODEL §3.6), fed once per *moving* second:
 * 3 s / 10 s rolling averages, average (coasting zeros included), max, and Normalized Power
 * (30 s rolling average → 4th power → mean → 4th root, only with ≥ 30 s of data).
 */
public class PowerMetrics {
    private val short = RollingAverage(PowerConfig.SHORT_WINDOW_S)
    private val medium = RollingAverage(PowerConfig.MEDIUM_WINDOW_S)
    private val npWindow = RollingAverage(PowerConfig.NP_WINDOW_S)
    private var sum = 0.0
    private var seconds = 0L
    private var npFourthSum = 0.0
    private var npCount = 0L

    public var maxW: Int? = null
        private set

    /** True once any power-meter value arrived during the ride. */
    public val hasData: Boolean get() = seconds > 0

    /** Adds one moving second; a missing value counts as 0 (coasting / dropout while moving). */
    public fun addMovingSecond(powerW: Int?) {
        val value = (powerW ?: 0).toDouble()
        short.add(value)
        medium.add(value)
        npWindow.add(value)
        sum += value
        seconds++
        powerW?.let { maxW = max(maxW ?: 0, it) }
        npWindow.fullAverageOrNull()?.let {
            npFourthSum += it.pow(PowerConfig.NP_EXPONENT)
            npCount++
        }
    }

    public fun threeSecondW(): Int? = short.averageOrNull()?.roundToInt()

    public fun tenSecondW(): Int? = medium.averageOrNull()?.roundToInt()

    public fun averageW(): Int? = if (seconds > 0) (sum / seconds).roundToInt() else null

    /** Normalized Power, null with less than 30 s of data. */
    public fun normalizedW(): Int? =
        if (npCount > 0) (npFourthSum / npCount).pow(1 / PowerConfig.NP_EXPONENT).roundToInt() else null

    /** Total mechanical work in kJ. */
    public fun workKj(): Double = sum / CalorieConfig.JOULES_PER_KJ
}

/** Average/max over moving seconds with a valid value (HR, cadence > 0 — 05-DATA-MODEL §3.8, §3.11). */
public class AverageMax {
    private var sum = 0.0
    private var count = 0L

    public var max: Int? = null
        private set

    public fun add(value: Int?) {
        if (value == null || value <= 0) return
        sum += value
        count++
        max = max(max ?: 0, value)
    }

    public fun average(): Int? = if (count > 0) (sum / count).roundToInt() else null
}

/** Zones (1-based) from upper bounds in percent of a reference (HR max, FTP). */
public object Zones {
    /**
     * Zone of [value] relative to [reference]. [isUpperInclusive]: the upper bound belongs to the zone
     * (power: Z5 = 106–120 %), otherwise it starts the next zone (HR: Z1 < 60 %).
     */
    public fun of(value: Int?, reference: Int?, upperPercents: List<Double>, isUpperInclusive: Boolean): Int? {
        if (value == null || reference == null || reference <= 0) return null
        val percent = value * Units.PERCENT / reference
        val index = upperPercents.indexOfFirst { if (isUpperInclusive) percent <= it else percent < it }
        return if (index < 0) upperPercents.size + 1 else index + 1
    }

    public fun heartRate(bpm: Int?, hrMax: Int?): Int? =
        of(bpm, hrMax, HeartRateConfig.ZONE_UPPER_PERCENT_HR_MAX, isUpperInclusive = false)

    public fun power(watts: Int?, ftp: Int?): Int? =
        of(watts, ftp, PowerConfig.ZONE_UPPER_PERCENT_FTP, isUpperInclusive = true)
}

/** Inputs of the estimated power model. */
public data class PowerModelInput(
    val totalMassKg: Double,
    val speedMps: Double,
    val accelerationMps2: Double,
    val gradePercent: Double,
    val surface: Surface,
    val position: RidingPosition,
)

/**
 * Estimated power without a power meter (05-DATA-MODEL §3.7):
 * `P = max(0, (F_roll + F_grav + F_aero + F_acc) · v / η)`. Shown as "EST", stored in `est_power`, never
 * in the FIT `power` field.
 */
public object EstimatedPowerModel {
    public fun watts(input: PowerModelInput): Int {
        val theta = atan(input.gradePercent / Units.PERCENT)
        val crr = when (input.surface) {
            Surface.ROAD -> EstimatedPowerConfig.CRR_ROAD
            Surface.GRAVEL -> EstimatedPowerConfig.CRR_GRAVEL
        }
        val cda = when (input.position) {
            RidingPosition.TOPS -> EstimatedPowerConfig.CDA_TOPS_M2
            RidingPosition.HOODS -> EstimatedPowerConfig.CDA_HOODS_M2
            RidingPosition.DROPS -> EstimatedPowerConfig.CDA_DROPS_M2
        }
        val v = input.speedMps
        val weight = input.totalMassKg * EstimatedPowerConfig.GRAVITY_MPS2
        val rolling = crr * weight * cos(theta)
        val gravity = weight * sin(theta)
        val aero = HALF * EstimatedPowerConfig.AIR_DENSITY_KG_M3 * cda * v * v
        val acceleration = input.totalMassKg * input.accelerationMps2
        return max(0.0, (rolling + gravity + aero + acceleration) * v / EstimatedPowerConfig.DRIVETRAIN_EFFICIENCY)
            .roundToInt()
    }

    private const val HALF = 0.5
}

/** Rider data needed by the HR calorie formula. */
public data class CalorieProfile(val weightKg: Double, val ageYears: Int?, val sex: Sex?)

/** Keytel et al. kcal for one second at [bpm]; null without age/sex (05-DATA-MODEL §3.10). */
public object KeytelCalories {
    public fun kcalPerSecond(bpm: Int, profile: CalorieProfile): Double? {
        val age = profile.ageYears
        val sex = profile.sex
        if (age == null || sex == null) return null
        // Keytel gives kJ/min; ÷ 4.184 → kcal/min.
        val perMinuteKj = when (sex) {
            Sex.MALE ->
                CalorieConfig.MALE_INTERCEPT + CalorieConfig.MALE_HR * bpm +
                    CalorieConfig.MALE_WEIGHT * profile.weightKg + CalorieConfig.MALE_AGE * age
            Sex.FEMALE ->
                CalorieConfig.FEMALE_INTERCEPT + CalorieConfig.FEMALE_HR * bpm +
                    CalorieConfig.FEMALE_WEIGHT * profile.weightKg + CalorieConfig.FEMALE_AGE * age
        }
        return max(0.0, perMinuteKj / CalorieConfig.KJ_PER_KCAL) / Units.SECONDS_PER_MINUTE
    }
}
