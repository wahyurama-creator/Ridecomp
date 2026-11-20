package com.ridecomp.data.mapper

import com.ridecomp.core.engine.map.MapCacheLimit
import com.ridecomp.core.engine.map.OfflineMapLimit
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.core.engine.voice.VoiceSettings
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.Sex
import com.ridecomp.core.model.Surface
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.data.db.entity.ProfileEntity
import com.ridecomp.data.db.entity.SettingEntity

/** Keys of the `settings` table, in one place. Stable strings: never rename (data on users' phones). */
internal object SettingsKeys {
    const val AUTO_PAUSE_ENABLED = "ride.auto_pause_enabled"
    const val AUTO_PAUSE_SPEED_MPS = "ride.auto_pause_speed_mps"
    const val AUTO_PAUSE_DELAY_S = "ride.auto_pause_delay_s"
    const val IDLE_PROMPT_MIN = "ride.idle_prompt_min"
    const val AUTO_LAP_M = "ride.auto_lap_m"
    const val DRINK_INTERVAL_MIN = "ride.drink_interval_min"
    const val EAT_INTERVAL_MIN = "ride.eat_interval_min"
    const val SCREEN_MODE = "ride.screen_mode"
    const val BATTERY_SAVER_ENABLED = "ride.battery_saver_enabled"
    const val VOLUME_BUTTONS_ENABLED = "ride.volume_buttons_enabled"
    const val UNIT_SYSTEM = "app.unit_system"
    const val ONLINE_MAP_MODE = "app.online_map_mode"
    const val THEME = "app.theme"
    const val MAP_CACHE_LIMIT = "app.map_cache_limit"
    const val OFFLINE_MAP_LIMIT = "app.offline_map_limit"
    const val MAP_CACHE_FILLED = "map.cache_filled"
    const val MAP_PACKS_INSTALLED = "maps.installed"
    const val MAP_PACKS_DISMISSED = "maps.dismissed"
    const val MAP_PACKS_MOBILE_DATA = "maps.mobile_data"
    const val VOICE_EVERY = "ride.voice_every"
    const val VOICE_LAPS = "ride.voice_laps"
    const val ONBOARDING_DONE = "app.onboarding_done"
    const val DATA_PAGES = "ride.data_pages"
    const val LAST_PROFILE_ID = "app.last_profile_id"
    const val LAST_BIKE_ID = "app.last_bike_id"
    const val BACKUP_LAST_AT = "backup.last_at"
    const val BACKUP_ACTIVITY_COUNT = "backup.activity_count"
    const val BACKUP_SNOOZED_UNTIL = "backup.reminder_snoozed_until"
    const val SAFETY_ENABLED = "safety.enabled"
    const val SAFETY_RIDER_NAME = "safety.rider_name"
    const val SAFETY_LAST_TEST_SMS_AT = "safety.last_test_sms_at"
    const val GUIDE_LEVEL = "guide.rider_level"
    const val GUIDE_DISMISSED_TIPS = "guide.dismissed_tips"

    /** In-app update check (T10.9, D-103). */
    const val UPDATE_AUTO_CHECK = "update.auto_check"
    const val UPDATE_LAST_CHECK_MS = "update.last_check_ms"
    const val UPDATE_LATEST_VERSION = "update.latest_version"
    const val UPDATE_LATEST_URL = "update.latest_url"
    const val UPDATE_DISMISSED_VERSION = "update.dismissed_version"
    const val TRAINING_CUSTOM_PLANS = "training.custom_plans"
    const val TRAINING_ACTIVE_PLAN = "training.active_plan"
    const val TRAINING_PLAN_START = "training.plan_start"
    const val TRAINING_RIDE_TARGET = "training.ride_target"

    /** Stored for an optional value that the user switched off (distinguishes "off" from "default"). */
    const val OFF = "off"
}

internal fun ProfileEntity.toDomain() = RiderProfile(
    weightKg = weightKg,
    bikeWeightKg = bikeWeightKg,
    wheelCircumferenceMm = wheelCircumferenceMm,
    hrMaxBpm = hrMax,
    hrRestBpm = hrRest,
    ftpW = ftpW,
    birthYear = birthYear,
    sex = sex?.toEnumOrNull<Sex>(),
    ridingPosition = ridingPosition.toEnumOrNull<RidingPosition>() ?: RidingPosition.HOODS,
    surface = surface.toEnumOrNull<Surface>() ?: Surface.ROAD,
)

internal fun RiderProfile.toEntity() = ProfileEntity(
    weightKg = weightKg,
    bikeWeightKg = bikeWeightKg,
    wheelCircumferenceMm = wheelCircumferenceMm,
    hrMax = hrMaxBpm,
    hrRest = hrRestBpm,
    ftpW = ftpW,
    birthYear = birthYear,
    sex = sex?.toDbValue(),
    ridingPosition = ridingPosition.toDbValue(),
    surface = surface.toDbValue(),
)

