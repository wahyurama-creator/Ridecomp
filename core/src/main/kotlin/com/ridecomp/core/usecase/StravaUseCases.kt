package com.ridecomp.core.usecase

import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.model.StravaSetup
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

/** Strava connection for the screens, without tokens (Phase 5). */
public class ObserveStravaStatusUseCase(private val store: StravaAccountStore, private val setup: StravaSetup) {
    public operator fun invoke(): Flow<StravaStatus> = store.observe().map { it.status(setup) }.distinctUntilChanged()
}

internal fun StravaAccount.status(setup: StravaSetup): StravaStatus = when {
    !setup.isAvailable -> StravaStatus.Unavailable
    tokens == null -> StravaStatus.NotConnected
    else -> StravaStatus.Connected(athleteName, isAutoUpload = autoUploadFromMs != null)
}

/**
 * Starts the authorization (T5.2): a fresh random `state` is stored (SEC-AUTH-02) and the mobile authorize
 * URL of RideComp's app returned, to be opened in the Strava app or the browser (SEC-AUTH-01). Null when
 * this build has no Strava app.
 */
public class StartStravaAuthUseCase(
    private val store: StravaAccountStore,
    private val setup: StravaSetup,
    private val random: SecureRandom,
) {
    public suspend operator fun invoke(): String? {
        if (!setup.isAvailable) return null
        val bytes = ByteArray(StravaConfig.STATE_BYTES).also(random::nextBytes)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        store.update { it.copy(pendingState = state) }
        val params = listOf(
            "client_id" to setup.clientId,
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
    /** No authorization was started (an unexpected redirect). */
    NOT_STARTED,

    /** The `state` does not match: ignored (CSRF guard). */
    STATE_MISMATCH,

    /** The rider pressed Cancel on Strava. */
    DENIED,

    /** The rider unticked "upload activities". */
    MISSING_SCOPE,

    /** No internet. */
    OFFLINE,

    /** Strava refused the code (expired or already used). */
    REJECTED,

    /** Any other failure. */
    FAILED,
}

/** Finishes the authorization: checks `state` and scope, trades the code for tokens and stores them. */
public class CompleteStravaAuthUseCase(private val store: StravaAccountStore, private val api: StravaApi) {
    public suspend operator fun invoke(redirect: StravaAuthRedirect): Outcome<Unit, StravaAuthError> {
        val expected = store.get().pendingState
        return when {
            expected == null -> StravaAuthError.NOT_STARTED.asFailure()
            !expected.matches(redirect.state) -> StravaAuthError.STATE_MISMATCH.asFailure()
            else -> {
                // The state is single use, whatever the outcome.
                store.update { it.copy(pendingState = null) }
                finish(redirect)
            }
        }
    }

    private suspend fun finish(redirect: StravaAuthRedirect): Outcome<Unit, StravaAuthError> {
        val code = redirect.code
        return when {
            redirect.error != null || code.isNullOrBlank() -> StravaAuthError.DENIED.asFailure()
            !redirect.scope.grants(StravaConfig.SCOPE) -> StravaAuthError.MISSING_SCOPE.asFailure()
            else -> when (val result = api.exchangeCode(code)) {
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
        val tokens = store.get().tokens
        val nowS = clock.nowMs() / Units.MS_PER_SECOND
        when {
            tokens == null -> StravaError.NOT_CONNECTED.asFailure()
            !forceRefresh && tokens.expiresAtS - nowS > StravaConfig.REFRESH_MARGIN_S -> tokens.accessToken.asSuccess()
            else -> refresh(tokens.refreshToken)
        }
    }

    private suspend fun refresh(refreshToken: String): Outcome<String, StravaError> =
        when (val result = api.refresh(refreshToken)) {
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
 * `deauthorize`, through the token server), then always forgets the tokens here, even offline, so the phone
 * keeps no access; auto-upload is turned off. False when Strava could not be told
 * (the rider can remove access on strava.com/settings/apps).
 */
public class DisconnectStravaUseCase(private val store: StravaAccountStore, private val api: StravaApi) {
    public suspend operator fun invoke(): Boolean {
        val tokens = store.get().tokens
        val isRevoked = tokens == null || api.revoke(tokens.refreshToken) is Outcome.Success
        store.update { it.copy(tokens = null, athleteName = null, autoUploadFromMs = null, pendingState = null) }
        return isRevoked
    }
}
