package com.ridecomp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which tips show for each rider level (Phase 4G). */
class GuidePreferencesTest {
    @Test
    fun `beginners see every tip, intermediates only advanced ones, experienced none`() {
        val beginner = GuidePreferences(RiderLevel.BEGINNER)
        val intermediate = GuidePreferences(RiderLevel.INTERMEDIATE)
        val experienced = GuidePreferences(RiderLevel.EXPERIENCED)

        assertTrue(GuideTip.entries.all(beginner::shows))
        assertEquals(GuideTip.entries.filter { it.isAdvanced }, GuideTip.entries.filter(intermediate::shows))
        assertTrue(GuideTip.entries.none(experienced::shows))
    }

    @Test
    fun `a closed tip stays closed and no level means intermediate`() {
        assertFalse(GuidePreferences(RiderLevel.BEGINNER, setOf(GuideTip.ROUTES)).shows(GuideTip.ROUTES))
        assertEquals(RiderLevel.INTERMEDIATE, GuidePreferences().effectiveLevel)
        assertFalse(GuidePreferences().shows(GuideTip.FIRST_RIDE))
        assertTrue(GuidePreferences().shows(GuideTip.BACKUP))
        assertEquals(RiderLevel.EXPERIENCED, RiderLevel.fromStorage("experienced"))
    }
}
