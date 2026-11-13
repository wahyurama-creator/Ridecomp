package com.ridecomp.platform.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.safety.IncidentState
import com.ridecomp.core.model.BatteryMode
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.platform.R

/**
 * Foreground notification of the recording (T1.10): time, distance, speed and a Pause/Resume action.
 * It is also the data display when the screen mode is "off" (02-DESIGN §6). Updated at most once per
 * second and only when the text changes, silently (BAT-06).
 */
internal class RecordingNotification(private val context: Context) {
    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private var lastContent: String? = null

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_recording),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_recording_description)
            setShowBadge(false)
        }
        manager?.createNotificationChannel(channel)
    }

    fun build(live: LiveRide, formatter: UnitFormatter): Notification {
        val content = contentText(live, formatter)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ride)
            .setContentTitle(context.getString(titleRes(live)))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
        val incidentActions = incidentActions(live.incident)
        if (incidentActions.isEmpty()) pauseResumeAction(live.status)?.let(builder::addAction)
        incidentActions.forEach(builder::addAction)
        return builder.build()
    }

    /** Re-posts the notification only when its text changed (BAT-06). */
    fun update(live: LiveRide, formatter: UnitFormatter) {
        val content = contentText(live, formatter) + live.status + live.batteryMode + live.incident::class.simpleName
        if (content == lastContent) return
        lastContent = content
        manager?.notify(NOTIFICATION_ID, build(live, formatter))
    }

    private fun contentText(live: LiveRide, formatter: UnitFormatter): String {
        val battery = when (live.batteryMode) {
            BatteryMode.NORMAL -> null
            BatteryMode.SAVER -> context.getString(R.string.notification_battery_saver)
            BatteryMode.CRITICAL, BatteryMode.EMERGENCY -> context.getString(R.string.notification_battery_critical)
        }
        return listOfNotNull(
            formatter.duration(live.movingMs),
            "${formatter.distance(live.distanceM)} ${formatter.distanceUnit}",
            "${formatter.speed(live.speedMps)} ${formatter.speedUnit}",
            battery,
        ).joinToString(SEPARATOR)
    }

    private fun titleRes(live: LiveRide): Int = when (live.incident) {
        is IncidentState.Countdown -> R.string.notification_title_incident
        is IncidentState.Alerted -> R.string.notification_title_incident_sent
        IncidentState.None -> statusTitleRes(live.status)
    }

    private fun statusTitleRes(status: RideStatus): Int = when (status) {
        RideStatus.RECORDING -> R.string.notification_title_recording
        RideStatus.PAUSED -> R.string.notification_title_paused
        RideStatus.AUTO_PAUSED -> R.string.notification_title_auto_paused
        RideStatus.FINISHED -> R.string.notification_title_finished
        RideStatus.IDLE -> R.string.notification_title_starting
    }

    /** Incident buttons, so the countdown can be stopped from the lock screen (12-FEATURE-SPECS §5.3). */
    private fun incidentActions(state: IncidentState): List<NotificationCompat.Action> = when (state) {
        IncidentState.None -> emptyList()
        is IncidentState.Countdown -> listOf(
            incidentAction(IncidentAction.CANCEL, R.string.notification_action_incident_ok),
            incidentAction(IncidentAction.SEND_NOW, R.string.notification_action_incident_send),
        )
        is IncidentState.Alerted -> listOf(
            incidentAction(IncidentAction.SAFE, R.string.notification_action_incident_safe),
        )
    }

    private fun incidentAction(action: IncidentAction, label: Int): NotificationCompat.Action {
        val intent = PendingIntent.getService(
            context,
            INCIDENT_REQUEST_BASE + action.ordinal,
            ServiceIntents.incident(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(null, context.getString(label), intent)
    }

    private fun pauseResumeAction(status: RideStatus): NotificationCompat.Action? {
        val (action, label) = when (status) {
            RideStatus.RECORDING, RideStatus.AUTO_PAUSED -> RideAction.PAUSE to R.string.notification_action_pause
            RideStatus.PAUSED -> RideAction.RESUME to R.string.notification_action_resume
            RideStatus.IDLE, RideStatus.FINISHED -> return null
        }
        val intent = PendingIntent.getService(
            context,
            action.ordinal,
            ServiceIntents.perform(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(null, context.getString(label), intent)
    }

    /** Opens the app's launcher activity; explicit and immutable (SEC-PLT-02). */
    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "ride_recording"
        const val NOTIFICATION_ID = 1_001
        private const val SEPARATOR = " · "

        /** Request codes of incident actions, apart from the pause/resume ones (RideAction ordinals). */
        private const val INCIDENT_REQUEST_BASE = 100
    }
}
