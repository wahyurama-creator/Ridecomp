package com.ridecomp.platform.voice

import android.content.Context
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.engine.recording.RecorderOutput
import com.ridecomp.core.engine.recording.RideSetup
import com.ridecomp.core.engine.training.TargetCoach
import com.ridecomp.core.engine.training.TargetStatus
import com.ridecomp.core.engine.voice.AnnouncementTracker
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.source.Logger
import com.ridecomp.platform.device.AlertPattern
import com.ridecomp.platform.device.AlertPlayer

/**
 * What the ride says and buzzes beyond the recorder's own alerts, owned by the recording service:
 * voice announcements (T7.1) and the planned-session coach (T7.4b). [setUp] at ride start or recovery,
 * [onOutput] every second, [shutdown] when the pipeline stops (09 §1, releases text-to-speech).
 */
class RideVoiceCoach(private val context: Context, logger: Logger, private val alertPlayer: AlertPlayer) {
    private val voice = VoiceAnnouncer(context, logger)
    private var announcements: AnnouncementTracker? = null
    private var speech: AnnouncementSpeech? = null
    private var coach: TargetCoach? = null

    /** Where the ride is against today's session; null without one. */
    var target: TargetStatus? = null
        private set

    /** HR max from the profile or 220 − age, FTP from the profile (05-DATA-MODEL §3.8, §3.9). */
    fun setUp(settings: RideSettings, setup: RideSetup, planned: PlanSession?) {
        val hrMax = setup.profile.hrMaxBpm ?: setup.ageYears?.let { ProfileDefaults.HR_MAX_AGE_BASE - it }
        coach = planned?.let { TargetCoach(it, hrMax, setup.profile.ftpW) }
        target = null
        announcements = AnnouncementTracker(settings.voice, settings.unitSystem).takeIf { settings.voice.isEnabled }
        speech =
            AnnouncementSpeech(context.resources, settings.unitSystem).takeIf { announcements != null || coach != null }
        if (speech != null) voice.start(context.resources.configuration.locales[0])
    }

    fun onOutput(output: RecorderOutput) {
        val words = speech
        announcements?.onOutput(output)?.forEach { announcement -> words?.let { voice.speak(it.text(announcement)) } }
        coach?.onLive(output.live)?.let { result ->
            target = result.status
            result.alerts.forEach { alert ->
                alertPlayer.play(AlertPattern.DOUBLE)
                words?.let { voice.speak(it.coach(alert, result.status.intensity)) }
            }
        }
    }

    fun shutdown() {
        voice.shutdown()
        announcements = null
        speech = null
        coach = null
        target = null
    }
}
