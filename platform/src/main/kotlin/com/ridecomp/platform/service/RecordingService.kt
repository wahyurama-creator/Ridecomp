package com.ridecomp.platform.service

import android.content.Context
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
import com.ridecomp.core.engine.recording.RideSetup
import com.ridecomp.core.engine.route.RouteAlert
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.segments.SegmentAlert
import com.ridecomp.core.engine.sensors.ErgConfig
import com.ridecomp.core.engine.sensors.SensorReading
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.model.ageYears
import com.ridecomp.core.model.withBike
import com.ridecomp.core.model.withProfile
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.core.util.onSuccess
import com.ridecomp.platform.device.AlertPattern
import com.ridecomp.platform.locale.AppLocale
import com.ridecomp.platform.voice.RideVoiceCoach
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
    /** Notification texts in the chosen app language (D-88). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

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
    private var isGpsEnabled = true
    private val widget by lazy { ThrottledRideWidget(deps.rideWidget, deps.clock) }

    /** Voice announcements (T7.1) and the planned-session coach (T7.4b). */
    private val voiceCoach by lazy { RideVoiceCoach(this, deps.logger, deps.alertPlayer) }
    private val incidents by lazy {
        IncidentSupervisor(deps) { state ->
            deps.stateHolder.updateIncident(state)
            notification.update(deps.stateHolder.liveRide.value, formatter)
        }
    }

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
            ServiceIntents.ACTION_START -> startRide(
                intent.idExtra(ServiceIntents.EXTRA_PROFILE_ID),
                intent.idExtra(ServiceIntents.EXTRA_ROUTE_ID),
                intent.getBooleanExtra(ServiceIntents.EXTRA_WITH_SESSION, false),
            )
            ServiceIntents.ACTION_RESUME_RECOVERED ->
                resumeRide(intent.getLongExtra(ServiceIntents.EXTRA_ACTIVITY_ID, ServiceIntents.NO_ID), null)
            ServiceIntents.ACTION_PERFORM -> intent.getStringExtra(ServiceIntents.EXTRA_RIDE_ACTION)
                ?.let { name -> RideAction.entries.firstOrNull { it.name == name } }
                ?.let { perform(it) }
            ServiceIntents.ACTION_INCIDENT -> intent.getStringExtra(ServiceIntents.EXTRA_INCIDENT_ACTION)
                ?.let { name -> IncidentAction.entries.firstOrNull { it.name == name } }
                ?.let { incidents.onAction(it, session?.activityId) }
            // Restarted by the system after the process was killed (START_STICKY): recover automatically.
            null -> deps.activityRepository.findRecoverable()?.let { resumeRide(it.id, it.status) } ?: stopRide()
            else -> deps.logger.warn(TAG) { "Unknown service action ignored" }
        }
        if (session == null) stopRide()
    }

    private suspend fun startRide(profileId: Long?, routeId: Long?, withSession: Boolean) = sessionLock.withLock {
        if (session != null) return@withLock
        val settings = deps.settingsRepository.getRideSettings()
        val setup = rideSetup(settings, profileId, routeId)
        formatter = UnitFormatter(settings.unitSystem)
        startRideAids(settings, setup, deps.rideSession.forNewRide(withSession))
        val newSession = deps.newSession()
        newSession.start(setup, deps.clock.nowMs()).onSuccess { output ->
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
        val setup = rideSetup(settings, activity.profileId, activity.routeId)
        startRideAids(settings, setup, deps.rideSession.forRecoveredRide())
        val resumeAs = if (storedStatus == ActivityStatus.RECORDING) RideStatus.RECORDING else RideStatus.PAUSED
        val restored = deps.newSession()
        restored.resume(
            activity,
            setup,
            resumeAs,
            deps.clock.nowMs(),
        )
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
        val isFinished = session?.status == RideStatus.FINISHED
        if (isEnding || isFinished) incidents.close()
        when {
            isEnding -> stopRide()
            // Ride over (summary screen): GPS, sensors and wakelock off now; stay foreground until save/discard.
            isFinished -> stopPipeline()
        }
    }

    private fun startPipeline() {
        val scope = serviceScope ?: return
        wakeLock.acquireOrRenew(deps.clock.nowMs())
        pipelineJobs += scope.launch { tickLoop() }
        pipelineJobs += scope.launch { collectSensors() }
        pipelineJobs += incidents.start(scope)
        // Indoor (GPS off): no location and no barometer, whose drift would invent climbing.
        if (isGpsEnabled) {
            pipelineJobs += scope.launch { collectLocation() }
            startPressure()
        }
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
                    serviceScope?.let { incidents.onSecond(it, output.live, active.activityId) }
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
            .collect { fix ->
                incidents.lastFix = fix
                sessionLock.withLock { session?.onLocation(fix) }
            }
    }

    /**
     * Paired auto-connect sensors (T2.3). The connector reconnects by itself; a failure here (Bluetooth
     * permission revoked, adapter gone) never stops the ride (STB-04): it is retried later.
     */
    private suspend fun collectSensors() {
        val sensors = deps.sensorRepository.getAutoConnect()
        if (sensors.isEmpty()) return
        deps.sensorConnector.readings(sensors)
            .retryWhen { cause, _ ->
                deps.logger.warn(TAG, cause) { "Sensors unavailable, retrying" }
                delay(SOURCE_RETRY_MS)
                true
            }
            .collect { reading ->
                sessionLock.withLock { session?.onSensor(reading) }
                if (reading is SensorReading.Battery) {
                    reading.address?.let { deps.sensorRepository.updateBattery(it, reading.percent) }
                }
            }
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
        voiceCoach.onOutput(output)
        output.events.forEach { event -> alertFor(event.type)?.let(deps.alertPlayer::play) }
        // Off route: vibration and a beep (N-04), heard with the phone in a pocket or bag.
        if (output.events.any { it.type == RideEventType.OFF_ROUTE }) deps.alertPlayer.beep()
        output.live.route?.alert?.let { deps.alertPlayer.play(alertFor(it)) }
        output.live.segment?.alert?.let { deps.alertPlayer.play(alertFor(it)) }
        val change = output.batteryChange ?: return
        if (!BatteryModePolicy.isBarometerAllowed(change.mode)) {
            pressureJob?.cancel()
            pressureJob = null
        }
        if (change.shouldWriteEmergencyFit) {
            active.emergencyCheckpoint(now)
            active.activityId?.let { id -> deps.exportFit(id, isEmergency = true) }
        }
    }

    private fun alertFor(type: RideEventType): AlertPattern? = when (type) {
        RideEventType.AUTO_PAUSE, RideEventType.AUTO_RESUME -> AlertPattern.SHORT
        RideEventType.REMINDER_DRINK, RideEventType.REMINDER_EAT -> AlertPattern.DOUBLE
        RideEventType.BATTERY_MODE_SAVER, RideEventType.BATTERY_MODE_CRITICAL,
        RideEventType.BATTERY_MODE_EMERGENCY,
        -> AlertPattern.LONG
        RideEventType.SENSOR_LOST -> AlertPattern.DOUBLE
        RideEventType.SENSOR_BACK -> AlertPattern.SHORT
        RideEventType.OFF_ROUTE -> AlertPattern.LONG
        RideEventType.BACK_ON_ROUTE -> AlertPattern.DOUBLE
        RideEventType.START, RideEventType.PAUSE, RideEventType.RESUME, RideEventType.STOP, RideEventType.GAP,
        RideEventType.LAP, RideEventType.REMINDER_ACK, RideEventType.INCIDENT_DETECTED,
        RideEventType.INCIDENT_CANCELLED, RideEventType.SOS_SENT, RideEventType.SMS_FAILED,
        -> null
    }

    /** Live segment cues (§9.3): a new record buzzes twice. */
    private fun alertFor(alert: SegmentAlert): AlertPattern = when (alert) {
        SegmentAlert.STARTED, SegmentAlert.FINISHED -> AlertPattern.SHORT
        SegmentAlert.NEW_RECORD -> AlertPattern.DOUBLE
    }

    /** Turn and climb alerts (12-FEATURE-SPECS §6.2, §7.2): the near turn buzzes twice. */
    private fun alertFor(alert: RouteAlert): AlertPattern = when (alert) {
        RouteAlert.TURN_FAR, RouteAlert.CLIMB_AHEAD -> AlertPattern.SHORT
        RouteAlert.TURN_NEAR -> AlertPattern.DOUBLE
    }

    private fun publish(output: RecorderOutput) {
        val live = output.live.copy(incident = deps.incidentManager.state.value, target = voiceCoach.target)
        deps.stateHolder.publish(live, session?.activityId)
        notification.update(live, formatter)
        widget.onRideUpdate()
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
        voiceCoach.shutdown()
        // The trainer is free again once the ride is over (T9.1).
        deps.trainerControl.setTarget(null)
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
        widget.now()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * General settings and rider with the activity profile (12-FEATURE-SPECS §4) and the picked bike's
     * weight and wheel size (D-89) applied.
     */
    private suspend fun rideSetup(settings: RideSettings, profileId: Long?, routeId: Long?): RideSetup {
        val bike = deps.getSelectedBike()
        val rider = deps.profileRepository.get().let { profile -> bike?.let(profile::withBike) ?: profile }
        val activityProfile = deps.getRideProfile(profileId)
        isGpsEnabled = activityProfile?.isGpsEnabled != false
        // Indoor rides have no position to follow a route with.
        val followedId = routeId?.takeIf { isGpsEnabled }
        val guidance = followedId?.let { deps.getRouteGuidance(it) }
        return RideSetup(
            settings = activityProfile?.let(settings::withProfile) ?: settings,
            altitudeSource = altitudeSource(),
            profile = activityProfile?.let(rider::withProfile) ?: rider,
            ageYears = rider.ageYears(deps.clock.nowMs()),
            profileId = activityProfile?.id,
            bikeId = bike?.id,
            isGpsEnabled = isGpsEnabled,
            routeId = routeId,
            routePoints = followedId?.let { deps.routeRepository.getPoints(it) }.orEmpty(),
            routeClimbs = guidance?.climbs.orEmpty(),
            routeTurns = guidance?.turns.orEmpty(),
            // Indoor rides have no position to cross a segment with.
            segments = if (isGpsEnabled) deps.getLiveSegments() else emptyList(),
        )
    }

    /**
     * Voice and session coach (T7.1, T7.4b), and the smart trainer's first ERG target: today's session
     * zone with FTP, else a gentle default the rider adjusts (T9.1).
     */
    private fun startRideAids(settings: RideSettings, setup: RideSetup, planned: PlanSession?) {
        voiceCoach.setUp(settings, setup, planned)
        deps.trainerControl.setTarget(ErgConfig.startWatts(planned, setup.profile.ftpW) ?: ErgConfig.DEFAULT_W)
    }

    private fun altitudeSource(): AltitudeSource =
        if (deps.pressureSource.hasBarometer) AltitudeSource.BAROMETER else AltitudeSource.GPS

    private fun Intent.idExtra(name: String): Long? =
        getLongExtra(name, ServiceIntents.NO_ID).takeIf { it != ServiceIntents.NO_ID }

    private companion object {
        const val TAG = "RecordingService"
        const val SOURCE_RETRY_MS = 5L * Units.MS_PER_SECOND
    }
}
