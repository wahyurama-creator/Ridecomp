package com.ridecomp.core.usecase

import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.config.PowerConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.engine.metrics.Zones
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.GuideRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.source.Clock
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** FTP estimate when the profile has none (T11.6, 05-DATA-MODEL §3.9). */
public object FtpConfig {
    /** FTP ≈ 95 % of the best 20-minute power. */
    public const val BEST_20_MIN_SHARE: Double = 0.95

    /** Only recent power counts: the last 6 weeks. */
    public const val BEST_WINDOW_MS: Long = 42L * 24 * 60 * 60 * 1_000

    /** Without power-meter rides: weight × these W/kg by the rider's level. */
    public const val W_PER_KG_BEGINNER: Double = 2.0
    public const val W_PER_KG_INTERMEDIATE: Double = 2.5
    public const val W_PER_KG_EXPERIENCED: Double = 3.0
}

/** Where the FTP of the power zones comes from. */
public enum class FtpSource { PROFILE, BEST_20_MIN, WEIGHT }

/** The FTP the power zones use, where it comes from, and the weight it may be based on. */
public data class FtpEstimate(val ftpW: Int, val source: FtpSource, val weightKg: Double)

/**
 * FTP for the power zones (T11.6): the profile's, else 95 % of the best 20-minute power-meter effort of the
 * last 6 weeks, else weight × W/kg of the rider's level (D-121). Estimated power is never used here: its
 * 20-minute bests are not stored (they come with the duration records of T11.8).
 */
public class EstimateFtpUseCase(
    private val profiles: ProfileRepository,
    private val efforts: BestEffortRepository,
    private val guide: GuideRepository,
    private val clock: Clock,
) {
    public suspend operator fun invoke(): FtpEstimate {
        val profile = profiles.get()
        val weight = profile.weightKg
        return profile.ftpW?.let { FtpEstimate(it, FtpSource.PROFILE, weight) }
            ?: fromBest20Min()?.let { FtpEstimate(it, FtpSource.BEST_20_MIN, weight) }
            ?: FtpEstimate((weight * wattsPerKg()).roundToInt(), FtpSource.WEIGHT, weight)
    }

    private suspend fun fromBest20Min(): Int? =
        efforts.bestSince(BestEffortKey.POWER_20_MIN, clock.nowMs() - FtpConfig.BEST_WINDOW_MS)
            ?.takeIf { it > 0 }
            ?.let { (it * FtpConfig.BEST_20_MIN_SHARE).roundToInt() }

    private suspend fun wattsPerKg(): Double = when (guide.observe().first().effectiveLevel) {
        RiderLevel.BEGINNER -> FtpConfig.W_PER_KG_BEGINNER
        RiderLevel.INTERMEDIATE -> FtpConfig.W_PER_KG_INTERMEDIATE
        RiderLevel.EXPERIENCED -> FtpConfig.W_PER_KG_EXPERIENCED
    }
}

/** Time in power zones Z1..Z7 while moving (T11.6); [isEstimatedPower] without a power meter. */
public data class PowerZones(val ftp: FtpEstimate, val isEstimatedPower: Boolean, val zones: List<ZoneTimeSlice>) {
    public val totalSeconds: Long get() = zones.sumOf { it.seconds }
}

/** The watt range of each zone for [ftpW], as `Zones.power` assigns them (upper bound inside the zone). */
public object PowerZoneBounds {
    public fun of(ftpW: Int): List<ZoneTimeSlice> {
        val tops = PowerConfig.ZONE_UPPER_PERCENT_FTP.map { floor(ftpW * it / Units.PERCENT).toInt() }
        return (0..tops.size).map { index ->
            ZoneTimeSlice(
                zone = index + 1,
                low = if (index == 0) 0 else tops[index - 1] + 1,
                high = tops.getOrNull(index),
                seconds = 0,
            )
        }
    }
}

/**
 * Seconds per heart-rate and power zone in one pass over the samples (1 per second), read in bounded
 * chunks (DATA-04). Heart rate counts every sample with a value (as `ZoneTime`); power only while moving,
 * from the power meter when the ride has one, else the estimate; samples without power are skipped.
 */
internal class ZoneCounter(private val hrMaxBpm: Int?, private val ftpW: Int, private val isMeter: Boolean) {
    val heartRate = LongArray(HEART_RATE_ZONES)
    val power = LongArray(PowerConfig.ZONE_UPPER_PERCENT_FTP.size + 1)
    var hasHeartRate = false
        private set
    var hasPower = false
        private set

    fun add(sample: RideSample) {
        Zones.heartRate(sample.heartRateBpm, hrMaxBpm)?.let {
            heartRate[it - 1]++
            hasHeartRate = true
        }
        val watts = if (isMeter) sample.powerW else sample.estPowerW
        if ((sample.speedMps ?: 0.0) > 0.0) {
            Zones.power(watts, ftpW)?.let {
                power[it - 1]++
                hasPower = true
            }
        }
    }

    suspend fun countAll(activities: ActivityRepository, activityId: Long): ZoneCounter = apply {
        var after = Long.MIN_VALUE
        while (true) {
            val chunk = activities.getSamples(activityId, after, ActivityConfig.TRACK_CHUNK_SIZE)
            if (chunk.isEmpty()) break
            chunk.forEach(::add)
            after = chunk.last().timeMs
        }
    }

    private companion object {
        const val HEART_RATE_ZONES = 5
    }
}
