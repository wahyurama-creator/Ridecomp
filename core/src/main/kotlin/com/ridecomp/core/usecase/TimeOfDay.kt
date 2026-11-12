package com.ridecomp.core.usecase

import com.ridecomp.core.config.ActivityConfig

/** Part of day used for default ride names ("Morning ride", 02-DESIGN §3.6). */
public enum class TimeOfDay {
    MORNING,
    NOON,
    AFTERNOON,
    EVENING,
    ;

    public companion object {
        /** Maps a local hour (0–23) to the part of day. */
        public fun ofHour(hour: Int): TimeOfDay = when {
            hour < ActivityConfig.MORNING_START_HOUR -> EVENING
            hour < ActivityConfig.NOON_START_HOUR -> MORNING
            hour < ActivityConfig.AFTERNOON_START_HOUR -> NOON
            hour < ActivityConfig.EVENING_START_HOUR -> AFTERNOON
            else -> EVENING
        }
    }
}
