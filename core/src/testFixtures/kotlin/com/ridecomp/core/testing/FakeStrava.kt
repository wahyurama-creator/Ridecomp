package com.ridecomp.core.testing

import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaApp
import com.ridecomp.core.model.StravaError
import com.ridecomp.core.model.StravaTokens
import com.ridecomp.core.source.StravaAccountStore
import com.ridecomp.core.source.StravaApi
import com.ridecomp.core.source.StravaGrant
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [StravaAccountStore]. */
class FakeStravaAccountStore(initial: StravaAccount = StravaAccount()) : StravaAccountStore {
    val account = MutableStateFlow(initial)

    override fun observe(): Flow<StravaAccount> = account

    override suspend fun get(): StravaAccount = account.value

    override suspend fun update(transform: (StravaAccount) -> StravaAccount): StravaAccount {
        account.update(transform)
        return account.value
    }
}

/** Scripted [StravaApi]; records what was asked. */
class FakeStravaApi : StravaApi {
    var exchangeResult: Outcome<StravaGrant, StravaError> = GRANT.asSuccess()
    var refreshResult: Outcome<StravaGrant, StravaError> = REFRESHED.asSuccess()
    var revokeResult: Outcome<Unit, StravaError> = Unit.asSuccess()
    val exchangedCodes = mutableListOf<String>()
    val refreshedTokens = mutableListOf<String>()
    val revokedTokens = mutableListOf<String>()

    override suspend fun exchangeCode(app: StravaApp, code: String): Outcome<StravaGrant, StravaError> {
        exchangedCodes += code
        return exchangeResult
    }

    override suspend fun refresh(app: StravaApp, refreshToken: String): Outcome<StravaGrant, StravaError> {
        refreshedTokens += refreshToken
        return refreshResult
    }

    override suspend fun revoke(app: StravaApp, token: String): Outcome<Unit, StravaError> {
        revokedTokens += token
        return revokeResult
    }

    companion object {
        val APP = StravaApp("12345", "a".repeat(40))
        val GRANT = StravaGrant(StravaTokens("access1", "refresh1", 1_000_000L), "Rider One")
        val REFRESHED = StravaGrant(StravaTokens("access2", "refresh2", 2_000_000L), null)
    }
}
