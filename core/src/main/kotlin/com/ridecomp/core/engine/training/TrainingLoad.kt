package com.ridecomp.core.engine.training

import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.metrics.Zones
import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.Sex
import com.ridecomp.core.model.ageYears
import java.time.LocalDate
import kotlin.math.exp

/** Training load model (12-FEATURE-SPECS §10). Estimates for trends, not medical values. */
public object TrainingConfig {
    /** Fitness (CTL) and fatigue (ATL) time constants in days. */
    public const val CTL_DAYS: Double = 42.0
    public const val ATL_DAYS: Double = 7.0

    /** Resting HR used when the profile has none (marked as an estimate). */
    public const val DEFAULT_HR_REST_BPM: Int = 60

    /** Banister TRIMP coefficients: load = minutes × HRr × A × e^(B × HRr). */
    public const val TRIMP_MALE_A: Double = 0.64
    public const val TRIMP_MALE_B: Double = 1.92
    public const val TRIMP_FEMALE_A: Double = 0.86
    public const val TRIMP_FEMALE_B: Double = 1.67

    /** TRIMP is summed per minute of samples (1 Hz), as §10.1 recommends. */
    public const val SAMPLES_PER_MINUTE: Int = 60

    /** TSS scale: one hour at FTP = 100. */
    public const val TSS_SCALE: Double = 100.0

    /** Form above this is "fresh", below the negative bound "tired" (13-UI-SCREENS #20 status text). */
    public const val FORM_FRESH: Double = 5.0
    public const val FORM_TIRED: Double = -10.0

    /** Trend shown on the Training tab, and the period of the fitness change. */
    public const val TREND_WEEKS: Long = 8
    public const val FITNESS_CHANGE_WEEKS: Long = 4
    public const val DAYS_PER_WEEK: Long = 7
}

/** How a ride's load was computed (05-DATA-MODEL `load_method`; stored, never rename). */
public enum class LoadMethod(public val storageValue: String) {
    POWER("power"),
    HEART_RATE("hr"),
    ;

    public companion object {
        public fun fromStorage(value: String?): LoadMethod? = entries.firstOrNull { it.storageValue == value }
    }
}

/** Load of one ride. [isEstimatedInputs]: resting HR, HR max or sex were not in the profile. */
public data class RideLoad(
    val load: Double,
    val method: LoadMethod,
    val intensityFactor: Double?,
    val isEstimatedInputs: Boolean = false,
)

/** The rider values the load needs, with HR max from age when not entered (05-DATA-MODEL §3.8). */
public data class LoadInputs(val ftpW: Int?, val hrMaxBpm: Int?, val hrRestBpm: Int?, val sex: Sex?) {
    public companion object {
        public fun of(profile: RiderProfile, nowMs: Long): LoadInputs = LoadInputs(
            ftpW = profile.ftpW,
            hrMaxBpm = profile.hrMaxBpm ?: profile.ageYears(nowMs)?.let { ProfileDefaults.HR_MAX_AGE_BASE - it },
            hrRestBpm = profile.hrRestBpm,
            sex = profile.sex,
        )
    }
}

/**
 * Load per ride (12-FEATURE-SPECS §10.1): TSS-like from power-meter NP and FTP, otherwise Banister TRIMP from
 * heart rate summed per minute, otherwise none. Estimated power never counts.
 */
public object TrainingLoadCalculator {
    public fun calculate(track: RideTrack, totals: RideTotals, inputs: LoadInputs): RideLoad? =
        powerLoad(totals, inputs) ?: heartRateLoad(track, inputs)

    private fun powerLoad(totals: RideTotals, inputs: LoadInputs): RideLoad? {
        val np = totals.normalizedPowerW?.takeIf { it > 0 }
        val ftp = inputs.ftpW?.takeIf { it > 0 }
        if (np == null || ftp == null) return null
        val intensity = np.toDouble() / ftp
        val load = totals.movingS * np * intensity / (ftp * Units.SECONDS_PER_HOUR) * TrainingConfig.TSS_SCALE
        return RideLoad(load, LoadMethod.POWER, intensity)
    }

