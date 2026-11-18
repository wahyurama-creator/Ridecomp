package com.ridecomp.core.model

import com.ridecomp.core.config.ProfileDefaults

/** Built-in profile kinds (12-FEATURE-SPECS §4); CUSTOM = a copy made by the rider. */
public enum class ActivityKind { ROAD, GRAVEL, MTB, INDOOR, COMMUTE, FOLDING, CUSTOM }

/**
 * FIT `sport` / `sub_sport` enum values (FIT SDK Profile.xlsx, types `sport` and `sub_sport`). Only the
 * values RideComp writes; to be re-checked with FitCSVTool in the Phase Gate (07-TEST-PLAN).
 */
public object FitSportValues {
    public const val SPORT_CYCLING: Int = 2
    public const val SUB_SPORT_GENERIC: Int = 0
    public const val SUB_SPORT_INDOOR_CYCLING: Int = 6
    public const val SUB_SPORT_ROAD: Int = 7
    public const val SUB_SPORT_MOUNTAIN: Int = 8
    public const val SUB_SPORT_GRAVEL_CYCLING: Int = 46
    public const val SUB_SPORT_COMMUTING: Int = 48
}

/**
 * An activity profile (05-DATA-MODEL `activity_profiles`, 12-FEATURE-SPECS §4). Fields that are null use
 * the general ride settings / data pages, so changing a general setting changes every profile that does
 * not override it (decision D-32).
 */
public data class ActivityProfile(
    val id: Long = 0,
    /** Rider-chosen name; blank for built-ins, which show their localized kind name. */
    val name: String = "",
    val kind: ActivityKind,
    val isGpsEnabled: Boolean = true,
    val dataPages: DataPagesLayout? = null,
    val isAutoPauseEnabled: Boolean? = null,
    /** Auto lap distance; null = general setting, [AUTO_LAP_OFF] = off for this profile. */
    val autoLapM: Double? = null,
    val screenMode: ScreenMode? = null,
    val surface: Surface = Surface.ROAD,
    val ridingPosition: RidingPosition = RidingPosition.HOODS,
    val fitSport: Int = FitSportValues.SPORT_CYCLING,
    val fitSubSport: Int = FitSportValues.SUB_SPORT_GENERIC,
    val sortOrder: Int = 0,
) {
    public val isBuiltIn: Boolean get() = kind != ActivityKind.CUSTOM

    public companion object {
        /** Stored auto-lap value meaning "off for this profile" (null means "general setting"). */
        public const val AUTO_LAP_OFF: Double = 0.0
    }
}

/** General ride settings with this profile's overrides applied (12-FEATURE-SPECS §4). */
public fun RideSettings.withProfile(profile: ActivityProfile): RideSettings = copy(
    isAutoPauseEnabled = profile.isAutoPauseEnabled ?: isAutoPauseEnabled,
    autoLapM = when (val lap = profile.autoLapM) {
        null -> autoLapM
        ActivityProfile.AUTO_LAP_OFF -> null
        else -> lap
    },
    screenMode = profile.screenMode ?: screenMode,
)

/** The rider with this profile's surface and position, for estimated power (05-DATA-MODEL §3.7). */
public fun RiderProfile.withProfile(profile: ActivityProfile): RiderProfile =
    copy(surface = profile.surface, ridingPosition = profile.ridingPosition)

/**
 * Metric keys used by built-in pages. They must match the ride screen metric keys (checked by a test in
 * :feature-ride); unknown keys would simply be dropped by `sanitized()`.
 */
public object BuiltInMetricKeys {
    public const val SPEED: String = "speed"
    public const val DISTANCE: String = "distance"
    public const val MOVING_TIME: String = "moving_time"
    public const val CLOCK: String = "clock"
    public const val HEART_RATE: String = "heart_rate"
    public const val HEART_RATE_ZONE: String = "heart_rate_zone"
    public const val ALTITUDE: String = "altitude"
    public const val ASCENT: String = "ascent"
    public const val GRADE: String = "grade"
    public const val CADENCE: String = "cadence"

    public val ALL: List<String> =
        listOf(SPEED, DISTANCE, MOVING_TIME, CLOCK, HEART_RATE, HEART_RATE_ZONE, ALTITUDE, ASCENT, GRADE, CADENCE)
}

