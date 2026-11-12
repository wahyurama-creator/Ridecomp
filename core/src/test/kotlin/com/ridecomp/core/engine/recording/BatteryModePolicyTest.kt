package com.ridecomp.core.engine.recording

import com.ridecomp.core.model.BatteryMode
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.ScreenMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatteryModePolicyTest {
    private fun discharging(percent: Int) = BatteryStatus(percent, isCharging = false)

    @Test
    fun `draining from 22 to 4 percent walks through every mode once`() {
        val policy = BatteryModePolicy()

        val changes = (22 downTo 4).mapNotNull { policy.update(discharging(it)) }

        assertEquals(listOf(BatteryMode.SAVER, BatteryMode.CRITICAL, BatteryMode.EMERGENCY), changes.map { it.mode })
        assertEquals(
            listOf(
                RideEventType.BATTERY_MODE_SAVER,
                RideEventType.BATTERY_MODE_CRITICAL,
                RideEventType.BATTERY_MODE_EMERGENCY,
            ),
            changes.map { it.event },
        )
        assertEquals(listOf(false, false, true), changes.map { it.shouldWriteEmergencyFit })
    }

    @Test
    fun `reading noise never steps back to a lighter mode`() {
        val policy = BatteryModePolicy()
        policy.update(discharging(10))

        assertNull(policy.update(discharging(11)))
        assertEquals(BatteryMode.CRITICAL, policy.mode)
    }

    @Test
    fun `charging returns to normal`() {
        val policy = BatteryModePolicy()
        policy.update(discharging(9))

        val change = policy.update(BatteryStatus(9, isCharging = true))

        assertEquals(BatteryModeChange(BatteryMode.NORMAL, null, false), change)
    }

    @Test
    fun `emergency fit is written only once even after charging and draining again`() {
        val policy = BatteryModePolicy()
        policy.update(discharging(5))
        policy.update(BatteryStatus(6, isCharging = true))

        val again = policy.update(discharging(5))

        assertEquals(BatteryMode.EMERGENCY, again?.mode)
        assertFalse(requireNotNull(again).shouldWriteEmergencyFit)
    }

    @Test
    fun `disabling saver skips saver but not critical`() {
        val policy = BatteryModePolicy(isSaverEnabled = false)

        assertNull(policy.update(discharging(15)))
        assertEquals(BatteryMode.CRITICAL, policy.update(discharging(10))?.mode)
    }

    @Test
    fun `battery mode overrides the screen mode`() {
        assertEquals(
            ScreenMode.ALWAYS_ON,
            BatteryModePolicy.effectiveScreenMode(ScreenMode.ALWAYS_ON, BatteryMode.NORMAL),
        )
        assertEquals(
            ScreenMode.AUTO_DIM,
            BatteryModePolicy.effectiveScreenMode(ScreenMode.ALWAYS_ON, BatteryMode.SAVER),
        )
        assertEquals(ScreenMode.OFF, BatteryModePolicy.effectiveScreenMode(ScreenMode.OFF, BatteryMode.SAVER))
        assertEquals(ScreenMode.OFF, BatteryModePolicy.effectiveScreenMode(ScreenMode.AUTO_DIM, BatteryMode.CRITICAL))
        assertEquals(ScreenMode.OFF, BatteryModePolicy.effectiveScreenMode(ScreenMode.ALWAYS_ON, BatteryMode.EMERGENCY))
    }

    @Test
    fun `sensors and tiles are restricted by mode`() {
        assertTrue(BatteryModePolicy.isBarometerAllowed(BatteryMode.SAVER))
        assertFalse(BatteryModePolicy.isBarometerAllowed(BatteryMode.CRITICAL))
        assertTrue(BatteryModePolicy.isMapTilesAllowed(BatteryMode.NORMAL))
        assertFalse(BatteryModePolicy.isMapTilesAllowed(BatteryMode.SAVER))
    }
}
