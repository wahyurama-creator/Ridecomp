package com.ridecomp.core.usecase

import com.ridecomp.core.engine.export.FitDecoder
import com.ridecomp.core.engine.export.FitProfile
import com.ridecomp.core.engine.export.GpxWriter
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeExportFileStore
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.getOrNull
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private const val START_MS = 1_767_225_600_000L

class ExportUseCasesTest {
    private val repository = FakeActivityRepository()
    private val store = FakeExportFileStore()
    private val clock = FakeClock()
    private val zone = ZoneId.of("Asia/Jakarta")
    private val profiles = FakeActivityProfileRepository()
    private val exportFit = ExportFitUseCase(repository, store, clock, zone, profiles)

    private suspend fun storedRide(withStop: Boolean = true): Long {
        val id = repository.create(NewActivity("uuid-1", "Gowes <Pagi> & \"Sore\"", START_MS))
        repository.appendSamples(
            id,
            (0L..2_500L).map {
                RideSample(
                    timeMs = START_MS + it * 1_000,
                    latDeg = if (it == 5L) null else -7.79,
                    lonDeg = if (it == 5L) null else 110.36,
                    distanceM = it * 8.0,
                    heartRateBpm = 140,
                )
            },
        )
        val events = mutableListOf(
            RideEvent(START_MS, RideEventType.START),
            RideEvent(START_MS + 100_000, RideEventType.AUTO_PAUSE),
            RideEvent(START_MS + 100_000, RideEventType.REMINDER_DRINK),
            RideEvent(START_MS + 130_000, RideEventType.AUTO_RESUME),
        )
        if (withStop) events += RideEvent(START_MS + 2_500_000, RideEventType.STOP)
        repository.appendEvents(id, events)
        return id
    }

    @Test
    fun `fit export reads all chunks, keeps timer events in order and marks the ride exported`() = runTest {
        val id = storedRide()

        val file = assertNotNull(exportFit(id).getOrNull())

        assertEquals("RideComp_20260101_0700.fit", file.fileName)
        val messages = FitDecoder.decode(requireNotNull(store.files[file.fileName]))
        assertEquals(2_501, messages.count { it.global == FitProfile.Message.RECORD })
        val timer = messages.filter {
            it.global == FitProfile.Message.EVENT
        }.map { it.fields[FitProfile.Event.FIELD_EVENT_TYPE] }
        assertEquals(
            listOf(
                FitProfile.Event.TYPE_START,
                FitProfile.Event.TYPE_STOP_ALL,
                FitProfile.Event.TYPE_START,
                FitProfile.Event.TYPE_STOP_ALL,
            ),
            timer,
        )
        assertEquals(FakeExportFileStore.PATH_PREFIX + file.fileName, repository.get(id)?.fitPath)
    }

    @Test
    fun `an unfinished ride still ends with the timer stopped`() = runTest {
        val id = storedRide(withStop = false)

        val file = assertNotNull(exportFit(id).getOrNull())

        val timer = FitDecoder.decode(requireNotNull(store.files[file.fileName]))
            .filter { it.global == FitProfile.Message.EVENT }
            .map { it.fields[FitProfile.Event.FIELD_EVENT_TYPE] }
        assertEquals(FitProfile.Event.TYPE_STOP_ALL, timer.last())
    }

    @Test
    fun `emergency fit is not marked exported and is removed by the normal export`() = runTest {
        val id = storedRide()

        val emergency = assertNotNull(exportFit(id, isEmergency = true).getOrNull())
        assertNull(repository.get(id)?.fitPath)
        assertTrue(emergency.fileName in store.files)

        exportFit(id)
        assertTrue(emergency.fileName !in store.files)
    }

    @Test
    fun `missing ride and write failures are reported`() = runTest {
        assertEquals(Outcome.Failure(ExportError.NOT_FOUND), exportFit(99))

        val id = storedRide()
        store.isFailing = true
        assertEquals(Outcome.Failure(ExportError.WRITE_FAILED), exportFit(id))
    }

    @Test
    fun `gpx streams every positioned point with escaped name`() = runTest {
        val id = storedRide()

        val file = assertNotNull(ExportGpxUseCase(repository, store, zone)(id).getOrNull())

        val text = requireNotNull(store.files[file.fileName]).toString(Charsets.UTF_8)
        assertEquals("RideComp_20260101_0700.gpx", file.fileName)
        assertEquals(2_500, Regex("<trkpt ").findAll(text).count())
        assertTrue(text.contains("<name>Gowes &lt;Pagi&gt; &amp; &quot;Sore&quot;</name>"))
        assertTrue(text.contains("<gpxtpx:hr>140</gpxtpx:hr>"))
        assertTrue(text.contains("<time>2026-01-01T00:00:00Z</time>"))
        assertTrue(text.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun `xml escaping covers every special character`() {
        assertEquals("&lt;&gt;&amp;&quot;&apos;", GpxWriter.escape("<>&\"'"))
    }
}
