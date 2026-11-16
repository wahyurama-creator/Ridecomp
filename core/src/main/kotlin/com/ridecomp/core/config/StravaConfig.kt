package com.ridecomp.core.config

import java.time.LocalDate
import java.time.ZoneOffset

/** Strava API endpoints and limits (04-INTEGRATIONS §E.2, findings of T5.0, D-79). */
public object StravaConfig {
    /** OAuth stays on www.strava.com; only the REST API moves in 2027. */
    public const val AUTHORIZE_URL: String = "https://www.strava.com/oauth/mobile/authorize"
    public const val TOKEN_URL: String = "https://www.strava.com/oauth/token"
    public const val REVOKE_URL: String = "https://www.strava.com/oauth/revoke"

    /** Where the rider creates the personal API app (T5.1 guide). */
    public const val API_SETTINGS_URL: String = "https://www.strava.com/settings/api"
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

    /** Client ID: digits; Client Secret: 40 hex characters (as shown on the API settings page). */
    public val CLIENT_ID_PATTERN: Regex = Regex("^[0-9]{1,12}$")
    public val CLIENT_SECRET_PATTERN: Regex = Regex("^[0-9a-fA-F]{40}$")

    public const val CONNECT_TIMEOUT_MS: Int = 15_000
    public const val READ_TIMEOUT_MS: Int = 30_000

    /** Upload replies above this size are not trusted (SEC-NET-04). */
    public const val MAX_RESPONSE_BYTES: Int = 64 * 1024

    /** REST base URL for [nowMs] (migration to api-v3 in 2027, T5.4). */
    public fun apiBaseUrl(nowMs: Long): String = if (nowMs >= NEW_API_FROM_MS) NEW_API_BASE_URL else LEGACY_API_BASE_URL
}
