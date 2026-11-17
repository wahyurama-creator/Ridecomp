package com.ridecomp.core.testing

import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.model.StravaSetup
import com.ridecomp.core.model.StravaTokens
import com.ridecomp.core.model.StravaUpload
import com.ridecomp.core.repository.StravaUploadRepository
import com.ridecomp.core.source.StravaAccountStore
import com.ridecomp.core.source.StravaApi
import com.ridecomp.core.source.StravaGrant
import com.ridecomp.core.source.StravaUploadReply
import com.ridecomp.core.source.StravaUploadRequest
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [StravaUploadRepository]. */
class FakeStravaUploadRepository : StravaUploadRepository {
    val uploads = MutableStateFlow<Map<Long, StravaUpload>>(emptyMap())

    override fun observe(activityId: Long): Flow<StravaUpload?> = uploads.map { it[activityId] }

    override suspend fun get(activityId: Long): StravaUpload? = uploads.value[activityId]

    override suspend fun save(upload: StravaUpload) {
        uploads.update { it + (upload.activityId to upload) }
    }

    override fun observeOnStrava(): Flow<Set<Long>> =
        uploads.map { all -> all.values.filter { it.state.isOnStrava }.map { it.activityId }.toSet() }

    override suspend fun getActive(): List<StravaUpload> =
        uploads.value.values.filter { it.state.isActive }.sortedBy { it.updatedAtMs }
}

/** In-memory [StravaAccountStore]. */
class FakeStravaAccountStore(initial: StravaAccount = StravaAccount()) : StravaAccountStore {
    val account = MutableStateFlow(initial)

    override fun observe(): Flow<StravaAccount> = account

    override suspend fun get(): StravaAccount = account.value

    override suspend fun update(transform: (StravaAccount) -> StravaAccount): StravaAccount {
        account.update(transform)
        return account.value
    }
}

/** Scripted [StravaApi]; records what was asked. */
class FakeStravaApi : StravaApi {
    var exchangeResult: Outcome<StravaGrant, StravaError> = GRANT.asSuccess()
    var refreshResult: Outcome<StravaGrant, StravaError> = REFRESHED.asSuccess()
    var revokeResult: Outcome<Unit, StravaError> = Unit.asSuccess()
    val exchangedCodes = mutableListOf<String>()
    val refreshedTokens = mutableListOf<String>()
    val revokedTokens = mutableListOf<String>()

    override suspend fun exchangeCode(code: String): Outcome<StravaGrant, StravaError> {
        exchangedCodes += code
        return exchangeResult
    }

    override suspend fun refresh(refreshToken: String): Outcome<StravaGrant, StravaError> {
        refreshedTokens += refreshToken
        return refreshResult
    }

    override suspend fun revoke(token: String): Outcome<Unit, StravaError> {
        revokedTokens += token
        return revokeResult
    }

    /** Replies of the next uploads, in order; the last one repeats. */
    var uploadResults: List<Outcome<StravaUploadReply, StravaError>> =
        listOf(StravaUploadReply(77, null, null).asSuccess())

    /** Replies of the next status checks, in order; the last one repeats. */
    var statusResults: List<Outcome<StravaUploadReply, StravaError>> =
        listOf(StravaUploadReply(77, 900, null).asSuccess())
    val uploadRequests = mutableListOf<Pair<String, StravaUploadRequest>>()
    val statusChecks = mutableListOf<Pair<String, Long>>()

    override suspend fun upload(
        accessToken: String,
        request: StravaUploadRequest,
    ): Outcome<StravaUploadReply, StravaError> {
        uploadRequests += accessToken to request
        return uploadResults[minOf(uploadRequests.lastIndex, uploadResults.lastIndex)]
    }

    override suspend fun uploadStatus(accessToken: String, uploadId: Long): Outcome<StravaUploadReply, StravaError> {
        statusChecks += accessToken to uploadId
        return statusResults[minOf(statusChecks.lastIndex, statusResults.lastIndex)]
    }

    companion object {
        val SETUP = StravaSetup("12345", "https://token.example")
        val GRANT = StravaGrant(StravaTokens("access1", "refresh1", 1_000_000L), "Rider One")
        val REFRESHED = StravaGrant(StravaTokens("access2", "refresh2", 2_000_000L), null)
    }
}
