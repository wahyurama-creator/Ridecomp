package com.ridecomp.core.usecase

import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.model.StravaSetup
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.model.StravaTokens
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeStravaAccountStore
import com.ridecomp.core.testing.FakeStravaApi
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.errorOrNull
import com.ridecomp.core.util.getOrNull
import java.net.URI
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class StravaUseCasesTest {
    private val store = FakeStravaAccountStore()
    private val api = FakeStravaApi()

    @Test
    fun `status follows the build setup and the stored tokens`() = runTest {
        assertEquals(StravaStatus.Unavailable, ObserveStravaStatusUseCase(store, StravaSetup("", "")).invoke().first())
        assertEquals(
            StravaStatus.Unavailable,
            ObserveStravaStatusUseCase(store, StravaSetup("12345", "http://token.example")).invoke().first(),
        )
        val observe = ObserveStravaStatusUseCase(store, FakeStravaApi.SETUP)
        assertEquals(StravaStatus.NotConnected, observe().first())
        store.account.value =
            StravaAccount(tokens = FakeStravaApi.GRANT.tokens, athleteName = "R", autoUploadFromMs = 1)
        assertEquals(StravaStatus.Connected("R", isAutoUpload = true), observe().first())
        assertFalse(FakeStravaApi.GRANT.tokens.toString().contains("access1"))
    }

    @Test
    fun `authorization url carries the app, the exact redirect, the scope and a fresh state`() = runTest {
        assertNull(StartStravaAuthUseCase(store, StravaSetup("", ""), SecureRandom())())
        val start = StartStravaAuthUseCase(store, FakeStravaApi.SETUP, SecureRandom())

        val first = URI(requireNotNull(start()))
        val firstState = store.account.value.pendingState
        val second = start()

        assertEquals("https://www.strava.com/oauth/mobile/authorize", first.toString().substringBefore("?"))
        val query = first.query.split("&").associate { it.substringBefore("=") to it.substringAfter("=") }
        assertEquals("12345", query["client_id"])
        assertEquals(StravaConfig.REDIRECT_URI, query["redirect_uri"])
        assertEquals(StravaConfig.SCOPE, query["scope"])
        assertEquals(firstState, query["state"])
        assertNotEquals(firstState, store.account.value.pendingState)
        assertTrue(second?.contains(requireNotNull(store.account.value.pendingState)) == true)
    }

    @Test
    fun `a matching redirect stores the tokens and the state is used once`() = runTest {
        store.account.value = StravaAccount(pendingState = "s1")
        val complete = CompleteStravaAuthUseCase(store, api)

        val result = complete(StravaAuthRedirect("code1", "s1", "read,activity:write", null))

        assertTrue(result is Outcome.Success)
        assertEquals(listOf("code1"), api.exchangedCodes)
        assertEquals(FakeStravaApi.GRANT.tokens, store.account.value.tokens)
        assertEquals("Rider One", store.account.value.athleteName)
        assertNull(store.account.value.pendingState)
        assertEquals(
            StravaAuthError.NOT_STARTED,
            complete(StravaAuthRedirect("code1", "s1", "activity:write", null)).errorOrNull(),
        )
    }

    @Test
    fun `wrong state, denial, missing scope and network errors are reported`() = runTest {
        val complete = CompleteStravaAuthUseCase(store, api)
        assertEquals(
            StravaAuthError.NOT_STARTED,
            complete(StravaAuthRedirect("c", "s", "activity:write", null)).errorOrNull(),
        )

        store.account.value = StravaAccount(pendingState = "s1")
        assertEquals(
            StravaAuthError.STATE_MISMATCH,
            complete(StravaAuthRedirect("c", "evil", "activity:write", null)).errorOrNull(),
        )
        // A forged redirect does not cancel the real authorization.
        assertEquals("s1", store.account.value.pendingState)
        assertEquals(
            StravaAuthError.DENIED,
            complete(StravaAuthRedirect(null, "s1", null, "access_denied")).errorOrNull(),
        )

        store.account.value = store.account.value.copy(pendingState = "s2")
        assertEquals(StravaAuthError.MISSING_SCOPE, complete(StravaAuthRedirect("c", "s2", "read", null)).errorOrNull())

        store.account.value = store.account.value.copy(pendingState = "s3")
        api.exchangeResult = StravaError.OFFLINE.asFailure()
        assertEquals(
            StravaAuthError.OFFLINE,
            complete(StravaAuthRedirect("c", "s3", "activity:write", null)).errorOrNull(),
        )

        store.account.value = store.account.value.copy(pendingState = "s4")
        api.exchangeResult = StravaError.UNAUTHORIZED.asFailure()
        assertEquals(
            StravaAuthError.REJECTED,
            complete(StravaAuthRedirect("c", "s4", "activity:write", null)).errorOrNull(),
        )

        store.account.value = store.account.value.copy(pendingState = "s5")
        api.exchangeResult = StravaError.SERVER.asFailure()
        assertEquals(
            StravaAuthError.FAILED,
            complete(StravaAuthRedirect("c", "s5", "activity:write", null)).errorOrNull(),
        )
        assertNull(store.account.value.tokens)
    }

    @Test
    fun `access token is reused until close to expiry, then refreshed and the new pair stored`() = runTest {
        val clock = FakeClock(nowMs = 0)
        val tokens = StravaTokens("access1", "refresh1", expiresAtS = 3_600)
        store.account.value = StravaAccount(tokens = tokens)
        val token = GetStravaAccessTokenUseCase(store, api, clock)

        assertEquals("access1", token().getOrNull())
        assertTrue(api.refreshedTokens.isEmpty())

        clock.advanceBy((3_600 - StravaConfig.REFRESH_MARGIN_S) * 1_000)
        assertEquals("access2", token().getOrNull())
        assertEquals(listOf("refresh1"), api.refreshedTokens)
        assertEquals(FakeStravaApi.REFRESHED.tokens, store.account.value.tokens)

        assertEquals("access2", token(forceRefresh = true).getOrNull())
        assertEquals(listOf("refresh1", "refresh2"), api.refreshedTokens)
    }

    @Test
    fun `a refused refresh disconnects, other failures keep the tokens`() = runTest {
        val tokens = StravaTokens("access1", "refresh1", expiresAtS = 0)
        store.account.value = StravaAccount(tokens = tokens, athleteName = "R")
        val token = GetStravaAccessTokenUseCase(store, api, FakeClock(nowMs = 0))

        api.refreshResult = StravaError.OFFLINE.asFailure()
        assertEquals(StravaError.OFFLINE, token().errorOrNull())
        assertEquals(tokens, store.account.value.tokens)

        api.refreshResult = StravaError.UNAUTHORIZED.asFailure()
        assertEquals(StravaError.NOT_CONNECTED, token().errorOrNull())
        assertNull(store.account.value.tokens)
        assertEquals(StravaError.NOT_CONNECTED, token().errorOrNull())
    }

    @Test
    fun `disconnect revokes and always forgets the tokens`() = runTest {
        val disconnect = DisconnectStravaUseCase(store, api)
        val tokens = StravaTokens("access1", "refresh1", 10)
        store.account.value = StravaAccount(tokens = tokens, athleteName = "R", autoUploadFromMs = 5)

        assertTrue(disconnect())
        assertEquals(listOf("refresh1"), api.revokedTokens)
        assertEquals(StravaAccount(), store.account.value)

        store.account.value = StravaAccount(tokens = tokens)
        api.revokeResult = StravaError.OFFLINE.asFailure()
        assertFalse(disconnect())
        assertNull(store.account.value.tokens)
        assertTrue(disconnect())
    }

    @Test
    fun `api base url moves to api-v3 on 4 January 2027`() {
        assertEquals(StravaConfig.LEGACY_API_BASE_URL, StravaConfig.apiBaseUrl(StravaConfig.NEW_API_FROM_MS - 1))
        assertEquals(StravaConfig.NEW_API_BASE_URL, StravaConfig.apiBaseUrl(StravaConfig.NEW_API_FROM_MS))
        assertEquals(1_799_020_800_000, StravaConfig.NEW_API_FROM_MS)
    }
}
