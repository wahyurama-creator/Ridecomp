package com.ridecomp.app.navigation

import android.content.Intent
import android.net.Uri
import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.usecase.StravaAuthRedirect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The OAuth redirect from Strava (T5.2). Only `ridecomp://localhost/strava-auth` exactly is accepted
 * (SEC-AUTH-03); its values are untrusted and length-limited. Any web page could send such a link, so the
 * Strava screen accepts it only with the `state` of an authorization the rider started (SEC-AUTH-02).
 */
object IncomingStravaAuth {
    private val _redirect = MutableStateFlow<StravaAuthRedirect?>(null)
    val redirect: StateFlow<StravaAuthRedirect?> = _redirect.asStateFlow()

    fun offer(intent: Intent?) {
        val data = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        if (!data.isStravaRedirect()) return
        _redirect.value = StravaAuthRedirect(
            code = data.param(CODE),
            state = data.param(STATE),
            scope = data.param(SCOPE),
            error = data.param(ERROR),
        )
    }

    /** Called once the Strava screen took the redirect. */
    fun consumed() {
        _redirect.value = null
    }

    private fun Uri.isStravaRedirect(): Boolean = scheme == StravaConfig.REDIRECT_SCHEME &&
        host == StravaConfig.REDIRECT_HOST &&
        path == StravaConfig.REDIRECT_PATH &&
        port == NO_PORT &&
        userInfo == null &&
        fragment == null

    private fun Uri.param(name: String): String? = getQueryParameter(name)?.takeIf { it.length <= MAX_VALUE_LENGTH }

    private const val CODE = "code"
    private const val STATE = "state"
    private const val SCOPE = "scope"
    private const val ERROR = "error"
    private const val NO_PORT = -1
    private const val MAX_VALUE_LENGTH = 512
}
