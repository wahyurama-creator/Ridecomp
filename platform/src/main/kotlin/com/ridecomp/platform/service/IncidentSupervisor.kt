package com.ridecomp.platform.service

import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.safety.IncidentDetector
import com.ridecomp.core.engine.safety.IncidentState
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.RideStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wires incident detection into a ride (T2.16): accelerometer → [IncidentDetector] → countdown and SMS in
 * [com.ridecomp.core.engine.safety.IncidentManager]. Runs on the service's confined dispatcher, so the
 * detector is only touched by one coroutine (CONC-01). SMS sending suspends for seconds and therefore runs
 * in its own loop, never under the recording lock.
 *
 * Resources (09 §1): the accelerometer is collected only while a ride runs **and** the feature is armed;
 * its job is cancelled with the other pipeline jobs.
 */
internal class IncidentSupervisor(
    private val deps: RecordingDependencies,
    private val onStateChanged: (IncidentState) -> Unit,
) {
    private val detector = IncidentDetector()

    /** Last GPS fix, for the location link in the SMS. Null indoors. */
    var lastFix: LocationFix? = null

    /** Starts the incident jobs of a ride; the caller cancels them with the pipeline. */
    fun start(scope: CoroutineScope): List<Job> = listOf(
        scope.launch { collectAcceleration() },
        scope.launch { deps.incidentManager.state.collect(onStateChanged) },
        scope.launch { incidentLoop() },
    ).also { detector.reset() }

    /** Called from the 1 Hz tick with the new live state; detection only while riding (auto pause included). */
    fun onSecond(scope: CoroutineScope, live: LiveRide, activityId: Long?) {
        val isRiding = live.status == RideStatus.RECORDING || live.status == RideStatus.AUTO_PAUSED
        if (!isRiding || activityId == null) {
            detector.reset()
            return
        }
        detector.onSecond(live.nowMs, live.speedMps)?.let { detection ->
            scope.launch { deps.incidentManager.onDetected(activityId, detection, live.nowMs) }
        }
    }

    suspend fun onAction(action: IncidentAction, activityId: Long?) {
        if (action != IncidentAction.SOS) detector.reset()
        deps.incidentManager.onAction(action, activityId, deps.clock.nowMs(), lastFix)
    }

    /** Ride over: an open countdown is cancelled. */
    suspend fun close() {
        detector.reset()
        deps.incidentManager.close(deps.clock.nowMs())
    }

    private suspend fun collectAcceleration() {
        val source = deps.accelerometerSource
        if (!source.hasAccelerometer || !deps.incidentManager.isArmed()) return
        source.samples()
            .retryWhen { cause, _ ->
                deps.logger.warn(TAG, cause) { "Accelerometer unavailable, retrying" }
                delay(SOURCE_RETRY_MS)
                true
            }
            .collect(detector::onAcceleration)
    }

    private suspend fun incidentLoop() {
        while (currentCoroutineContext().isActive) {
            delay(GpsConfig.INTERVAL_MS)
            deps.incidentManager.tick(deps.clock.nowMs(), lastFix)
            // Loud alarm and vibration every second of the countdown (12-FEATURE-SPECS §5.3).
            if (deps.incidentManager.state.value is IncidentState.Countdown) deps.alertPlayer.alarm()
        }
    }

    private companion object {
        const val TAG = "IncidentSupervisor"
        const val SOURCE_RETRY_MS = 5L * Units.MS_PER_SECOND
    }
}
