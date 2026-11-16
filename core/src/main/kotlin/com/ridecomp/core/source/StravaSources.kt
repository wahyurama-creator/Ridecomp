package com.ridecomp.core.source

import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaApp
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

/** Strava's HTTP API (HTTPS only, timeouts, validated replies — SEC-NET-*). */
public interface StravaApi {
    public suspend fun exchangeCode(app: StravaApp, code: String): Outcome<StravaGrant, StravaError>

    public suspend fun refresh(app: StravaApp, refreshToken: String): Outcome<StravaGrant, StravaError>

    /** Revokes [token] (and so the whole grant). */
    public suspend fun revoke(app: StravaApp, token: String): Outcome<Unit, StravaError>
}
