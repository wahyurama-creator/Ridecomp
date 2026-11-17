package com.ridecomp.platform.voice

import android.content.res.Resources
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.training.CoachAlert
import com.ridecomp.core.engine.voice.Announcement
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.platform.R
import kotlin.math.roundToInt

/** Spoken text of an [Announcement] in the app language (T7.1): short sentences, whole numbers. */
class AnnouncementSpeech(private val resources: Resources, private val units: UnitSystem) {
    private val format = UnitFormatter(units)
    private val isMetric get() = units == UnitSystem.METRIC

    fun text(announcement: Announcement): String = when (announcement) {
        is Announcement.Distance -> listOfNotNull(
            distance(announcement.distanceM),
            resources.getString(R.string.voice_time, duration(announcement.movingMs)),
            resources.getString(R.string.voice_avg_speed, format.speed(announcement.avgSpeedMps), speedUnit()),
            announcement.heartRateBpm?.let { resources.getString(R.string.voice_heart_rate, it) },
            announcement.avgPowerW?.let { resources.getQuantityString(R.plurals.voice_power, it, it) },
        ).joinToString(SENTENCE_GAP)
        is Announcement.LapDone -> listOfNotNull(
            resources.getString(R.string.voice_lap, announcement.number),
            resources.getString(R.string.voice_lap_time, duration(announcement.lap.movingS * Units.MS_PER_SECOND)),
            resources.getString(R.string.voice_avg_speed, format.speed(announcement.lap.avgSpeedMps), speedUnit()),
            announcement.lap.avgHeartRateBpm?.let { resources.getString(R.string.voice_heart_rate, it) },
        ).joinToString(SENTENCE_GAP)
        Announcement.DrinkReminder -> resources.getString(R.string.voice_drink)
        Announcement.EatReminder -> resources.getString(R.string.voice_eat)
    }

    /** Session coach (T7.4b): too hard for an easy session, or the planned time is done. */
    fun coach(alert: CoachAlert, intensity: Intensity): String = when (alert) {
        CoachAlert.TOO_HARD -> resources.getString(R.string.voice_too_hard, intensity.zone)
        CoachAlert.SESSION_DONE -> resources.getString(R.string.voice_session_done)
    }

    private fun distance(distanceM: Double): String {
        val whole = (distanceM / if (isMetric) Units.METERS_PER_KM else Units.METERS_PER_MILE).roundToInt()
        val plural = if (isMetric) R.plurals.voice_kilometers else R.plurals.voice_miles
        return resources.getQuantityString(plural, whole, whole)
    }

    private fun speedUnit(): String = resources.getString(if (isMetric) R.string.voice_kmh else R.string.voice_mph)

    /** "1 jam 5 menit", "25 menit 10 detik" (seconds only under an hour). */
    private fun duration(durationMs: Long): String {
        val totalS = durationMs / Units.MS_PER_SECOND
        val hours = (totalS / Units.SECONDS_PER_HOUR).toInt()
        val minutes = ((totalS % Units.SECONDS_PER_HOUR) / Units.SECONDS_PER_MINUTE).toInt()
        val seconds = (totalS % Units.SECONDS_PER_MINUTE).toInt()
        return buildList {
            if (hours > 0) add(resources.getQuantityString(R.plurals.voice_hours, hours, hours))
            if (minutes > 0 || hours > 0) add(resources.getQuantityString(R.plurals.voice_minutes, minutes, minutes))
            if (hours == 0) add(resources.getQuantityString(R.plurals.voice_seconds, seconds, seconds))
        }.joinToString(WORD_GAP)
    }

    private companion object {
        const val SENTENCE_GAP = ". "
        const val WORD_GAP = " "
    }
}
