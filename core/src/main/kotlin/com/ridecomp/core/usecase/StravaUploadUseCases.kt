package com.ridecomp.core.usecase

import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.model.StravaUpload
import com.ridecomp.core.model.StravaUploadError
import com.ridecomp.core.model.StravaUploadState
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.StravaUploadRepository
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.StravaAccountStore
import com.ridecomp.core.source.StravaApi
import com.ridecomp.core.source.StravaUploadReply
import com.ridecomp.core.source.StravaUploadRequest
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.runCatchingCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Strava calls with a valid access token; one forced refresh when Strava refuses the token. */
public class AuthorizedStravaApi(private val accessToken: GetStravaAccessTokenUseCase, private val api: StravaApi) {
    public suspend fun upload(request: StravaUploadRequest): Outcome<StravaUploadReply, StravaError> =
        call { api.upload(it, request) }

    public suspend fun uploadStatus(uploadId: Long): Outcome<StravaUploadReply, StravaError> =
        call { api.uploadStatus(it, uploadId) }

    private suspend fun <T> call(block: suspend (String) -> Outcome<T, StravaError>): Outcome<T, StravaError> =
        when (val token = accessToken()) {
            is Outcome.Failure -> token
            is Outcome.Success -> {
                val first = block(token.value)
                if (first is Outcome.Failure && first.error == StravaError.UNAUTHORIZED) retry(block) else first
            }
        }

    private suspend fun <T> retry(block: suspend (String) -> Outcome<T, StravaError>): Outcome<T, StravaError> =
        when (val token = accessToken(forceRefresh = true)) {
            is Outcome.Failure -> token
            is Outcome.Success -> block(token.value)
        }
}

/** Wakes the upload queue (the app processes it while open, BAT-05). Single instance. */
public class StravaUploadRequests {
    private val requests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    public val flow: Flow<Unit> = requests.asSharedFlow()

    public fun request() {
        requests.tryEmit(Unit)
    }
}

/** Rides already on Strava ("Di Strava" in the history list). */
public class ObserveRidesOnStravaUseCase(private val uploads: StravaUploadRepository) {
    public operator fun invoke(): Flow<Set<Long>> = uploads.observeOnStrava()
}

public class ObserveStravaUploadUseCase(private val uploads: StravaUploadRepository) {
    public operator fun invoke(activityId: Long): Flow<StravaUpload?> = uploads.observe(activityId)
}

/** "Upload to Strava" on a ride: queued, then sent by the app. A ride already on Strava or on its way is left alone. */
public class QueueStravaUploadUseCase(
    private val uploads: StravaUploadRepository,
    private val requests: StravaUploadRequests,
    private val clock: Clock,
) {
    public suspend operator fun invoke(activityId: Long) {
        val state = uploads.get(activityId)?.state
        if (state?.isActive != true && state?.isOnStrava != true) {
            uploads.save(StravaUpload(activityId, StravaUploadState.QUEUED, updatedAtMs = clock.nowMs()))
        }
        requests.request()
    }
}

/** Turns auto-upload on (for rides finished from now on) or off. */
public class SetStravaAutoUploadUseCase(private val store: StravaAccountStore, private val clock: Clock) {
    public suspend operator fun invoke(isOn: Boolean) {
        store.update { it.copy(autoUploadFromMs = if (isOn) it.autoUploadFromMs ?: clock.nowMs() else null) }
    }
}

/** With auto-upload on, queues the latest rides finished since it was turned on and never sent. */
public class QueueNewRidesForStravaUseCase(
    private val store: StravaAccountStore,
    private val activities: ActivityRepository,
    private val uploads: StravaUploadRepository,
    private val clock: Clock,
) {
    public suspend operator fun invoke() {
        val account = store.get()
        val fromMs = account.autoUploadFromMs ?: return
        if (account.tokens == null) return
        activities.observeFinished(StravaConfig.AUTO_UPLOAD_RECENT_RIDES, 0).first()
            .filter { (it.endedAtMs ?: 0) >= fromMs && uploads.get(it.id) == null }
            .forEach { uploads.save(StravaUpload(it.id, StravaUploadState.QUEUED, updatedAtMs = clock.nowMs())) }
    }
}

/**
 * Sends queued rides and follows Strava's processing (04-INTEGRATIONS §E.2): the FIT file is made, sent
 * with the ride's UUID as `external_id`, then its status is read every [StravaConfig.POLL_INTERVAL_MS] until
 * Strava answers or [StravaConfig.MAX_POLLS] is reached (the next run continues). Network trouble keeps the
 * ride queued and stops the run; Strava's own refusal ends in [StravaUploadState.FAILED], a duplicate links
 * to the activity Strava already has. Single instance.
 */
