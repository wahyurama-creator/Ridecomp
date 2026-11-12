package com.ridecomp.platform.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.ridecomp.core.config.BatteryModeConfig
import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.metrics.AltitudeSource
import com.ridecomp.core.engine.metrics.BarometricAltitude
import com.ridecomp.core.engine.recording.BatteryModePolicy
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RecorderOutput
import com.ridecomp.core.engine.recording.RecordingSession
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.core.util.onSuccess
import com.ridecomp.platform.device.AlertPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground service that owns the whole recording pipeline (03-ARCHITECTURE §4.1). It only wires
 * Android lifecycle and sources to the domain [RecordingSession] (ARCH-08).
 *
 * Resources (09 §1): GPS/barometer flows, wakelock and the foreground state exist only while a ride exists
 * and are released on save/discard and in [onDestroy] (BAT-01). All session calls run on one confined
 * dispatcher under [sessionLock] (CONC-01).
 */
class RecordingService : LifecycleService() {
    private val deps: RecordingDependencies by lazy {
        (application as RecordingDependenciesProvider).recordingDependencies
    }
    private val notification by lazy { RecordingNotification(this) }
    private val wakeLock by lazy { WakeLockHolder(this) }
    private val sessionLock = Mutex()
    private val gpsIntervalMs = MutableStateFlow(GpsConfig.INTERVAL_MS)
    private var serviceScope: CoroutineScope? = null
    private var session: RecordingSession? = null
    private val pipelineJobs = mutableListOf<Job>()
    private var pressureJob: Job? = null
    private var formatter = UnitFormatter(UnitSystem.METRIC)
    private var lastBatteryReadMs: Long? = null

    override fun onCreate() {
        super.onCreate()
        notification.createChannel()
        @OptIn(ExperimentalCoroutinesApi::class)
        val confined = deps.dispatchers.default.limitedParallelism(1)
        serviceScope = CoroutineScope(SupervisorJob() + confined)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // STB-03: foreground first, before any other work.
        enterForeground(deps.stateHolder.liveRide.value)
        val scope = serviceScope ?: return START_NOT_STICKY
        scope.launch { handle(intent) }
        return START_STICKY
    }

    override fun onDestroy() {
        stopPipeline()
        deps.alertPlayer.cancel()
        serviceScope?.cancel()
        serviceScope = null
        super.onDestroy()
    }

    private suspend fun handle(intent: Intent?) {
        when (intent?.action) {
            ServiceIntents.ACTION_START -> startRide()
            ServiceIntents.ACTION_RESUME_RECOVERED ->
                resumeRide(intent.getLongExtra(ServiceIntents.EXTRA_ACTIVITY_ID, ServiceIntents.NO_ID), null)
            ServiceIntents.ACTION_PERFORM -> intent.getStringExtra(ServiceIntents.EXTRA_RIDE_ACTION)
                ?.let { name -> RideAction.entries.firstOrNull { it.name == name } }
                ?.let { perform(it) }
            // Restarted by the system after the process was killed (START_STICKY): recover automatically.
            null -> deps.activityRepository.findRecoverable()?.let { resumeRide(it.id, it.status) } ?: stopRide()
            else -> deps.logger.warn(TAG) { "Unknown service action ignored" }
        }
        if (session == null) stopRide()
    }

    private suspend fun startRide() = sessionLock.withLock {
        if (session != null) return@withLock
        val settings = deps.settingsRepository.getRideSettings()
        formatter = UnitFormatter(settings.unitSystem)
        val newSession = deps.newSession()
        newSession.start(settings, altitudeSource(), deps.clock.nowMs()).onSuccess { output ->
            session = newSession
            publish(output)
            startPipeline()
        }
    }

    /** Recovers a ride: as RECORDING after a system restart of a recording ride, else PAUSED (user decides). */
    private suspend fun resumeRide(activityId: Long, storedStatus: ActivityStatus?) = sessionLock.withLock {
        if (session != null) return@withLock
        val activity = deps.activityRepository.get(activityId) ?: return@withLock
        val settings = deps.settingsRepository.getRideSettings()
        formatter = UnitFormatter(settings.unitSystem)
        val resumeAs = if (storedStatus == ActivityStatus.RECORDING) RideStatus.RECORDING else RideStatus.PAUSED
        val restored = deps.newSession()
        restored.resume(activity, settings, altitudeSource(), resumeAs, deps.clock.nowMs())
        session = restored
        restored.tick(deps.clock.nowMs())?.let(::publish)
        startPipeline()
    }

