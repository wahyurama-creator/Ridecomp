package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.config.StravaConfig
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.usecase.CompleteStravaAuthUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
import com.ridecomp.core.usecase.SaveStravaAppUseCase
import com.ridecomp.core.usecase.StartStravaAuthUseCase
import com.ridecomp.core.usecase.StravaAppError
import com.ridecomp.core.usecase.StravaAuthError
import com.ridecomp.core.usecase.StravaAuthRedirect
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.runCatchingCancellable
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.launch

/** One-time message of the Strava screen. */
enum class StravaMessage { CONNECTED, DENIED, MISSING_SCOPE, OFFLINE, REJECTED, FAILED, NO_BROWSER }

@Immutable
data class StravaUiState(
    /** Null while loading. */
    val status: StravaStatus? = null,
    /** The app form is shown when nothing is set up yet, or the rider chose to change it. */
    val isEditingApp: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val appError: StravaAppError? = null,
    val isWorking: Boolean = false,
    val message: StravaMessage? = null,
) {
    val isFormShown: Boolean get() = status == StravaStatus.NotSetUp || isEditingApp
}

sealed interface StravaUiEvent {
    data class ClientIdChanged(val value: String) : StravaUiEvent

    data class ClientSecretChanged(val value: String) : StravaUiEvent

    data object SaveApp : StravaUiEvent

    data object EditApp : StravaUiEvent

    data object CancelEdit : StravaUiEvent

    data object Connect : StravaUiEvent

    data object OpenApiPage : StravaUiEvent

    /** No app could open the link. */
    data object OpenFailed : StravaUiEvent

    /** The redirect from Strava, already matched to `ridecomp://localhost/strava-auth` by the app. */
    data class Redirect(val redirect: StravaAuthRedirect) : StravaUiEvent

    data object MessageShown : StravaUiEvent
}

sealed interface StravaEffect {
    /** Opens the authorize page (Strava app or browser, SEC-AUTH-01) or the API settings page. */
    data class OpenUrl(val url: String) : StravaEffect
}

/** Use cases of the Strava screen, grouped to keep the constructor short (KT-05). */
data class StravaUseCases(
    val observeStatus: ObserveStravaStatusUseCase,
    val saveApp: SaveStravaAppUseCase,
    val startAuth: StartStravaAuthUseCase,
    val completeAuth: CompleteStravaAuthUseCase,
)

/** Strava connection (Phase 5, T5.1–T5.2): set up the rider's API app, then authorize RideComp. */
class StravaViewModel(private val useCases: StravaUseCases) :
    UdfViewModel<StravaUiState, StravaUiEvent, StravaEffect>(StravaUiState()) {
    init {
        viewModelScope.launch {
            useCases.observeStatus().collect { status -> updateState { copy(status = status) } }
        }
    }

    override fun onEvent(event: StravaUiEvent) {
        when (event) {
            is StravaUiEvent.ClientIdChanged -> updateState { copy(clientId = event.value, appError = null) }
            is StravaUiEvent.ClientSecretChanged -> updateState { copy(clientSecret = event.value, appError = null) }
            StravaUiEvent.SaveApp -> saveApp()
            StravaUiEvent.EditApp -> updateState { copy(isEditingApp = true, clientId = "", clientSecret = "") }
            StravaUiEvent.CancelEdit -> updateState { copy(isEditingApp = false, clientSecret = "", appError = null) }
            StravaUiEvent.Connect -> work {
                useCases.startAuth()?.let { sendEffect(StravaEffect.OpenUrl(it)) }
            }
            // Never dropped, even while another action runs: the code is single use.
            StravaUiEvent.OpenApiPage -> sendEffect(StravaEffect.OpenUrl(StravaConfig.API_SETTINGS_URL))
            StravaUiEvent.OpenFailed -> updateState { copy(message = StravaMessage.NO_BROWSER) }
            is StravaUiEvent.Redirect -> work(isForced = true) { complete(event.redirect) }
            StravaUiEvent.MessageShown -> updateState { copy(message = null) }
        }
    }

    private fun saveApp() {
        val state = uiState.value
        work {
            when (val result = useCases.saveApp(state.clientId, state.clientSecret)) {
                // The secret leaves the screen state as soon as it is stored.
                is Outcome.Success -> updateState { copy(isEditingApp = false, clientId = "", clientSecret = "") }
                is Outcome.Failure -> updateState { copy(appError = result.error) }
            }
        }
    }

    private suspend fun complete(redirect: StravaAuthRedirect) {
        val message = when (val result = useCases.completeAuth(redirect)) {
            is Outcome.Success -> StravaMessage.CONNECTED
            is Outcome.Failure -> when (result.error) {
                // A redirect nobody asked for is ignored silently.
                StravaAuthError.NOT_STARTED -> null
                StravaAuthError.DENIED -> StravaMessage.DENIED
                StravaAuthError.MISSING_SCOPE -> StravaMessage.MISSING_SCOPE
                StravaAuthError.OFFLINE -> StravaMessage.OFFLINE
                StravaAuthError.REJECTED -> StravaMessage.REJECTED
                StravaAuthError.STATE_MISMATCH, StravaAuthError.FAILED -> StravaMessage.FAILED
            }
        }
        updateState { copy(message = message) }
    }

    /** Runs one action at a time; a storage failure shows a message instead of crashing. */
    private fun work(isForced: Boolean = false, action: suspend () -> Unit) {
        if (uiState.value.isWorking && !isForced) return
        updateState { copy(isWorking = true) }
        viewModelScope.launch {
            runCatchingCancellable { action() }.onFailure { updateState { copy(message = StravaMessage.FAILED) } }
            updateState { copy(isWorking = false) }
        }
    }
}
