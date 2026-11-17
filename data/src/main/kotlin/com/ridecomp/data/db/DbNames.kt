package com.ridecomp.data.db

/** Table and file names of the Room database, in one place (05-DATA-MODEL §1). */
object DbNames {
    const val DATABASE_FILE = "ridecomp.db"

    const val TABLE_PROFILE = "profile"
    const val TABLE_SETTINGS = "settings"
    const val TABLE_ACTIVITIES = "activities"
    const val TABLE_TRACK_POINTS = "track_points"
    const val TABLE_EVENTS = "events"
    const val TABLE_LAPS = "laps"
    const val TABLE_SENSORS = "sensors"
    const val TABLE_ACTIVITY_PROFILES = "activity_profiles"
    const val TABLE_EMERGENCY_CONTACTS = "emergency_contacts"
    const val TABLE_INCIDENTS = "incidents"
    const val TABLE_ROUTES = "routes"
    const val TABLE_ROUTE_POINTS = "route_points"
    const val TABLE_ROUTE_CLIMBS = "route_climbs"
    const val TABLE_ROUTE_TURNS = "route_turns"
    const val TABLE_BEST_EFFORTS = "best_efforts"
    const val TABLE_SEGMENTS = "segments"
    const val TABLE_SEGMENT_POINTS = "segment_points"
    const val TABLE_SEGMENT_EFFORTS = "segment_efforts"
    const val TABLE_DAILY_TRAINING = "daily_training"
    const val TABLE_STRAVA_UPLOADS = "strava_uploads"
    const val TABLE_BIKES = "bikes"
    const val TABLE_BIKE_COMPONENTS = "bike_components"

    /** The profile table always holds exactly one row with this id. */
    const val SINGLE_PROFILE_ID = 1

    /** Status values stored in `activities.status`. Stable strings: never rename (data on users' phones). */
    object ActivityStatus {
        const val RECORDING = "recording"
        const val PAUSED = "paused"
        const val FINISHED = "finished"
        const val DISCARDED = "discarded"
    }
}
