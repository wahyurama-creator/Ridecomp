package com.ridecomp.core.usecase

import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPageLimits
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.withFieldCount
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DataPageUseCasesTest {
    private val repository = FakeSettingsRepository()
    private val profiles = FakeActivityProfileRepository()
    private val known = setOf("speed", "distance", "hr", "cadence", "power", "clock", "ascent", "grade", "lap_time")

    @Test
    fun `unknown keys, empty pages and extra pages are removed`() = runTest {
        val tooMany = List(DataPageLimits.MAX_PAGES + 2) { DataPageLayout(listOf("speed")) }
        val layout = DataPagesLayout(
            listOf(DataPageLayout(listOf("speed", "removed_metric", "hr")), DataPageLayout(listOf("gone"))) + tooMany,
        )

        SaveDataPagesUseCase(repository, profiles)(layout, known)

        val saved = ObserveDataPagesUseCase(repository, profiles)(known).first()
        assertEquals(DataPageLimits.MAX_PAGES, saved?.pages?.size)
        assertEquals(listOf("speed", "hr"), saved?.pages?.first()?.fields)
    }

    @Test
    fun `nothing usable or reset means defaults`() = runTest {
        SaveDataPagesUseCase(repository, profiles)(DataPagesLayout(listOf(DataPageLayout(listOf("unknown")))), known)
        assertNull(ObserveDataPagesUseCase(repository, profiles)(known).first())

        SaveDataPagesUseCase(repository, profiles)(DataPagesLayout(listOf(DataPageLayout(listOf("speed")))), known)
        ResetDataPagesUseCase(repository, profiles)()
        assertNull(ObserveDataPagesUseCase(repository, profiles)(known).first())
    }

    @Test
    fun `stored pages are sanitized when read too`() = runTest {
        val tooManyFields = List(DataPageLimits.MAX_FIELDS + 3) { "speed" }
        repository.saveDataPages(DataPagesLayout(listOf(DataPageLayout(tooManyFields))))

        assertEquals(
            DataPageLimits.MAX_FIELDS,
            ObserveDataPagesUseCase(repository, profiles)(known).first()?.pages?.single()?.fields?.size,
        )
    }

    @Test
    fun `changing the field count cuts or fills without duplicates`() {
        val page = DataPageLayout(listOf("speed", "distance", "hr"))

        assertEquals(listOf("speed"), page.withFieldCount(1, known.toList()).fields)
        assertEquals(
            listOf("speed", "distance", "hr", "cadence", "power", "clock"),
            page.withFieldCount(6, listOf("speed", "cadence", "power", "clock", "ascent")).fields,
        )
        assertEquals(DataPageLimits.MAX_FIELDS, page.withFieldCount(20, known.toList()).fields.size)
    }
}
