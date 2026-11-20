package com.ridecomp.core.model

import com.ridecomp.core.config.AutoPauseConfig
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.config.ReminderConfig
import com.ridecomp.core.engine.map.MapCacheLimit
import com.ridecomp.core.engine.map.OfflineMapLimit
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.core.engine.voice.VoiceSettings
import java.time.Instant
import java.time.ZoneOffset

public enum class Sex { MALE, FEMALE }

/** App colors (T11.9, D-110): dark, light, or following the phone. Dark by default so nothing changes. */
public enum class AppTheme {
    DARK,
    LIGHT,
    SYSTEM,
    ;

    public companion object {
        public val DEFAULT: AppTheme = DARK
    }
}

/** Riding position → CdA for estimated power (05-DATA-MODEL §3.7). */
public enum class RidingPosition { TOPS, HOODS, DROPS }

/** Surface → rolling resistance for estimated power. */
public enum class Surface { ROAD, GRAVEL }

public enum class UnitSystem { METRIC, IMPERIAL }

/** Screen behaviour during a ride (02-DESIGN §6). */
public enum class ScreenMode { ALWAYS_ON, AUTO_DIM, OFF }

/** Automatic battery modes (12-FEATURE-SPECS §3), ordered from least to most restrictive. */
public enum class BatteryMode { NORMAL, SAVER, CRITICAL, EMERGENCY }

/** Rider profile (05-DATA-MODEL `profile`). */
public data class RiderProfile(
    val weightKg: Double = ProfileDefaults.WEIGHT_KG,
    val bikeWeightKg: Double = ProfileDefaults.BIKE_WEIGHT_KG,
    val wheelCircumferenceMm: Int = ProfileDefaults.WHEEL_CIRCUMFERENCE_MM,
    val hrMaxBpm: Int? = null,
    val hrRestBpm: Int? = null,
    val ftpW: Int? = null,
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val ridingPosition: RidingPosition = RidingPosition.HOODS,
    val surface: Surface = Surface.ROAD,
)

/**
 * Settings that shape a recording. In Phase 2 these move into activity profiles (12-FEATURE-SPECS §4);
 * until then one global set is used.
 */
public data class RideSettings(
    val isAutoPauseEnabled: Boolean = AutoPauseConfig.IS_ENABLED_DEFAULT,
    val autoPauseSpeedMps: Double = AutoPauseConfig.SPEED_THRESHOLD_MPS,
    val autoPauseDelayS: Int = AutoPauseConfig.DELAY_DEFAULT_S,
    val idlePromptMin: Int = AutoPauseConfig.IDLE_PROMPT_DEFAULT_MIN,
    /** Auto lap distance; null = off. */
    val autoLapM: Double? = null,
    /** Drink reminder interval of moving time; null = off. */
    val drinkIntervalMin: Int? = ReminderConfig.DRINK_INTERVAL_DEFAULT_MIN,
    /** Eat reminder interval of moving time; null = off. */
    val eatIntervalMin: Int? = ReminderConfig.EAT_INTERVAL_DEFAULT_MIN,
    val screenMode: ScreenMode = ScreenMode.AUTO_DIM,
    /** The user may disable the Saver mode only (12-FEATURE-SPECS §3). */
    val isBatterySaverEnabled: Boolean = true,
    /** Volume Up = lap, Volume Down = pause/resume on the ride screen (02-DESIGN §3.4, T2.12). */
    val isVolumeButtonsEnabled: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /** Online map tiles: Wi‑Fi and data, Wi‑Fi only, or never (D-90). */
    val onlineMapMode: OnlineMapMode = OnlineMapMode.ALWAYS,
    /** Space the online map's tile cache may use (T11.11). */
    val mapCacheLimit: MapCacheLimit = MapCacheLimit.DEFAULT,
    /** Space all offline maps together may use (T11.14). */
    val offlineMapLimit: OfflineMapLimit = OfflineMapLimit.DEFAULT,
    /** Spoken distance, laps and reminders (T7.1); off by default. */
    val voice: VoiceSettings = VoiceSettings(),
)

/** Age in whole years at [nowMs] (UTC year difference), or null without a birth year. */
public fun RiderProfile.ageYears(nowMs: Long): Int? =
    birthYear?.let { Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).year - it }
