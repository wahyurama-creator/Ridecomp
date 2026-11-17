package com.ridecomp.core.source

import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.model.StravaTokens
import com.ridecomp.core.util.Outcome
import kotlinx.coroutines.flow.Flow

/** Encrypted store of the Strava app and tokens (Android Keystore, D-79). Single instance. */
public interface StravaAccountStore {
    public fun observe(): Flow<StravaAccount>

    public suspend fun get(): StravaAccount

    /** Read-modify-write under one lock; returns the stored value. */
    public suspend fun update(transform: (StravaAccount) -> StravaAccount): StravaAccount
}

/** Tokens and the athlete's display name from a token reply. */
public data class StravaGrant(val tokens: StravaTokens, val athleteName: String?)

/**
 * Strava over HTTPS (timeouts, validated replies — SEC-NET-*): token calls through RideComp's token server,
 * which adds the client secret (D-82); uploads straight to Strava with the rider's access token.
 */
public interface StravaApi {
    public suspend fun exchangeCode(code: String): Outcome<StravaGrant, StravaError>

    public suspend fun refresh(refreshToken: String): Outcome<StravaGrant, StravaError>

    /** Revokes [token] (and so the whole grant). */
    public suspend fun revoke(token: String): Outcome<Unit, StravaError>

    /** `POST /uploads` (multipart): sends the FIT file at [StravaUploadRequest.filePath]. */
    public suspend fun upload(
        accessToken: String,
        request: StravaUploadRequest,
    ): Outcome<StravaUploadReply, StravaError>

    /** `GET /uploads/{id}`. */
    public suspend fun uploadStatus(accessToken: String, uploadId: Long): Outcome<StravaUploadReply, StravaError>
}

/** What is sent with a FIT file (04-INTEGRATIONS §E.2). */
public data class StravaUploadRequest(
    val filePath: String,
    val fileName: String,
    val name: String,
    /** The ride's UUID: Strava uses it to spot the same file sent twice. */
    val externalId: String,
)

/** Strava's upload record: [activityId] once processed, [error] when it failed. */
public data class StravaUploadReply(val id: Long, val activityId: Long?, val error: String?)
