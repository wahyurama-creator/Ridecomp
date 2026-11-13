package com.ridecomp.core.usecase

import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeExportFileStore
import com.ridecomp.core.testing.FakeExportedFileHandler
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.getOrNull
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ShareUseCasesTest {
    private val repository = FakeActivityRepository()
    private val handler = FakeExportedFileHandler()
    private val store = FakeExportFileStore()
    private val export = ExportActivityUseCase(
        ExportFitUseCase(repository, store, FakeClock(), ZoneOffset.UTC, FakeActivityProfileRepository()),
        ExportGpxUseCase(repository, store, ZoneOffset.UTC),
    )

    @Test
    fun `share exports then opens the share sheet`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", FakeClock.DEFAULT_START_MS))

        val file = ShareActivityFileUseCase(export, handler)(id, ExportFormat.GPX).getOrNull()

        assertEquals(listOf(file), handler.shared)
        assertEquals(ExportFormat.GPX, file?.format)
    }

    @Test
    fun `nothing is shared when the export fails`() = runTest {
        val result = ShareActivityFileUseCase(export, handler)(42, ExportFormat.FIT)

        assertEquals(Outcome.Failure(ExportError.NOT_FOUND), result)
        assertEquals(emptyList(), handler.shared)
    }

    @Test
    fun `save copies to the chosen document and reports a failed copy`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", FakeClock.DEFAULT_START_MS))
        val save = SaveActivityFileUseCase(export, handler)

        save(id, ExportFormat.FIT, "content://downloads/1")
        handler.isCopyFailing = true
        val failed = save(id, ExportFormat.FIT, "content://downloads/2")

        assertEquals("content://downloads/1", handler.copied.single().second)
        assertEquals(Outcome.Failure(ExportError.WRITE_FAILED), failed)
    }

    @Test
    fun `suggested file name follows the export naming`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", FakeClock.DEFAULT_START_MS))
        val suggest = SuggestExportFileNameUseCase(repository, ZoneOffset.UTC)

        assertEquals("RideComp_20260101_0000.fit", suggest(id, ExportFormat.FIT))
        assertNull(suggest(99, ExportFormat.FIT))
    }
}