/** Builds [RideSettings] from stored rows; missing or unreadable values fall back to defaults. */
internal fun List<SettingEntity>.toRideSettings(): RideSettings {
    val values = associate { it.key to it.value }
    val defaults = RideSettings()
    return RideSettings(
        isAutoPauseEnabled = values[SettingsKeys.AUTO_PAUSE_ENABLED]?.toBooleanStrictOrNull()
            ?: defaults.isAutoPauseEnabled,
        autoPauseSpeedMps = values[SettingsKeys.AUTO_PAUSE_SPEED_MPS]?.toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: defaults.autoPauseSpeedMps,
        autoPauseDelayS = values[SettingsKeys.AUTO_PAUSE_DELAY_S]?.toIntOrNull() ?: defaults.autoPauseDelayS,
        idlePromptMin = values[SettingsKeys.IDLE_PROMPT_MIN]?.toIntOrNull() ?: defaults.idlePromptMin,
        autoLapM = values.optional(SettingsKeys.AUTO_LAP_M, defaults.autoLapM) { it.toDoubleOrNull() },
        drinkIntervalMin = values.optional(SettingsKeys.DRINK_INTERVAL_MIN, defaults.drinkIntervalMin) {
            it.toIntOrNull()
        },
        eatIntervalMin = values.optional(SettingsKeys.EAT_INTERVAL_MIN, defaults.eatIntervalMin) { it.toIntOrNull() },
        screenMode = values[SettingsKeys.SCREEN_MODE]?.toEnumOrNull<ScreenMode>() ?: defaults.screenMode,
        isBatterySaverEnabled = values[SettingsKeys.BATTERY_SAVER_ENABLED]?.toBooleanStrictOrNull()
            ?: defaults.isBatterySaverEnabled,
        isVolumeButtonsEnabled = values[SettingsKeys.VOLUME_BUTTONS_ENABLED]?.toBooleanStrictOrNull()
            ?: defaults.isVolumeButtonsEnabled,
        unitSystem = values[SettingsKeys.UNIT_SYSTEM]?.toEnumOrNull<UnitSystem>() ?: defaults.unitSystem,
        onlineMapMode = values[SettingsKeys.ONLINE_MAP_MODE]?.toEnumOrNull<OnlineMapMode>() ?: defaults.onlineMapMode,
        mapCacheLimit = values[SettingsKeys.MAP_CACHE_LIMIT]?.toEnumOrNull<MapCacheLimit>() ?: defaults.mapCacheLimit,
        offlineMapLimit = values[SettingsKeys.OFFLINE_MAP_LIMIT]?.toEnumOrNull<OfflineMapLimit>()
            ?: defaults.offlineMapLimit,
        voice = VoiceSettings(
            everyUnits = values.optional(SettingsKeys.VOICE_EVERY, defaults.voice.everyUnits) { raw ->
                raw.toIntOrNull()?.takeIf { it > 0 }
            },
            isLapEnabled = values[SettingsKeys.VOICE_LAPS]?.toBooleanStrictOrNull() ?: defaults.voice.isLapEnabled,
        ),
    )
}

/** Rows to store for [RideSettings]. */
internal fun RideSettings.toEntities(): List<SettingEntity> = listOf(
    SettingEntity(SettingsKeys.AUTO_PAUSE_ENABLED, isAutoPauseEnabled.toString()),
    SettingEntity(SettingsKeys.AUTO_PAUSE_SPEED_MPS, autoPauseSpeedMps.toString()),
    SettingEntity(SettingsKeys.AUTO_PAUSE_DELAY_S, autoPauseDelayS.toString()),
    SettingEntity(SettingsKeys.IDLE_PROMPT_MIN, idlePromptMin.toString()),
    SettingEntity(SettingsKeys.AUTO_LAP_M, autoLapM?.toString() ?: SettingsKeys.OFF),
    SettingEntity(SettingsKeys.DRINK_INTERVAL_MIN, drinkIntervalMin?.toString() ?: SettingsKeys.OFF),
    SettingEntity(SettingsKeys.EAT_INTERVAL_MIN, eatIntervalMin?.toString() ?: SettingsKeys.OFF),
    SettingEntity(SettingsKeys.SCREEN_MODE, screenMode.toDbValue()),
    SettingEntity(SettingsKeys.BATTERY_SAVER_ENABLED, isBatterySaverEnabled.toString()),
    SettingEntity(SettingsKeys.VOLUME_BUTTONS_ENABLED, isVolumeButtonsEnabled.toString()),
    SettingEntity(SettingsKeys.UNIT_SYSTEM, unitSystem.toDbValue()),
    SettingEntity(SettingsKeys.ONLINE_MAP_MODE, onlineMapMode.toDbValue()),
    SettingEntity(SettingsKeys.MAP_CACHE_LIMIT, mapCacheLimit.toDbValue()),
    SettingEntity(SettingsKeys.OFFLINE_MAP_LIMIT, offlineMapLimit.toDbValue()),
    SettingEntity(SettingsKeys.VOICE_EVERY, voice.everyUnits?.toString() ?: SettingsKeys.OFF),
    SettingEntity(SettingsKeys.VOICE_LAPS, voice.isLapEnabled.toString()),
)

/** Optional setting: missing → default, [SettingsKeys.OFF] → null, else parsed (unreadable → default). */
private fun <T> Map<String, String>.optional(key: String, default: T?, parse: (String) -> T?): T? =
    when (val raw = this[key]) {
        null -> default
        SettingsKeys.OFF -> null
        else -> parse(raw) ?: default
    }
