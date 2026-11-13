package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.SafetyConfig
import com.ridecomp.core.usecase.AddEmergencyContactUseCase
import com.ridecomp.core.usecase.AddPickedContactUseCase
import com.ridecomp.core.usecase.ObserveSafetyUseCase
import com.ridecomp.core.usecase.RemoveEmergencyContactUseCase
import com.ridecomp.core.usecase.SafetyError
import com.ridecomp.core.usecase.SaveRiderNameUseCase
import com.ridecomp.core.usecase.SendTestSmsUseCase
import com.ridecomp.core.usecase.SetSafetyEnabledUseCase
import com.ridecomp.core.util.Outcome
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One-line feedback below the controls. */
sealed interface SafetyMessage {
    data class Error(val error: SafetyError) : SafetyMessage

    data class TestSent(val count: Int) : SafetyMessage
}

@Immutable
data class SafetyUiState(
    val isEnabled: Boolean = false,
    val riderName: String = "",
    val contacts: ImmutableList<EmergencyContact> = persistentListOf(),
    val newName: String = "",
    val newPhone: String = "",
    val isSendingTest: Boolean = false,
    val message: SafetyMessage? = null,
    val isLoaded: Boolean = false,
) {
    val canAddContact: Boolean get() = contacts.size < SafetyConfig.MAX_CONTACTS
}

sealed interface SafetyUiEvent {
    data class Enable(val isEnabled: Boolean) : SafetyUiEvent

    /** The SMS permission dialog closed: try enabling again (it reports if still missing). */
    data object SmsPermissionResult : SafetyUiEvent

    data class RiderName(val name: String) : SafetyUiEvent

    data class NewName(val name: String) : SafetyUiEvent

    data class NewPhone(val phone: String) : SafetyUiEvent

    data object AddTyped : SafetyUiEvent

    data class Picked(val uri: String) : SafetyUiEvent

    data class Remove(val id: Long) : SafetyUiEvent

    data object SendTest : SafetyUiEvent

    data object MessageShown : SafetyUiEvent
}

sealed interface SafetyEffect {
    /** Ask for SEND_SMS now, while the rider turns the feature on (SEC-SMS-01). */
    data object RequestSmsPermission : SafetyEffect
}

/** Safety screen (T2.14, 12-FEATURE-SPECS §5.1). */
/** Use cases of the Safety screen, grouped to keep the constructor short. */
data class SafetyUseCases(
    val observeSafety: ObserveSafetyUseCase,
    val setEnabled: SetSafetyEnabledUseCase,
    val saveRiderName: SaveRiderNameUseCase,
    val addContact: AddEmergencyContactUseCase,
    val addPicked: AddPickedContactUseCase,
    val removeContact: RemoveEmergencyContactUseCase,
    val sendTest: SendTestSmsUseCase,
)

class SafetyViewModel(private val useCases: SafetyUseCases) :
    UdfViewModel<SafetyUiState, SafetyUiEvent, SafetyEffect>(SafetyUiState()) {
    init {
        viewModelScope.launch {
            // The name field is edited locally after the first load, so typing is never overwritten.
            val first = useCases.observeSafety().first()
            updateState { copy(riderName = first.settings.riderName, isLoaded = true) }
            useCases.observeSafety().collect { safety ->
                updateState {
                    copy(isEnabled = safety.settings.isEnabled, contacts = safety.contacts.toImmutableList())
                }
            }
        }
    }

    override fun onEvent(event: SafetyUiEvent) {
        when (event) {
            is SafetyUiEvent.Enable -> enable(event.isEnabled, mayAskPermission = true)
            SafetyUiEvent.SmsPermissionResult -> enable(isEnabled = true, mayAskPermission = false)
            is SafetyUiEvent.RiderName -> {
                updateState { copy(riderName = event.name.take(SafetyConfig.NAME_MAX_LENGTH)) }
                viewModelScope.launch { useCases.saveRiderName(event.name) }
            }
            is SafetyUiEvent.NewName -> updateState { copy(newName = event.name.take(SafetyConfig.NAME_MAX_LENGTH)) }
            is SafetyUiEvent.NewPhone -> updateState { copy(newPhone = event.phone) }
            SafetyUiEvent.AddTyped -> add { useCases.addContact(currentState.newName, currentState.newPhone) }
            is SafetyUiEvent.Picked -> add { useCases.addPicked(event.uri) }
            is SafetyUiEvent.Remove -> viewModelScope.launch { useCases.removeContact(event.id) }
            SafetyUiEvent.SendTest -> sendTestSms()
            SafetyUiEvent.MessageShown -> updateState { copy(message = null) }
        }
    }

    private fun enable(isEnabled: Boolean, mayAskPermission: Boolean) {
        viewModelScope.launch {
            val result = useCases.setEnabled(isEnabled)
            if (result is Outcome.Failure) {
                if (result.error == SafetyError.NO_SMS_PERMISSION && mayAskPermission) {
                    sendEffect(SafetyEffect.RequestSmsPermission)
                } else {
                    updateState { copy(message = SafetyMessage.Error(result.error)) }
                }
            }
        }
    }

    private fun add(action: suspend () -> Outcome<Unit, SafetyError>) {
        viewModelScope.launch {
            when (val result = action()) {
                is Outcome.Success -> updateState { copy(newName = "", newPhone = "", message = null) }
                is Outcome.Failure -> updateState { copy(message = SafetyMessage.Error(result.error)) }
            }
        }
    }

    private fun sendTestSms() {
        if (currentState.isSendingTest) return
        updateState { copy(isSendingTest = true) }
        viewModelScope.launch {
            val message = when (val result = useCases.sendTest()) {
                is Outcome.Success -> SafetyMessage.TestSent(result.value)
                is Outcome.Failure -> SafetyMessage.Error(result.error)
            }
            updateState { copy(isSendingTest = false, message = message) }
        }
    }
}