    private fun heartRateLoad(track: RideTrack, inputs: LoadInputs): RideLoad? {
        val hrMax = inputs.hrMaxBpm
        val hrRest = inputs.hrRestBpm ?: TrainingConfig.DEFAULT_HR_REST_BPM
        if (hrMax == null || hrMax <= hrRest) return null
        val isEstimated = inputs.hrRestBpm == null || inputs.sex == null
        return trimp(track, hrRest, hrMax, coefficients(inputs.sex))
            ?.let { RideLoad(it, LoadMethod.HEART_RATE, null, isEstimated) }
    }

    /** Banister TRIMP summed per minute of samples; null when the ride has no heart rate. */
    private fun trimp(track: RideTrack, hrRest: Int, hrMax: Int, coefficients: Pair<Double, Double>): Double? {
        val (a, b) = coefficients
        var load = 0.0
        var hasHeartRate = false
        var i = 0
        while (i < track.size) {
            val end = minOf(i + TrainingConfig.SAMPLES_PER_MINUTE, track.size)
            val minute = (i until end).mapNotNull { track.heartRateBpm(it) }
            if (minute.isNotEmpty()) {
                hasHeartRate = true
                val reserve = ((minute.average() - hrRest) / (hrMax - hrRest)).coerceIn(0.0, 1.0)
                load += minute.size.toDouble() / TrainingConfig.SAMPLES_PER_MINUTE * reserve * a * exp(b * reserve)
            }
            i = end
        }
        return load.takeIf { hasHeartRate }
    }

    /** Coefficients by sex; without it the average of both (§10.1, marked as an estimate). */
    private fun coefficients(sex: Sex?): Pair<Double, Double> = when (sex) {
        Sex.MALE -> TrainingConfig.TRIMP_MALE_A to TrainingConfig.TRIMP_MALE_B
        Sex.FEMALE -> TrainingConfig.TRIMP_FEMALE_A to TrainingConfig.TRIMP_FEMALE_B
        null -> (TrainingConfig.TRIMP_MALE_A + TrainingConfig.TRIMP_FEMALE_A) / 2 to
            (TrainingConfig.TRIMP_MALE_B + TrainingConfig.TRIMP_FEMALE_B) / 2
    }
}

/** Load and fitness of one local day (05-DATA-MODEL `daily_training`). */
public data class DailyTraining(
    val date: LocalDate,
    val load: Double,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
)

/**
 * Daily trend (12-FEATURE-SPECS §10.2): CTL += (load − CTL) / 42, ATL += (load − ATL) / 7, TSB = yesterday's
 * CTL − ATL. Computed from [from] to [to] given the day before (zero when there is none).
 */
public object TrainingTrend {
    public fun compute(
        from: LocalDate,
        to: LocalDate,
        dayBefore: DailyTraining?,
        loadByDate: Map<LocalDate, Double>,
    ): List<DailyTraining> {
        var ctl = dayBefore?.ctl ?: 0.0
        var atl = dayBefore?.atl ?: 0.0
        val days = mutableListOf<DailyTraining>()
        var date = from
        while (!date.isAfter(to)) {
            val load = loadByDate[date] ?: 0.0
            val tsb = ctl - atl
            ctl += (load - ctl) / TrainingConfig.CTL_DAYS
            atl += (load - atl) / TrainingConfig.ATL_DAYS
            days += DailyTraining(date, load, ctl, atl, tsb)
            date = date.plusDays(1)
        }
        return days
    }
}

/** Time in each heart-rate zone (Z1..Z5) of a ride, in seconds; empty without HR or HR max. */
public object ZoneTime {
    private const val HR_ZONES = 5

    public fun heartRate(track: RideTrack, hrMaxBpm: Int?): List<Long> {
        val seconds = LongArray(HR_ZONES)
        var hasAny = false
        for (i in 0 until track.size) {
            val zone = Zones.heartRate(track.heartRateBpm(i), hrMaxBpm) ?: continue
            seconds[zone - 1]++
            hasAny = true
        }
        return if (hasAny) seconds.toList() else emptyList()
    }
}
