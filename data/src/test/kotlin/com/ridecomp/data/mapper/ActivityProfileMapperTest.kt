package com.ridecomp.data.mapper

import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.BuiltInProfiles
import com.ridecomp.core.model.ScreenMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityProfileMapperTest {
    @Test
    fun `every built-in profile survives a round trip`() {
        BuiltInProfiles.ALL.forEach { profile -> assertEquals(profile, profile.toEntity().toDomain()) }
    }

    @Test
    fun `overrides survive and unknown kinds become custom`() {
        val profile = ActivityProfile(
            id = 7,
            name = "Night",
            kind = ActivityKind.CUSTOM,
            isAutoPauseEnabled = false,
            autoLapM = ActivityProfile.AUTO_LAP_OFF,
            screenMode = ScreenMode.ALWAYS_ON,
        )

        assertEquals(profile, profile.toEntity().toDomain())
        assertEquals(ActivityKind.CUSTOM, profile.toEntity().copy(kind = "e_bike").toDomain().kind)
    }
}
