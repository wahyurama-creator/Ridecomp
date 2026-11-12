package com.ridecomp.data.mapper

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
    const val UNIT_SYSTEM = "app.unit_system"
    const val ONBOARDING_DONE = "app.onboarding_done"

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
        unitSystem = values[SettingsKeys.UNIT_SYSTEM]?.toEnumOrNull<UnitSystem>() ?: defaults.unitSystem,
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
    SettingEntity(SettingsKeys.UNIT_SYSTEM, unitSystem.toDbValue()),
)

/** Optional setting: missing → default, [SettingsKeys.OFF] → null, else parsed (unreadable → default). */
private fun <T> Map<String, String>.optional(key: String, default: T?, parse: (String) -> T?): T? =
    when (val raw = this[key]) {
        null -> default
        SettingsKeys.OFF -> null
        else -> parse(raw) ?: default
    }
