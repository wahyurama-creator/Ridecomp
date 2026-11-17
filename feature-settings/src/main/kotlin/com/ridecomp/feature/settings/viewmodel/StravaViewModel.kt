package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.usecase.CompleteStravaAuthUseCase
import com.ridecomp.core.usecase.DisconnectStravaUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
import com.ridecomp.core.usecase.SetStravaAutoUploadUseCase
import com.ridecomp.core.usecase.StartStravaAuthUseCase
import com.ridecomp.core.usecase.StravaAuthError
import com.ridecomp.core.usecase.StravaAuthRedirect
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.runCatchingCancellable
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.launch

/** One-time message of the Strava screen. */
enum class StravaMessage {
    CONNECTED,
    DENIED,
    MISSING_SCOPE,
    OFFLINE,
    REJECTED,
    FAILED,
    NO_BROWSER,
    DISCONNECTED,

    /** Tokens forgotten here, but Strava could not be told (offline). */
    DISCONNECTED_LOCALLY,
}

@Immutable
data class StravaUiState(
    /** Null while loading. */
    val status: StravaStatus? = null,
    val isWorking: Boolean = false,
    val message: StravaMessage? = null,
)

sealed interface StravaUiEvent {
    data object Connect : StravaUiEvent

    data class SetAutoUpload(val isOn: Boolean) : StravaUiEvent

    data object Disconnect : StravaUiEvent

    /** No app could open the authorize page. */
    data object OpenFailed : StravaUiEvent

    /** The redirect from Strava, already matched to `ridecomp://localhost/strava-auth` by the app. */
    data class Redirect(val redirect: StravaAuthRedirect) : StravaUiEvent

    data object MessageShown : StravaUiEvent
}

sealed interface StravaEffect {
    /** Opens the authorize page in the Strava app or the browser (SEC-AUTH-01). */
    data class OpenUrl(val url: String) : StravaEffect
}

/** Use cases of the Strava screen, grouped to keep the constructor short (KT-05). */
data class StravaUseCases(
    val observeStatus: ObserveStravaStatusUseCase,
    val startAuth: StartStravaAuthUseCase,
    val completeAuth: CompleteStravaAuthUseCase,
    val setAutoUpload: SetStravaAutoUploadUseCase,
    val disconnect: DisconnectStravaUseCase,
)

/** Strava connection (Phase 5, D-82): one tap to link the account, auto-upload, disconnect. */
class StravaViewModel(private val useCases: StravaUseCases) :
    UdfViewModel<StravaUiState, StravaUiEvent, StravaEffect>(StravaUiState()) {
    init {
        viewModelScope.launch {
            useCases.observeStatus().collect { status -> updateState { copy(status = status) } }
        }
    }

    override fun onEvent(event: StravaUiEvent) {
        when (event) {
            StravaUiEvent.Connect -> work {
                useCases.startAuth()?.let { sendEffect(StravaEffect.OpenUrl(it)) }
            }
            is StravaUiEvent.SetAutoUpload -> work { useCases.setAutoUpload(event.isOn) }
            StravaUiEvent.Disconnect -> work {
                val isRevoked = useCases.disconnect()
                updateState {
                    copy(message = if (isRevoked) StravaMessage.DISCONNECTED else StravaMessage.DISCONNECTED_LOCALLY)
                }
            }
            StravaUiEvent.OpenFailed -> updateState { copy(message = StravaMessage.NO_BROWSER) }
            // Never dropped, even while another action runs: the code is single use.
            is StravaUiEvent.Redirect -> work(isForced = true) { complete(event.redirect) }
            StravaUiEvent.MessageShown -> updateState { copy(message = null) }
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
