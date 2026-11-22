package com.ridecomp.feature.settings

import com.ridecomp.core.model.GuideTopic
import com.ridecomp.feature.settings.ui.GUIDE_TEXTS
import kotlin.test.Test
import kotlin.test.assertEquals

class GuideTextsTest {
    @Test
    fun `every help topic has its texts`() {
        assertEquals(GuideTopic.entries.toSet(), GUIDE_TEXTS.keys)
    }
}
