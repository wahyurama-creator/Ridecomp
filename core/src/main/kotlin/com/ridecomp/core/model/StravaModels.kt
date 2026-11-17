package com.ridecomp.core.model

import com.ridecomp.core.config.StravaConfig

/**
 * RideComp's Strava API app as built in (D-82): the public Client ID and the address of the token server
 * that keeps the secret. Empty values (a build without them) make Strava unavailable.
 */
public data class StravaSetup(val clientId: String, val tokenServerUrl: String) {
    public val isAvailable: Boolean
        get() = StravaConfig.CLIENT_ID_PATTERN.matches(clientId) &&
            tokenServerUrl.startsWith(StravaConfig.HTTPS_PREFIX) &&
            tokenServerUrl.length > StravaConfig.HTTPS_PREFIX.length
}

/** OAuth tokens; never logged or shown (SEC-AUTH-06). */
public data class StravaTokens(val accessToken: String, val refreshToken: String, val expiresAtS: Long) {
    override fun toString(): String = "StravaTokens(expiresAtS=$expiresAtS)"
}

/** Everything stored for Strava, encrypted on the device and kept out of backups (SEC-STO-02/03). */
public data class StravaAccount(
    val tokens: StravaTokens? = null,
    val athleteName: String? = null,
    /** `state` of an authorization in progress; survives the app being killed while the browser is open. */
    val pendingState: String? = null,
    /** Auto-upload is on for rides finished from this time on; null = off (T5.3). */
    val autoUploadFromMs: Long? = null,
)

/** What the screens see: no tokens. */
public sealed interface StravaStatus {
    /** This build has no Strava app or token server configured. */
    public data object Unavailable : StravaStatus

    public data object NotConnected : StravaStatus

    public data class Connected(val athleteName: String?, val isAutoUpload: Boolean = false) : StravaStatus
}

/** Where a ride is on its way to Strava (T5.3). */
public enum class StravaUploadState {
    /** Waiting to be sent (also after a network error: retried while the app is open). */
    QUEUED,

    /** Sent; Strava is turning the file into an activity. */
    PROCESSING,

    /** On Strava as [StravaUpload.stravaActivityId]. */
    DONE,

    /** Strava already had this ride: linked to the existing activity. */
    DUPLICATE,

    /** Stopped; the rider can try again. */
    FAILED,
    ;

    public val isActive: Boolean get() = this == QUEUED || this == PROCESSING
    public val isOnStrava: Boolean get() = this == DONE || this == DUPLICATE
}

/** Why the last attempt did not finish. */
public enum class StravaUploadError {
    OFFLINE,
    NOT_CONNECTED,
    RATE_LIMITED,
    SERVER,

    /** Strava could not read the file (its own error message). */
    REJECTED,

    /** The FIT file could not be made. */
    FILE,
}

/** One ride's upload, stored so it survives the app being closed (`strava_uploads`, schema v7). */
public data class StravaUpload(
    val activityId: Long,
    val state: StravaUploadState,
    val uploadId: Long? = null,
    val stravaActivityId: Long? = null,
    val error: StravaUploadError? = null,
    val updatedAtMs: Long = 0,
)

/** Why a Strava request failed. */
public enum class StravaError {
    /** No account linked (or Strava revoked the access). */
    NOT_CONNECTED,

    /** No internet or a timeout. */
    OFFLINE,

    /** Strava refused the credentials or the token. */
    UNAUTHORIZED,

    /** Too many requests (X-RateLimit). */
    RATE_LIMITED,

    /** Strava had a problem (5xx). */
    SERVER,

    /** The reply could not be understood. */
    BAD_RESPONSE,
}
