package com.ridecomp.core.usecase

import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.model.StravaTokens
import com.ridecomp.core.model.StravaUpload
import com.ridecomp.core.model.StravaUploadError
import com.ridecomp.core.model.StravaUploadState
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.source.StravaUploadReply
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeExportFileStore
import com.ridecomp.core.testing.FakeStravaAccountStore
import com.ridecomp.core.testing.FakeStravaApi
import com.ridecomp.core.testing.FakeStravaUploadRepository
import com.ridecomp.core.testing.RecordingLogger
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class StravaUploadUseCasesTest {
    private val clock = FakeClock()
    private val activities = FakeActivityRepository()
    private val uploads = FakeStravaUploadRepository()
    private val store = FakeStravaAccountStore(
        StravaAccount(tokens = StravaTokens("access", "refresh", Long.MAX_VALUE / 2_000)),
    )
    private val api = FakeStravaApi()
    private val requests = StravaUploadRequests()
    private val process = ProcessStravaUploadsUseCase(
        uploads,
        activities,
        ExportFitUseCase(activities, FakeExportFileStore(), clock, ZoneOffset.UTC, FakeActivityProfileRepository()),
        AuthorizedStravaApi(GetStravaAccessTokenUseCase(store, api, clock), api),
        clock,
        RecordingLogger(),
    )

    private suspend fun ride(uuid: String = "uuid-1", endedAtMs: Long = FakeClock.DEFAULT_START_MS + 1_000): Long {
        val id = activities.create(NewActivity(uuid, "Morning ride", FakeClock.DEFAULT_START_MS))
        activities.finish(id, "Morning ride", RideTotals(distanceM = 1_000.0), endedAtMs)
        return id
    }

    private suspend fun queued(id: Long) = QueueStravaUploadUseCase(uploads, requests, clock)(id)

    @Test
    fun `a queued ride is sent with its uuid, then linked once Strava processed it`() = runTest {
        val id = ride()
        queued(id)
        assertEquals(StravaUploadState.QUEUED, uploads.get(id)?.state)

        process()

        val request = api.uploadRequests.single()
        assertEquals("access", request.first)
        assertEquals("uuid-1", request.second.externalId)
        assertEquals("Morning ride", request.second.name)
        assertTrue(request.second.fileName.endsWith(".fit"))
        assertEquals(listOf("access" to 77L), api.statusChecks)
        assertEquals(StravaUpload(id, StravaUploadState.DONE, 77, 900, null, clock.nowMs()), uploads.get(id))
        assertEquals(StravaUploadState.DONE, ObserveStravaUploadUseCase(uploads)(id).first()?.state)
    }

    @Test
    fun `status is polled while Strava processes, and continued at the next run`() = runTest {
        val id = ride()
        queued(id)
        api.statusResults = listOf(StravaUploadReply(77, null, null).asSuccess())

        process()

        assertEquals(30, api.statusChecks.size)
        assertEquals(StravaUploadState.PROCESSING, uploads.get(id)?.state)

        api.statusResults = listOf(StravaUploadReply(77, 901, null).asSuccess())
        process()
        assertEquals(1, api.uploadRequests.size)
        assertEquals(901L, uploads.get(id)?.stravaActivityId)
    }

    @Test
    fun `duplicates link to the existing activity, other refusals fail`() = runTest {
        val first = ride("u1")
        val second = ride("u2")
        queued(first)
        queued(second)
        api.statusResults = listOf(
            StravaUploadReply(77, null, "RideComp.fit duplicate of activity 123456").asSuccess(),
            StravaUploadReply(78, null, "Improperly formatted data.").asSuccess(),
        )

        process()

        assertEquals(StravaUploadState.DUPLICATE, uploads.get(first)?.state)
        assertEquals(123456L, uploads.get(first)?.stravaActivityId)
        assertEquals(StravaUploadState.FAILED, uploads.get(second)?.state)
        assertEquals(StravaUploadError.REJECTED, uploads.get(second)?.error)
    }

    @Test
    fun `no internet keeps the ride queued and stops the run`() = runTest {
        val first = ride("u1")
        val second = ride("u2")
        queued(first)
        clock.advanceBy(1)
        queued(second)
        api.uploadResults = listOf(StravaError.OFFLINE.asFailure())

        process()

        assertEquals(1, api.uploadRequests.size)
        assertEquals(StravaUploadState.QUEUED, uploads.get(first)?.state)
        assertEquals(StravaUploadError.OFFLINE, uploads.get(first)?.error)
        assertNull(uploads.get(second)?.error)
    }

    @Test
    fun `a refused token is refreshed once, a revoked account fails the upload`() = runTest {
        val id = ride()
        queued(id)
        api.uploadResults = listOf(StravaError.UNAUTHORIZED.asFailure(), StravaUploadReply(77, 5, null).asSuccess())

        process()

        assertEquals(listOf("access", "access2"), api.uploadRequests.map { it.first })
        assertEquals(StravaUploadState.DONE, uploads.get(id)?.state)

        val other = ride("u2")
        queued(other)
        api.uploadResults = listOf(StravaError.UNAUTHORIZED.asFailure())
        api.refreshResult = StravaError.UNAUTHORIZED.asFailure()
        process()
        assertEquals(StravaUploadError.NOT_CONNECTED, uploads.get(other)?.error)
        assertEquals(StravaUploadState.FAILED, uploads.get(other)?.state)
    }

    @Test
    fun `a missing ride fails with a file error and a ride on Strava is not queued again`() = runTest {
        queued(42)
        process()
        assertEquals(StravaUploadError.FILE, uploads.get(42)?.error)

        val id = ride()
        uploads.save(StravaUpload(id, StravaUploadState.DONE, 1, 2))
        queued(id)
        assertEquals(StravaUploadState.DONE, uploads.get(id)?.state)

        // A failed one can be sent again.
        queued(42)
        assertEquals(StravaUploadState.QUEUED, uploads.get(42)?.state)
    }

    @Test
    fun `auto-upload queues rides finished after it was turned on`() = runTest {
        val old = ride("old", endedAtMs = clock.nowMs() - 1)
        val autoUpload = SetStravaAutoUploadUseCase(store, clock)
        val queueNew = QueueNewRidesForStravaUseCase(store, activities, uploads, clock)
        queueNew()
        assertTrue(uploads.uploads.value.isEmpty())

        autoUpload(true)
        val new = ride("new", endedAtMs = clock.nowMs() + 1)
        queueNew()

        assertNull(uploads.get(old))
        assertEquals(StravaUploadState.QUEUED, uploads.get(new)?.state)
        autoUpload(false)
        assertNull(store.account.value.autoUploadFromMs)
    }
}
