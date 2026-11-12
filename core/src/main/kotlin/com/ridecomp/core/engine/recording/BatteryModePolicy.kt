package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.BatteryModeConfig
import com.ridecomp.core.model.BatteryMode
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.ScreenMode

/** Phone battery as read from the sticky battery broadcast. */
public data class BatteryStatus(val percent: Int, val isCharging: Boolean)

/** A mode change to apply; [shouldWriteEmergencyFit] is true exactly once per ride (≤ 5 %). */
public data class BatteryModeChange(
    val mode: BatteryMode,
    val event: RideEventType?,
    val shouldWriteEmergencyFit: Boolean,
)

/**
 * Automatic battery modes (12-FEATURE-SPECS §3) — goal: a ride is never lost because the phone died.
 *
 * - ≤ 20 % Saver, ≤ 10 % Critical, ≤ 5 % Emergency (full checkpoint + temporary FIT, once).
 * - Modes only escalate while discharging, so ±1 % reading noise never flips them back and forth.
 * - Charging returns to the user's choice (NORMAL).
 * - The user may disable Saver only; Critical/Emergency always apply.
 */
public class BatteryModePolicy(private val isSaverEnabled: Boolean = true) {
    public var mode: BatteryMode = BatteryMode.NORMAL
        private set
    private var hasWrittenEmergencyFit = false

    /** Evaluates a new reading; returns the change to apply, or null when the mode stays the same. */
    public fun update(status: BatteryStatus): BatteryModeChange? {
        val target = if (status.isCharging) BatteryMode.NORMAL else maxOf(mode, modeFor(status.percent))
        if (target == mode) return null
        mode = target
        val shouldWriteFit = target == BatteryMode.EMERGENCY && !hasWrittenEmergencyFit
        if (shouldWriteFit) hasWrittenEmergencyFit = true
        return BatteryModeChange(target, eventFor(target), shouldWriteFit)
    }

    private fun modeFor(percent: Int): BatteryMode = when {
        percent <= BatteryModeConfig.EMERGENCY_PERCENT -> BatteryMode.EMERGENCY
        percent <= BatteryModeConfig.CRITICAL_PERCENT -> BatteryMode.CRITICAL
        percent <= BatteryModeConfig.SAVER_PERCENT && isSaverEnabled -> BatteryMode.SAVER
        else -> BatteryMode.NORMAL
    }

    private fun eventFor(mode: BatteryMode): RideEventType? = when (mode) {
        BatteryMode.NORMAL -> null
        BatteryMode.SAVER -> RideEventType.BATTERY_MODE_SAVER
        BatteryMode.CRITICAL -> RideEventType.BATTERY_MODE_CRITICAL
        BatteryMode.EMERGENCY -> RideEventType.BATTERY_MODE_EMERGENCY
    }

    public companion object {
        /** Screen mode actually applied: Saver forces at least auto-dim, Critical/Emergency force off. */
        public fun effectiveScreenMode(userMode: ScreenMode, batteryMode: BatteryMode): ScreenMode =
            when (batteryMode) {
                BatteryMode.NORMAL -> userMode
                BatteryMode.SAVER -> if (userMode == ScreenMode.ALWAYS_ON) ScreenMode.AUTO_DIM else userMode
                BatteryMode.CRITICAL, BatteryMode.EMERGENCY -> ScreenMode.OFF
            }

        /** Barometer and other non-essential sensors are switched off from Critical on. */
        public fun isBarometerAllowed(batteryMode: BatteryMode): Boolean = batteryMode < BatteryMode.CRITICAL

        /** Maps switch to breadcrumb mode from Saver on (used in Phase 3). */
        public fun isMapTilesAllowed(batteryMode: BatteryMode): Boolean = batteryMode < BatteryMode.SAVER
    }
}
