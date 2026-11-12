package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.AutoPauseConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.geo.GeoMath
import kotlin.math.max

/** Input of one auto-pause evaluation, once per second. */
public data class MotionSample(
    val timeMs: Long,
    /** Best current speed (wheel sensor > GPS); null = no data (e.g. GPS lost in a tunnel). */
    val speedMps: Double?,
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val accuracyM: Double? = null,
    /** Speed comes from a wheel sensor: reliable at standstill, displacement check not needed. */
    val isFromWheelSensor: Boolean = false,
)

/** What the detector asks the ride to do. */
public enum class AutoPauseDecision { NONE, PAUSE, RESUME }

/** Thresholds of one activity profile (12-FEATURE-SPECS §1.1). */
public data class AutoPauseSettings(
    val isEnabled: Boolean = AutoPauseConfig.IS_ENABLED_DEFAULT,
    val speedThresholdMps: Double = AutoPauseConfig.SPEED_THRESHOLD_MPS,
    val delayS: Int = AutoPauseConfig.DELAY_DEFAULT_S,
    val idlePromptMin: Int = AutoPauseConfig.IDLE_PROMPT_DEFAULT_MIN,
)

/**
 * Auto pause/resume (12-FEATURE-SPECS §1, 05-DATA-MODEL §3.4).
 *
 * - Pause: speed < threshold and no meaningful displacement for `delayS`.
 * - Resume: speed > threshold + hysteresis for ≥ 2 s and, without a wheel sensor, displacement from the
 *   pause point > max(GPS accuracy, 10 m) — so GPS drift while standing never resumes the ride.
 * - Only used from RECORDING / AUTO_PAUSED; a manual pause is never resumed (caller does not ask).
 * - Missing speed data never causes a decision.
 */
public class AutoPauseDetector(private val settings: AutoPauseSettings) {
    private var slowSince: MotionSample? = null
    private var fastSinceMs: Long? = null
    private var pausePoint: MotionSample? = null
    private var autoPausedAtMs: Long? = null

    /** When the rider actually stopped for the last auto pause (start of the slow period). */
    public var lastStopStartedAtMs: Long? = null
        private set

    /** Evaluates [sample] while recording. Returns [AutoPauseDecision.PAUSE] when the ride should pause. */
    public fun onRecording(sample: MotionSample): AutoPauseDecision {
        val speed = sample.speedMps
        if (!settings.isEnabled || speed == null) return AutoPauseDecision.NONE
        val start = slowSince
        val decision = when {
            speed >= settings.speedThresholdMps -> null.also { slowSince = null }
            start == null -> null.also { slowSince = sample }
            hasMovedFrom(start, sample, AutoPauseConfig.MIN_RESUME_DISPLACEMENT_M) -> null.also { slowSince = sample }
            sample.timeMs - start.timeMs >= settings.delayS * Units.MS_PER_SECOND -> AutoPauseDecision.PAUSE
            else -> null
        }
        if (decision == AutoPauseDecision.PAUSE) enterPause(sample)
        return decision ?: AutoPauseDecision.NONE
    }

    /** Evaluates [sample] while auto-paused. Returns [AutoPauseDecision.RESUME] when riding resumed. */
    public fun onAutoPaused(sample: MotionSample): AutoPauseDecision {
        val speed = sample.speedMps
        val resumeSpeedMps = settings.speedThresholdMps + AutoPauseConfig.RESUME_HYSTERESIS_MPS
        // Missing data keeps the confirmation timer as is; a slow sample restarts it.
        if (speed != null && speed <= resumeSpeedMps) fastSinceMs = null
        val shouldResume = speed != null && speed > resumeSpeedMps && isResumeConfirmed(sample)
        if (shouldResume) reset()
        return if (shouldResume) AutoPauseDecision.RESUME else AutoPauseDecision.NONE
    }

    private fun isResumeConfirmed(sample: MotionSample): Boolean {
        val since = fastSinceMs ?: sample.timeMs.also { fastSinceMs = it }
        val isLongEnough = sample.timeMs - since >= AutoPauseConfig.RESUME_CONFIRM_MS
        val point = pausePoint
        val hasMoved = sample.isFromWheelSensor ||
            point == null ||
            hasMovedFrom(point, sample, AutoPauseConfig.MIN_RESUME_DISPLACEMENT_M)
        return isLongEnough && hasMoved
    }

    /** True once auto-paused longer than the idle prompt time ("Finish ride?", never auto-stop). */
    public fun isIdlePromptDue(nowMs: Long): Boolean {
        val pausedAt = autoPausedAtMs ?: return false
        return nowMs - pausedAt >= settings.idlePromptMin * Units.MS_PER_MINUTE
    }

    /** Clears all timers, e.g. after a manual pause/resume. */
    public fun reset() {
        slowSince = null
        fastSinceMs = null
        pausePoint = null
        autoPausedAtMs = null
    }

    private fun enterPause(sample: MotionSample) {
        lastStopStartedAtMs = slowSince?.timeMs ?: sample.timeMs
        pausePoint = slowSince?.takeIf { it.latDeg != null } ?: sample
        autoPausedAtMs = sample.timeMs
        slowSince = null
        fastSinceMs = null
    }

    private fun hasMovedFrom(origin: MotionSample, sample: MotionSample, minimumM: Double): Boolean {
        val from = origin.position()
        val to = sample.position()
        if (from == null || to == null) return false
        val thresholdM = max(sample.accuracyM ?: 0.0, minimumM)
        return GeoMath.distanceM(from.first, from.second, to.first, to.second) > thresholdM
    }

    private fun MotionSample.position(): Pair<Double, Double>? {
        val lat = latDeg
        val lon = lonDeg
        return if (lat != null && lon != null) lat to lon else null
    }
}