public class ProcessStravaUploadsUseCase(
    private val uploads: StravaUploadRepository,
    private val activities: ActivityRepository,
    private val exportFit: ExportFitUseCase,
    private val strava: AuthorizedStravaApi,
    private val clock: Clock,
    private val logger: Logger,
) {
    private val mutex = Mutex()

    public suspend operator fun invoke() {
        mutex.withLock {
            runCatchingCancellable {
                for (upload in uploads.getActive()) {
                    if (!process(upload)) break
                }
            }.onFailure { error -> logger.error(TAG, error) { "Strava uploads stopped; retried later" } }
        }
    }

    /** False when the rest of the queue should wait (no internet, not connected). */
    private suspend fun process(upload: StravaUpload): Boolean {
        val sent = if (upload.state == StravaUploadState.QUEUED) send(upload) else upload
        return when (sent.state) {
            StravaUploadState.PROCESSING -> poll(sent)
            StravaUploadState.QUEUED -> false
            StravaUploadState.FAILED -> sent.error != StravaUploadError.NOT_CONNECTED
            StravaUploadState.DONE, StravaUploadState.DUPLICATE -> true
        }
    }

    private suspend fun send(upload: StravaUpload): StravaUpload {
        val activity = activities.get(upload.activityId)
        val file = exportFit(upload.activityId)
        if (activity == null || file !is Outcome.Success) {
            return upload.saved(StravaUploadState.FAILED, error = StravaUploadError.FILE)
        }
        val request = StravaUploadRequest(file.value.path, file.value.fileName, activity.name, activity.uuid)
        return when (val reply = strava.upload(request)) {
            is Outcome.Success -> upload.withReply(reply.value)
            is Outcome.Failure -> upload.failedWith(reply.error)
        }
    }

    private suspend fun poll(upload: StravaUpload): Boolean {
        val uploadId = upload.uploadId
        if (uploadId == null) {
            upload.saved(StravaUploadState.QUEUED)
            return true
        }
        var polls = 0
        // null: Strava is still processing.
        var shouldContinue: Boolean? = null
        while (shouldContinue == null && polls < StravaConfig.MAX_POLLS) {
            delay(StravaConfig.POLL_INTERVAL_MS)
            polls++
            shouldContinue = when (val reply = strava.uploadStatus(uploadId)) {
                is Outcome.Success -> if (upload.withReply(reply.value).state ==
                    StravaUploadState.PROCESSING
                ) {
                    null
                } else {
                    true
                }
                is Outcome.Failure -> {
                    upload.saved(StravaUploadState.PROCESSING, uploadId = uploadId, error = reply.error.toUploadError())
                    false
                }
            }
        }
        // Still processing after the last check: the next run asks again.
        return shouldContinue ?: true
    }

    private suspend fun StravaUpload.withReply(reply: StravaUploadReply): StravaUpload {
        val error = reply.error
        val activityId = reply.activityId
        return when {
            error != null -> {
                val duplicateOf = StravaConfig.DUPLICATE_PATTERN.find(error)?.groupValues?.get(1)?.toLongOrNull()
                if (duplicateOf != null) {
                    saved(StravaUploadState.DUPLICATE, uploadId = reply.id, stravaActivityId = duplicateOf)
                } else {
                    saved(StravaUploadState.FAILED, uploadId = reply.id, error = StravaUploadError.REJECTED)
                }
            }
            activityId != null -> saved(StravaUploadState.DONE, uploadId = reply.id, stravaActivityId = activityId)
            else -> saved(StravaUploadState.PROCESSING, uploadId = reply.id)
        }
    }

    private suspend fun StravaUpload.failedWith(error: StravaError): StravaUpload {
        val uploadError = error.toUploadError()
        // Network trouble: stays queued and is sent again later. A missing account needs the rider.
        val state = when (uploadError) {
            StravaUploadError.OFFLINE, StravaUploadError.RATE_LIMITED, StravaUploadError.SERVER ->
                StravaUploadState.QUEUED
            StravaUploadError.NOT_CONNECTED, StravaUploadError.REJECTED, StravaUploadError.FILE ->
                StravaUploadState.FAILED
        }
        return saved(state, error = uploadError)
    }

    private suspend fun StravaUpload.saved(
        state: StravaUploadState,
        uploadId: Long? = this.uploadId,
        stravaActivityId: Long? = null,
        error: StravaUploadError? = null,
    ): StravaUpload = copy(
        state = state,
        uploadId = uploadId,
        stravaActivityId = stravaActivityId,
        error = error,
        updatedAtMs = clock.nowMs(),
    ).also { uploads.save(it) }

    private fun StravaError.toUploadError(): StravaUploadError = when (this) {
        StravaError.OFFLINE -> StravaUploadError.OFFLINE
        StravaError.NOT_CONNECTED, StravaError.UNAUTHORIZED -> StravaUploadError.NOT_CONNECTED
        StravaError.RATE_LIMITED -> StravaUploadError.RATE_LIMITED
        StravaError.SERVER, StravaError.BAD_RESPONSE -> StravaUploadError.SERVER
    }

    private companion object {
        const val TAG = "StravaUpload"
    }
}
