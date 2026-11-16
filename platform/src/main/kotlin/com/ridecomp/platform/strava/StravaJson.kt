package com.ridecomp.platform.strava

import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaApp
import com.ridecomp.core.model.StravaTokens
import com.ridecomp.core.source.StravaGrant
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON of Strava replies and of the encrypted account file, with org.json (D-26). Replies are untrusted
 * (SEC-NET-04): missing fields, wrong types or oversized values make the reply invalid (null), never a crash.
 */
internal object StravaJson {
    fun grant(text: String): StravaGrant? = parse(text) { json ->
        val tokens = StravaTokens(
            accessToken = json.token(ACCESS_TOKEN) ?: return@parse null,
            refreshToken = json.token(REFRESH_TOKEN) ?: return@parse null,
            expiresAtS = json.optLong(EXPIRES_AT).takeIf { it > 0 } ?: return@parse null,
        )
        StravaGrant(tokens, json.optJSONObject(ATHLETE)?.displayName())
    }

    fun encodeAccount(account: StravaAccount): String = JSONObject().apply {
        account.app?.let {
            put(CLIENT_ID, it.clientId)
            put(CLIENT_SECRET, it.clientSecret)
        }
        account.tokens?.let {
            put(ACCESS_TOKEN, it.accessToken)
            put(REFRESH_TOKEN, it.refreshToken)
            put(EXPIRES_AT, it.expiresAtS)
        }
        account.athleteName?.let { put(ATHLETE_NAME, it) }
        account.pendingState?.let { put(PENDING_STATE, it) }
    }.toString()

    fun decodeAccount(text: String): StravaAccount? = parse(text) { json ->
        val clientId = json.optString(CLIENT_ID).takeIf { it.isNotEmpty() }
        val clientSecret = json.optString(CLIENT_SECRET).takeIf { it.isNotEmpty() }
        val access = json.token(ACCESS_TOKEN)
        val refresh = json.token(REFRESH_TOKEN)
        StravaAccount(
            app = if (clientId != null && clientSecret != null) StravaApp(clientId, clientSecret) else null,
            tokens = if (access != null &&
                refresh != null
            ) {
                StravaTokens(access, refresh, json.optLong(EXPIRES_AT))
            } else {
                null
            },
            athleteName = json.optString(ATHLETE_NAME).takeIf { it.isNotEmpty() },
            pendingState = json.optString(PENDING_STATE).takeIf { it.isNotEmpty() },
        )
    }

    private inline fun <T> parse(text: String, block: (JSONObject) -> T?): T? = try {
        block(JSONObject(text))
    } catch (_: JSONException) {
        null
    }

    private fun JSONObject.token(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it.length <= MAX_TOKEN_LENGTH && !isNull(key) }

    private fun JSONObject.displayName(): String? = listOf(optString(FIRST_NAME), optString(LAST_NAME))
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .take(MAX_NAME_LENGTH)
        .takeIf { it.isNotBlank() }

    private const val ACCESS_TOKEN = "access_token"
    private const val REFRESH_TOKEN = "refresh_token"
    private const val EXPIRES_AT = "expires_at"
    private const val ATHLETE = "athlete"
    private const val FIRST_NAME = "firstname"
    private const val LAST_NAME = "lastname"
    private const val CLIENT_ID = "client_id"
    private const val CLIENT_SECRET = "client_secret"
    private const val ATHLETE_NAME = "athlete_name"
    private const val PENDING_STATE = "pending_state"
    private const val MAX_TOKEN_LENGTH = 512
    private const val MAX_NAME_LENGTH = 60
}
