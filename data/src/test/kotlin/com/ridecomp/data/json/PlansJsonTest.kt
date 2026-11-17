package com.ridecomp.data.json

import com.ridecomp.core.model.CustomPlan
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.PlanWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlansJsonTest {
    @Test
    fun `plans survive a round trip`() {
        val week = PlanWeek(
            listOf(
                PlanSession(Intensity.ENDURANCE, 60),
                null,
                PlanSession(Intensity.VO2MAX, 45),
                null,
                null,
                null,
                null,
            ),
        )
        val plans = listOf(CustomPlan(3, "Borobudur", 6, week))

        assertEquals(plans, PlansJson.decode(PlansJson.encode(plans)))
    }

    @Test
    fun `damaged json gives no plans and bad entries are skipped`() {
        assertTrue(PlansJson.decode("{not json").isEmpty())
        assertTrue(PlansJson.decode(null).isEmpty())
        // Six days only: dropped. Unknown intensity: that day becomes rest.
        val json = """[{"id":1,"name":"a","weeks":2,"days":[null,null,null,null,null,null]},""" +
            """{"id":2,"name":"b","weeks":99,"days":[{"i":"warp","min":60},null,null,null,null,null,""" +
            """{"i":"tempo","min":45}]}]"""

        val plans = PlansJson.decode(json)

        assertEquals(listOf(2L), plans.map { it.id })
        assertEquals(16, plans.single().weekCount)
        assertEquals(
            listOf(null, null, null, null, null, null, PlanSession(Intensity.TEMPO, 45)),
            plans.single().week.days,
        )
    }
}