/** The built-in profiles, seeded once into the database (folding bike added in schema v10, D-102). */
public object BuiltInProfiles {
    public val ALL: List<ActivityProfile> = listOf(
        ActivityProfile(kind = ActivityKind.ROAD, fitSubSport = FitSportValues.SUB_SPORT_ROAD, sortOrder = 0),
        ActivityProfile(
            kind = ActivityKind.GRAVEL,
            surface = Surface.GRAVEL,
            fitSubSport = FitSportValues.SUB_SPORT_GRAVEL_CYCLING,
            sortOrder = 1,
        ),
        ActivityProfile(
            kind = ActivityKind.MTB,
            surface = Surface.GRAVEL,
            ridingPosition = RidingPosition.TOPS,
            fitSubSport = FitSportValues.SUB_SPORT_MOUNTAIN,
            dataPages = MTB_PAGES,
            sortOrder = 2,
        ),
        // Indoor: GPS off, speed/distance and auto pause from the wheel sensor, FIT without positions.
        ActivityProfile(
            kind = ActivityKind.INDOOR,
            isGpsEnabled = false,
            fitSubSport = FitSportValues.SUB_SPORT_INDOOR_CYCLING,
            dataPages = INDOOR_PAGES,
            sortOrder = 3,
        ),
        ActivityProfile(
            kind = ActivityKind.COMMUTE,
            ridingPosition = RidingPosition.TOPS,
            fitSubSport = FitSportValues.SUB_SPORT_COMMUTING,
            dataPages = COMMUTE_PAGES,
            sortOrder = 4,
        ),
        // Folding bike (D-102): upright position on small wheels; FIT has no folding sub sport.
        ActivityProfile(
            kind = ActivityKind.FOLDING,
            ridingPosition = RidingPosition.TOPS,
            fitSubSport = FitSportValues.SUB_SPORT_GENERIC,
            sortOrder = 5,
        ),
    )

    /** The five seeded by the v2→v3 migration; later kinds come with their own migration. */
    public val FIRST: List<ActivityProfile> = ALL.filter { it.kind != ActivityKind.FOLDING }

    /** Added by the v9→v10 migration. */
    public val FOLDING: List<ActivityProfile> = ALL.filter { it.kind == ActivityKind.FOLDING }
}

/** The wheel a new bike of this kind starts with (D-102): small wheels for a folding bike. */
public fun ActivityKind.defaultWheelMm(): Int = when (this) {
    ActivityKind.FOLDING -> ProfileDefaults.FOLDING_WHEEL_CIRCUMFERENCE_MM
    ActivityKind.ROAD, ActivityKind.GRAVEL, ActivityKind.MTB, ActivityKind.INDOOR, ActivityKind.COMMUTE,
    ActivityKind.CUSTOM,
    -> ProfileDefaults.WHEEL_CIRCUMFERENCE_MM
}

/** MTB: elevation and heart rate first (12-FEATURE-SPECS §4). */
private val MTB_PAGES = DataPagesLayout(
    listOf(
        DataPageLayout(
            listOf(
                BuiltInMetricKeys.GRADE,
                BuiltInMetricKeys.HEART_RATE,
                BuiltInMetricKeys.ASCENT,
                BuiltInMetricKeys.ALTITUDE,
                BuiltInMetricKeys.SPEED,
                BuiltInMetricKeys.DISTANCE,
            ),
        ),
        DataPageLayout(
            listOf(
                BuiltInMetricKeys.HEART_RATE,
                BuiltInMetricKeys.HEART_RATE_ZONE,
                BuiltInMetricKeys.MOVING_TIME,
                BuiltInMetricKeys.CLOCK,
            ),
        ),
    ),
)

/** Commute: speed, distance, clock (12-FEATURE-SPECS §4). */
private val COMMUTE_PAGES = DataPagesLayout(
    listOf(
        DataPageLayout(
            listOf(BuiltInMetricKeys.SPEED, BuiltInMetricKeys.DISTANCE, BuiltInMetricKeys.CLOCK),
            isFirstFullWidth = true,
        ),
    ),
)

/** Indoor: no altitude/grade (no GPS); speed, cadence and heart rate. */
private val INDOOR_PAGES = DataPagesLayout(
    listOf(
        DataPageLayout(
            listOf(
                BuiltInMetricKeys.SPEED,
                BuiltInMetricKeys.DISTANCE,
                BuiltInMetricKeys.MOVING_TIME,
                BuiltInMetricKeys.HEART_RATE,
                BuiltInMetricKeys.CADENCE,
                BuiltInMetricKeys.CLOCK,
            ),
            isFirstFullWidth = true,
        ),
    ),
)
