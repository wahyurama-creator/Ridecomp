package com.ridecomp.core.usecase

import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaApp
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.StravaAccountStore
import com.ridecomp.core.source.StravaApi
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Strava connection for the screens, without secrets (Phase 5). */
public class ObserveStravaStatusUseCase(private val store: StravaAccountStore) {
    public operator fun invoke(): Flow<StravaStatus> = store.observe().map { it.status() }.distinctUntilChanged()
}

internal fun StravaAccount.status(): StravaStatus {
    val app = app
    return when {
        app == null -> StravaStatus.NotSetUp
        tokens == null -> StravaStatus.NotConnected(app.clientId)
        else -> StravaStatus.Connected(app.clientId, athleteName, isAutoUpload = autoUploadFromMs != null)
    }
}

public enum class StravaAppError { INVALID_CLIENT_ID, INVALID_CLIENT_SECRET }

/**
 * Saves the rider's API app (T5.1). Values are trimmed and checked against the format Strava shows; a new
 * app drops the old tokens, which belong to the old app.
 */
public class SaveStravaAppUseCase(private val store: StravaAccountStore) {
    public suspend operator fun invoke(clientId: String, clientSecret: String): Outcome<Unit, StravaAppError> {
        val id = clientId.trim()
        val secret = clientSecret.trim()
        val error = when {
            !StravaConfig.CLIENT_ID_PATTERN.matches(id) -> StravaAppError.INVALID_CLIENT_ID
            !StravaConfig.CLIENT_SECRET_PATTERN.matches(secret) -> StravaAppError.INVALID_CLIENT_SECRET
            else -> null
        }
        if (error != null) return error.asFailure()
        store.update { account ->
            val app = StravaApp(id, secret)
            if (account.app == app) account else StravaAccount(app = app)
        }
        return Unit.asSuccess()
    }
}

/**
 * Starts the authorization (T5.2): a fresh random `state` is stored (SEC-AUTH-02) and the mobile authorize
 * URL returned, to be opened in the Strava app or the browser (SEC-AUTH-01). Null when no app is set up.
 */
public class StartStravaAuthUseCase(private val store: StravaAccountStore, private val random: SecureRandom) {
    public suspend operator fun invoke(): String? {
        val bytes = ByteArray(StravaConfig.STATE_BYTES).also(random::nextBytes)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val account = store.update { it.copy(pendingState = state) }
        val app = account.app ?: return null
        val params = listOf(
            "client_id" to app.clientId,
            "redirect_uri" to StravaConfig.REDIRECT_URI,
            "response_type" to "code",
            "approval_prompt" to "auto",
            "scope" to StravaConfig.SCOPE,
            "state" to state,
        )
        return StravaConfig.AUTHORIZE_URL + "?" + params.joinToString("&") { (key, value) -> "$key=${value.encoded()}" }
    }

    private fun String.encoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

/** The query of the redirect `ridecomp://localhost/strava-auth?...` (already matched exactly by the app). */
public data class StravaAuthRedirect(val code: String?, val state: String?, val scope: String?, val error: String?)

public enum class StravaAuthError {
    /** No app saved, or no authorization was started (an unexpected redirect). */
    NOT_STARTED,

    /** The `state` does not match: ignored (CSRF guard). */
    STATE_MISMATCH,

    /** The rider pressed Cancel on Strava. */
    DENIED,

    /** The rider unticked "upload activities". */
    MISSING_SCOPE,

    /** No internet. */
    OFFLINE,

    /** Strava refused the Client ID/Secret or the code. */
    REJECTED,

    /** Any other failure. */
    FAILED,
}

/** Finishes the authorization: checks `state` and scope, trades the code for tokens and stores them. */
public class CompleteStravaAuthUseCase(private val store: StravaAccountStore, private val api: StravaApi) {
    public suspend operator fun invoke(redirect: StravaAuthRedirect): Outcome<Unit, StravaAuthError> {
        val account = store.get()
        val app = account.app
        val expected = account.pendingState
        return when {
            app == null || expected == null -> StravaAuthError.NOT_STARTED.asFailure()
            !expected.matches(redirect.state) -> StravaAuthError.STATE_MISMATCH.asFailure()
            else -> {
                // The state is single use, whatever the outcome.
                store.update { it.copy(pendingState = null) }
                finish(app, redirect)
            }
        }
    }

