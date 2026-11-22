package com.ridecomp.core.model

/**
 * How experienced the rider is (Phase 4G, owner request 2026-11-13). Chosen in onboarding, changeable in
 * Settings; decides how many tips are shown. Stored values never change.
 */
public enum class RiderLevel(public val storageValue: String) {
    /** New to cycling or < 1 year: every tip. */
    BEGINNER("beginner"),

    /** 1–3 years, rides regularly: tips for the advanced features only. */
    INTERMEDIATE("intermediate"),

    /** > 3 years or structured training: no tips (Help stays available). */
    EXPERIENCED("experienced"),
    ;

    public companion object {
        public fun fromStorage(value: String?): RiderLevel? = entries.firstOrNull { it.storageValue == value }
    }
}

/** Topics of the Help screen, in reading order (TG.2). */
public enum class GuideTopic {
    FIRST_RIDE,
    ROUTES_GPX,
    NAVIGATION,
    OFFLINE_MAPS,
    SENSORS,

    /** Heart rate from a watch: turning on HR broadcast per brand (T12.3). */
    WATCHES,
    EXPORT_STRAVA,
    BACKUP,
    RECORDS_SEGMENTS,
    TRAINING,
    SHARE_CARD,
    SAFETY,
    BATTERY,

    /** GPS lost in a forest or tunnel: what the recording does and how to keep the signal (D-149). */
    GPS_SIGNAL,
}

/**
 * Dismissible tips on the screens that need them (TG.3). [isAdvanced] tips also show to intermediate riders.
 * [storageKey] is stored when dismissed; never rename.
 */
public enum class GuideTip(
    public val storageKey: String,
    public val topic: GuideTopic,
    public val isAdvanced: Boolean,
) {
    FIRST_RIDE("first_ride", GuideTopic.FIRST_RIDE, isAdvanced = false),
    ROUTES("routes", GuideTopic.ROUTES_GPX, isAdvanced = false),
    SENSORS("sensors", GuideTopic.SENSORS, isAdvanced = false),
    EXPORT("export", GuideTopic.EXPORT_STRAVA, isAdvanced = true),
    BACKUP("backup", GuideTopic.BACKUP, isAdvanced = true),
    OFFLINE_MAPS("offline_maps", GuideTopic.OFFLINE_MAPS, isAdvanced = true),

    /**
     * "Pasang sensor detak jantung" on a ride without heart rate (T11.5). Shown at every level until closed,
     * so the detail page checks only [GuidePreferences.dismissed].
     */
    HEART_RATE_ZONES("heart_rate_zones", GuideTopic.SENSORS, isAdvanced = false),
}

/** The rider's level and the tips already closed. A rider who never chose a level is treated as intermediate. */
public data class GuidePreferences(val level: RiderLevel? = null, val dismissed: Set<GuideTip> = emptySet()) {
    public val effectiveLevel: RiderLevel get() = level ?: RiderLevel.INTERMEDIATE

    public fun shows(tip: GuideTip): Boolean = tip !in dismissed &&
        when (effectiveLevel) {
            RiderLevel.BEGINNER -> true
            RiderLevel.INTERMEDIATE -> tip.isAdvanced
            RiderLevel.EXPERIENCED -> false
        }
}
