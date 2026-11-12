package com.ridecomp.data.json

import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPagesLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataPagesJsonTest {
    @Test
    fun `pages survive a round trip`() {
        val layout = DataPagesLayout(
            listOf(
                DataPageLayout(listOf("speed", "distance", "hr"), isFirstFullWidth = true),
                DataPageLayout(listOf("grade")),
            ),
        )

        assertEquals(layout, DataPagesJson.decode(DataPagesJson.encode(layout)))
    }

    @Test
    fun `damaged json means defaults instead of a crash`() {
        assertNull(DataPagesJson.decode("{not json"))
        assertNull(DataPagesJson.decode("""{"pages":[{"fields":"speed"}]}"""))
        assertNull(DataPagesJson.decode("""{"other":1}"""))
    }

    @Test
    fun `missing full width flag defaults to false`() {
        val decoded = DataPagesJson.decode("""{"pages":[{"fields":["speed"]}]}""")

        assertEquals(DataPagesLayout(listOf(DataPageLayout(listOf("speed")))), decoded)
    }
}