    private suspend fun perform(action: RideAction) {
        val isEnding = sessionLock.withLock {
            val active = session ?: return@withLock false
            active.perform(action, deps.clock.nowMs()).onSuccess(::publish)
            val ends = active.activityId == null
            if (ends) session = null
            ends
        }
        when {
            isEnding -> stopRide()
            // Ride over (summary screen): GPS, sensors and wakelock off now; stay foreground until save/discard.
            session?.status == RideStatus.FINISHED -> stopPipeline()
        }
    }

    private fun startPipeline() {
        val scope = serviceScope ?: return
        wakeLock.acquireOrRenew(deps.clock.nowMs())
        pipelineJobs += scope.launch { tickLoop() }
        pipelineJobs += scope.launch { collectLocation() }
        startPressure()
    }

    private suspend fun tickLoop() {
        while (currentCoroutineContext().isActive) {
            delay(GpsConfig.INTERVAL_MS)
            val now = deps.clock.nowMs()
            sessionLock.withLock {
                val active = session ?: return@withLock
                readBatteryIfDue(active, now)
                active.tick(now)?.let { output ->
                    handleOutput(active, output, now)
                    publish(output)
                }
                gpsIntervalMs.value = active.desiredGpsIntervalMs(now, GpsConfig.INTERVAL_MS)
            }
            wakeLock.acquireOrRenew(now)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun collectLocation() {
        gpsIntervalMs
            .flatMapLatest { interval -> deps.locationSource.fixes(interval) }
            .retryWhen { cause, _ ->
                // STB-04/05: permission revoked or GPS off — keep recording, retry later.
                deps.logger.warn(TAG, cause) { "Location unavailable, retrying" }
                delay(SOURCE_RETRY_MS)
                true
            }
            .collect { fix -> sessionLock.withLock { session?.onLocation(fix) } }
    }

    private fun startPressure() {
        val scope = serviceScope ?: return
        if (!deps.pressureSource.hasBarometer || pressureJob != null) return
        pressureJob = scope.launch {
            deps.pressureSource.readings().collect { reading ->
                sessionLock.withLock {
                    session?.onAltitude(BarometricAltitude.altitudeM(reading.pressureHpa), reading.timeMs)
                }
            }
        }
    }

    private suspend fun readBatteryIfDue(active: RecordingSession, now: Long) {
        val last = lastBatteryReadMs
        if (last != null && now - last < BatteryModeConfig.READ_INTERVAL_MS) return
        lastBatteryReadMs = now
        deps.batteryLevelSource.read()?.let { active.onBattery(it, now) }
    }

    private suspend fun handleOutput(active: RecordingSession, output: RecorderOutput, now: Long) {
        output.events.forEach { event -> alertFor(event.type)?.let(deps.alertPlayer::play) }
        val change = output.batteryChange ?: return
        if (!BatteryModePolicy.isBarometerAllowed(change.mode)) {
            pressureJob?.cancel()
            pressureJob = null
        }
        // TODO(T1.14): also write the temporary emergency FIT file here.
        if (change.shouldWriteEmergencyFit) active.emergencyCheckpoint(now)
    }

    private fun alertFor(type: RideEventType): AlertPattern? = when (type) {
        RideEventType.AUTO_PAUSE, RideEventType.AUTO_RESUME -> AlertPattern.SHORT
        RideEventType.REMINDER_DRINK, RideEventType.REMINDER_EAT -> AlertPattern.DOUBLE
        RideEventType.BATTERY_MODE_SAVER, RideEventType.BATTERY_MODE_CRITICAL,
        RideEventType.BATTERY_MODE_EMERGENCY,
        -> AlertPattern.LONG
        RideEventType.START, RideEventType.PAUSE, RideEventType.RESUME, RideEventType.STOP, RideEventType.GAP,
        RideEventType.LAP, RideEventType.SENSOR_LOST, RideEventType.SENSOR_BACK, RideEventType.REMINDER_ACK,
        -> null
    }

    private fun publish(output: RecorderOutput) {
        deps.stateHolder.publish(output.live, session?.activityId)
        notification.update(output.live, formatter)
    }

    private fun enterForeground(live: LiveRide) {
        val type = if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            RecordingNotification.NOTIFICATION_ID,
            notification.build(live, formatter),
            type,
        )
    }

    private fun stopPipeline() {
        pipelineJobs.forEach { it.cancel() }
        pipelineJobs.clear()
        pressureJob?.cancel()
        pressureJob = null
        wakeLock.release()
    }

    /** No ride left: release everything and leave the foreground (BAT-01). */
    private fun stopRide() {
        stopPipeline()
        deps.stateHolder.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun altitudeSource(): AltitudeSource =
        if (deps.pressureSource.hasBarometer) AltitudeSource.BAROMETER else AltitudeSource.GPS

    private companion object {
        const val TAG = "RecordingService"
        const val SOURCE_RETRY_MS = 5L * Units.MS_PER_SECOND
    }
}
