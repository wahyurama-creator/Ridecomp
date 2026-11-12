package com.ridecomp.platform.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
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
        pauseResumeAction(live.status)?.let(builder::addAction)
        return builder.build()
    }

    /** Re-posts the notification only when its text changed (BAT-06). */
    fun update(live: LiveRide, formatter: UnitFormatter) {
        val content = contentText(live, formatter) + live.status + live.batteryMode
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

    private fun titleRes(live: LiveRide): Int = when (live.status) {
        RideStatus.RECORDING -> R.string.notification_title_recording
        RideStatus.PAUSED -> R.string.notification_title_paused
        RideStatus.AUTO_PAUSED -> R.string.notification_title_auto_paused
        RideStatus.FINISHED -> R.string.notification_title_finished
        RideStatus.IDLE -> R.string.notification_title_starting
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
    }
}
