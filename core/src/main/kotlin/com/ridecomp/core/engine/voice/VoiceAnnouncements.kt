package com.ridecomp.core.engine.voice

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RecorderOutput
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.UnitSystem
import kotlin.math.floor

/** Voice announcements setting (T7.1, D-91): every [everyUnits] km (or mi), at laps, reminders. */
public data class VoiceSettings(
    /** Spoken every this many km (mi in imperial); null = off. */
    val everyUnits: Int? = null,
    val isLapEnabled: Boolean = true,
) {
    public val isEnabled: Boolean get() = everyUnits != null

    public companion object {
        /** Choices offered in Settings, in km or mi. */
        public val EVERY_CHOICES: List<Int> = listOf(1, 5, 10)
    }
}

/** What to say; the platform turns it into localized speech. */
public sealed interface Announcement {
    /** Every N km: totals so far. */
    public data class Distance(
        val distanceM: Double,
        val movingMs: Long,
        val avgSpeedMps: Double,
        val heartRateBpm: Int?,
        val avgPowerW: Int?,
    ) : Announcement

    /** A lap just closed. */
    public data class LapDone(val number: Int, val lap: Lap) : Announcement

    public data object DrinkReminder : Announcement

    public data object EatReminder : Announcement
}

/**
 * Turns each second of the recorder into announcements (T7.1). The first observation only sets the
 * starting point, so a resumed or recovered ride does not announce the kilometres already ridden.
 * Not thread-safe: owned by the recording pipeline.
 */
public class AnnouncementTracker(private val settings: VoiceSettings, units: UnitSystem) {
    private val stepM: Double? = settings.everyUnits?.let { every ->
        every * if (units == UnitSystem.METRIC) Units.METERS_PER_KM else Units.METERS_PER_MILE
    }
    private var lastStep: Long? = null

    public fun onOutput(output: RecorderOutput): List<Announcement> {
        if (!settings.isEnabled) return emptyList()
        return buildList {
            distanceAnnouncement(output.live)?.let(::add)
            if (settings.isLapEnabled) output.laps.forEach { add(Announcement.LapDone(it.index + 1, it)) }
            output.events.forEach { event ->
                when (event.type) {
                    RideEventType.REMINDER_DRINK -> add(Announcement.DrinkReminder)
                    RideEventType.REMINDER_EAT -> add(Announcement.EatReminder)
                    else -> Unit
                }
            }
        }
    }

    private fun distanceAnnouncement(live: LiveRide): Announcement? {
        val step = stepM ?: return null
        val current = floor(live.distanceM / step).toLong()
        val previous = lastStep
        lastStep = current
        val isNewStep = previous != null && current > previous && live.status == RideStatus.RECORDING
        return if (isNewStep) {
            Announcement.Distance(
                distanceM = current * step,
                movingMs = live.movingMs,
                avgSpeedMps = live.avgSpeedMps,
                heartRateBpm = live.sensors.avgHeartRateBpm,
                avgPowerW = live.sensors.avgPowerW,
            )
        } else {
            null
        }
    }
}