    private suspend fun finish(app: StravaApp, redirect: StravaAuthRedirect): Outcome<Unit, StravaAuthError> {
        val code = redirect.code
        return when {
            redirect.error != null || code.isNullOrBlank() -> StravaAuthError.DENIED.asFailure()
            !redirect.scope.grants(StravaConfig.SCOPE) -> StravaAuthError.MISSING_SCOPE.asFailure()
            else -> when (val result = api.exchangeCode(app, code)) {
                is Outcome.Success -> {
                    store.update { it.copy(tokens = result.value.tokens, athleteName = result.value.athleteName) }
                    Unit.asSuccess()
                }
                is Outcome.Failure -> result.error.toAuthError().asFailure()
            }
        }
    }

    private fun String.matches(other: String?): Boolean =
        other != null && MessageDigest.isEqual(toByteArray(), other.toByteArray())

    private fun String?.grants(scope: String): Boolean =
        this?.split(StravaConfig.SCOPE_SEPARATOR)?.any { it.trim() == scope } == true

    private fun StravaError.toAuthError(): StravaAuthError = when (this) {
        StravaError.OFFLINE -> StravaAuthError.OFFLINE
        StravaError.UNAUTHORIZED -> StravaAuthError.REJECTED
        StravaError.NOT_CONNECTED, StravaError.RATE_LIMITED, StravaError.SERVER, StravaError.BAD_RESPONSE ->
            StravaAuthError.FAILED
    }
}

/**
 * A valid access token for an API call, refreshed when it is about to expire. Strava rotates refresh tokens
 * (the old one stops working at once), so the new pair is stored before it is used, and one refresh runs
 * at a time. A refused refresh means the access was revoked on strava.com: the tokens are dropped.
 */
public class GetStravaAccessTokenUseCase(
    private val store: StravaAccountStore,
    private val api: StravaApi,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    public suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<String, StravaError> = mutex.withLock {
        val account = store.get()
        val app = account.app
        val tokens = account.tokens
        val nowS = clock.nowMs() / Units.MS_PER_SECOND
        when {
            app == null || tokens == null -> StravaError.NOT_CONNECTED.asFailure()
            !forceRefresh && tokens.expiresAtS - nowS > StravaConfig.REFRESH_MARGIN_S -> tokens.accessToken.asSuccess()
            else -> refresh(app, tokens.refreshToken)
        }
    }

    private suspend fun refresh(app: StravaApp, refreshToken: String): Outcome<String, StravaError> =
        when (val result = api.refresh(app, refreshToken)) {
            is Outcome.Success -> {
                store.update { it.copy(tokens = result.value.tokens) }
                result.value.tokens.accessToken.asSuccess()
            }
            is Outcome.Failure -> {
                val isRevoked = result.error == StravaError.UNAUTHORIZED
                if (isRevoked) store.update { it.copy(tokens = null, athleteName = null) }
                (if (isRevoked) StravaError.NOT_CONNECTED else result.error).asFailure()
            }
        }
}

/**
 * Disconnect (T5.4, SEC-AUTH-06): revokes the grant at Strava (`/oauth/revoke`, which replaces the retired
 * `deauthorize`), then always forgets the tokens here, even offline, so the phone keeps no access. The API
 * app stays so the rider can connect again; auto-upload is turned off. False when Strava could not be told
 * (the rider can remove access on strava.com/settings/apps).
 */
public class DisconnectStravaUseCase(private val store: StravaAccountStore, private val api: StravaApi) {
    public suspend operator fun invoke(): Boolean {
        val account = store.get()
        val app = account.app
        val tokens = account.tokens
        val isRevoked = app == null || tokens == null || api.revoke(app, tokens.refreshToken) is Outcome.Success
        store.update { it.copy(tokens = null, athleteName = null, autoUploadFromMs = null, pendingState = null) }
        return isRevoked
    }
}
