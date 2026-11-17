package com.ridecomp.core.engine.training

import com.ridecomp.core.config.HeartRateConfig
import com.ridecomp.core.config.PowerConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.RideStatus
import kotlin.math.roundToInt

/** What the target is measured with (D-94): heart rate, else power, else by feel. */
public enum class TargetSource { HEART_RATE, POWER, FEEL }

public enum class ZoneState { BELOW, IN, ABOVE, UNKNOWN }

/** Where the rider is against today's planned session (T7.4b). */
public data class TargetStatus(
    val intensity: Intensity,
    val durationMs: Long,
    val source: TargetSource,
    /** Zone bounds in bpm or W; null = open end (zone 1 has no floor, zone 5 of HR no ceiling). */
    val lowValue: Int? = null,
    val highValue: Int? = null,
    val current: Int? = null,
    val state: ZoneState = ZoneState.UNKNOWN,
    val inZoneMs: Long = 0,
    val doneMs: Long = 0,
) {
    val isDone: Boolean get() = doneMs >= durationMs
}

public enum class CoachAlert {
    /** An easy session (zone 1–2) ridden too hard for a while. */
    TOO_HARD,

    /** The planned duration is reached. */
    SESSION_DONE,
}

public data class CoachResult(val status: TargetStatus, val alerts: List<CoachAlert>)

public object TargetConfig {
    /** Too hard for this long before the first alert: a short climb or sprint is fine. */
    public const val TOO_HARD_AFTER_MS: Long = 20_000

    /** Then at most once per this. */
    public const val TOO_HARD_REPEAT_MS: Long = 60_000

    /** Sessions up to this zone warn when ridden too hard (zone 2 discipline); harder ones only inform. */
    public const val WARN_UP_TO_ZONE: Int = 2

    private const val PERCENT = 100.0

    /** HR bounds of a zone (5 zones of % HR max, 05-DATA-MODEL §3.8). */
    public fun heartRateBounds(intensity: Intensity, hrMaxBpm: Int): Pair<Int?, Int?> =
        bounds(intensity, HeartRateConfig.ZONE_UPPER_PERCENT_HR_MAX, hrMaxBpm)

    /** Power bounds of a zone: the first five zones of % FTP (§3.9). */
    public fun powerBounds(intensity: Intensity, ftpW: Int): Pair<Int?, Int?> =
        bounds(intensity, PowerConfig.ZONE_UPPER_PERCENT_FTP, ftpW)

    private fun bounds(intensity: Intensity, uppers: List<Double>, base: Int): Pair<Int?, Int?> {
        val index = intensity.zone - 1
        val low = uppers.getOrNull(index - 1)?.let { (it * base / PERCENT).roundToInt() }
        val high = uppers.getOrNull(index)?.let { (it * base / PERCENT).roundToInt() }
        return low to high
    }
}

/**
 * Follows a ride against a planned session (T7.4b, D-94): which zone source is available each second,
 * time in zone and toward the duration (moving time), and alerts. Not thread-safe: owned by the
 * recording pipeline.
 */
public class TargetCoach(private val session: PlanSession, private val hrMaxBpm: Int?, private val ftpW: Int?) {
    private val durationMs = session.durationMin * Units.MS_PER_MINUTE
    private var inZoneMs = 0L
    private var lastMovingMs: Long? = null
    private var aboveSinceMs: Long? = null
    private var lastWarnMs: Long? = null
    private var isDoneAnnounced = false

    public fun onLive(live: LiveRide): CoachResult {
        val (source, current) = reading(live)
        val (low, high) = when (source) {
            TargetSource.HEART_RATE -> TargetConfig.heartRateBounds(session.intensity, requireNotNull(hrMaxBpm))
            TargetSource.POWER -> TargetConfig.powerBounds(session.intensity, requireNotNull(ftpW))
            TargetSource.FEEL -> null to null
        }
        val state = stateOf(current, low, high)
        val previousMoving = lastMovingMs ?: live.movingMs
        lastMovingMs = live.movingMs
        if (state == ZoneState.IN) inZoneMs += (live.movingMs - previousMoving).coerceAtLeast(0)
        val status =
            TargetStatus(session.intensity, durationMs, source, low, high, current, state, inZoneMs, live.movingMs)
        return CoachResult(status, alerts(status, live))
    }

    /** Heart rate when HR max is known and a strap sends, else power with FTP, else feel. */
    private fun reading(live: LiveRide): Pair<TargetSource, Int?> {
        val heartRate = live.sensors.heartRateBpm
        val power = live.sensors.power10sW ?: live.sensors.powerW
        return when {
            hrMaxBpm != null && heartRate != null -> TargetSource.HEART_RATE to heartRate
            ftpW != null && power != null -> TargetSource.POWER to power
            else -> TargetSource.FEEL to null
        }
    }

    private fun stateOf(current: Int?, low: Int?, high: Int?): ZoneState = when {
        current == null -> ZoneState.UNKNOWN
        low != null && current < low -> ZoneState.BELOW
        high != null && current > high -> ZoneState.ABOVE
        else -> ZoneState.IN
    }

    private fun alerts(status: TargetStatus, live: LiveRide): List<CoachAlert> = buildList {
        if (status.isDone && !isDoneAnnounced) {
            isDoneAnnounced = true
            add(CoachAlert.SESSION_DONE)
        }
        val isRiding = live.status == RideStatus.RECORDING
        val isTooHard =
            isRiding && status.state == ZoneState.ABOVE && session.intensity.zone <= TargetConfig.WARN_UP_TO_ZONE
        if (!isTooHard) {
            aboveSinceMs = null
            return@buildList
        }
        val since = aboveSinceMs ?: live.nowMs.also { aboveSinceMs = it }
        val lastWarn = lastWarnMs
        val isDue = live.nowMs - since >= TargetConfig.TOO_HARD_AFTER_MS &&
            (lastWarn == null || live.nowMs - lastWarn >= TargetConfig.TOO_HARD_REPEAT_MS)
        if (isDue) {
            lastWarnMs = live.nowMs
            add(CoachAlert.TOO_HARD)
        }
    }
}
