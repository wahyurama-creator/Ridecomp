package com.ridecomp.core.config

import java.time.LocalDate
import java.time.ZoneOffset

/** Strava API endpoints and limits (04-INTEGRATIONS §E.2, findings of T5.0, D-79). */
public object StravaConfig {
    /** OAuth stays on www.strava.com; only the REST API moves in 2027. */
    public const val AUTHORIZE_URL: String = "https://www.strava.com/oauth/mobile/authorize"

    /**
     * Paths of RideComp's token server (D-82): it holds the client secret and trades codes and refresh
     * tokens with Strava (`server/strava-token-server`). Uploads go straight to Strava.
     */
    public const val TOKEN_SERVER_TOKEN_PATH: String = "/token"
    public const val TOKEN_SERVER_REFRESH_PATH: String = "/refresh"
    public const val TOKEN_SERVER_REVOKE_PATH: String = "/revoke"
    public const val ACTIVITY_URL_PREFIX: String = "https://www.strava.com/activities/"

    public const val LEGACY_API_BASE_URL: String = "https://www.strava.com/api/v3"
    public const val NEW_API_BASE_URL: String = "https://api-v3.strava.com"

    /** The new base URL is served from this day (UTC) and required before 1 June 2027. */
    public val NEW_API_FROM_MS: Long =
        LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Redirect back into the app; the host must equal the app's Authorization Callback Domain. */
    public const val REDIRECT_SCHEME: String = "ridecomp"
    public const val REDIRECT_HOST: String = "localhost"
    public const val REDIRECT_PATH: String = "/strava-auth"
    public const val REDIRECT_URI: String = "$REDIRECT_SCHEME://$REDIRECT_HOST$REDIRECT_PATH"

    /** Only uploads (SEC-AUTH-05). */
    public const val SCOPE: String = "activity:write"
    public const val SCOPE_SEPARATOR: String = ","

    /** Random bytes of the OAuth `state` (CSRF guard, SEC-AUTH-02). */
    public const val STATE_BYTES: Int = 32

    /** Refresh the access token when less than this is left (it lasts 6 h). */
    public const val REFRESH_MARGIN_S: Long = 300

    /** RideComp's Client ID (public, in every authorize URL): digits. */
    public val CLIENT_ID_PATTERN: Regex = Regex("^[0-9]{1,12}$")
    public const val HTTPS_PREFIX: String = "https://"

    public const val CONNECT_TIMEOUT_MS: Int = 15_000
    public const val READ_TIMEOUT_MS: Int = 30_000

    /** Upload replies above this size are not trusted (SEC-NET-04). */
    public const val MAX_RESPONSE_BYTES: Int = 64 * 1024

    /** Status checks: Strava allows at most one per second; a ride usually takes a few seconds. */
    public const val POLL_INTERVAL_MS: Long = 2_000
    public const val MAX_POLLS: Int = 30

    /** Auto-upload looks at this many latest rides after each save. */
    public const val AUTO_UPLOAD_RECENT_RIDES: Int = 5

    /** `sport_type` of every upload (RideComp records bike rides). */
    public const val SPORT_TYPE: String = "Ride"
    public const val DATA_TYPE_FIT: String = "fit"

    /** "<file> duplicate of activity 123" (sometimes with a link): the existing activity id. */
    public val DUPLICATE_PATTERN: Regex = Regex("duplicate of\\D*(\\d+)", RegexOption.IGNORE_CASE)

    /** REST base URL for [nowMs] (migration to api-v3 in 2027, T5.4). */
    public fun apiBaseUrl(nowMs: Long): String = if (nowMs >= NEW_API_FROM_MS) NEW_API_BASE_URL else LEGACY_API_BASE_URL
}
