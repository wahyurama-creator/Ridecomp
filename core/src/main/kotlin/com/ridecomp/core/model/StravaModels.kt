package com.ridecomp.core.model

/** The rider's own Strava API app (SEC-AUTH-04). [toString] never shows the secret. */
public data class StravaApp(val clientId: String, val clientSecret: String) {
    override fun toString(): String = "StravaApp(clientId=$clientId)"
}

/** OAuth tokens; never logged or shown (SEC-AUTH-06). */
public data class StravaTokens(val accessToken: String, val refreshToken: String, val expiresAtS: Long) {
    override fun toString(): String = "StravaTokens(expiresAtS=$expiresAtS)"
}

/** Everything stored for Strava, encrypted on the device and kept out of backups (SEC-STO-02/03). */
public data class StravaAccount(
    val app: StravaApp? = null,
    val tokens: StravaTokens? = null,
    val athleteName: String? = null,
    /** `state` of an authorization in progress; survives the app being killed while the browser is open. */
    val pendingState: String? = null,
)

/** What the screens see: no secrets. */
public sealed interface StravaStatus {
    public data object NotSetUp : StravaStatus

    public data class NotConnected(val clientId: String) : StravaStatus

    public data class Connected(val clientId: String, val athleteName: String?) : StravaStatus
}

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
