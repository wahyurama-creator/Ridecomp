package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.backup.BackupError
import com.ridecomp.core.usecase.BackupFileNames
import com.ridecomp.core.usecase.BackupSummary
import com.ridecomp.core.usecase.CreateBackupUseCase
import com.ridecomp.core.usecase.ObserveLastBackupUseCase
import com.ridecomp.core.usecase.ReadBackupSummaryUseCase
import com.ridecomp.core.usecase.RestartAfterRestoreUseCase
import com.ridecomp.core.usecase.RestoreBackupUseCase
import com.ridecomp.core.usecase.SuggestBackupFileNameUseCase
import com.ridecomp.core.util.Outcome
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.launch

/** Use cases of the backup screen, grouped to keep the constructor short. */
data class BackupUseCases(
    val observeLastBackup: ObserveLastBackupUseCase,
    val suggestFileName: SuggestBackupFileNameUseCase,
    val create: CreateBackupUseCase,
    val readSummary: ReadBackupSummaryUseCase,
    val restore: RestoreBackupUseCase,
    val restart: RestartAfterRestoreUseCase,
)

sealed interface BackupMessage {
    data class Created(val activityCount: Int) : BackupMessage

    data class Failed(val error: BackupError) : BackupMessage
}

/** Restore steps (12-FEATURE-SPECS §12.5): file → password (if encrypted) → summary → 2-step confirm → restart. */
sealed interface RestoreStep {
    data class NeedsPassword(val uri: String) : RestoreStep

    data class Summary(val uri: String, val summary: BackupSummary, val isFinalConfirm: Boolean = false) : RestoreStep

    data object Done : RestoreStep
}

@Immutable
data class BackupUiState(
    val lastBackupAtMs: Long? = null,
    val isPasswordEnabled: Boolean = true,
    val password: String = "",
    val passwordConfirm: String = "",
    val restorePassword: String = "",
    val restore: RestoreStep? = null,
    val isWorking: Boolean = false,
    val message: BackupMessage? = null,
) {
    val canCreate: Boolean
        get() = !isWorking &&
            (
                !isPasswordEnabled ||
                    (password.length >= BackupFileNames.MIN_PASSWORD_LENGTH && password == passwordConfirm)
                )
}

sealed interface BackupUiEvent {
    data class PasswordEnabled(val isEnabled: Boolean) : BackupUiEvent

    data class Password(val value: String) : BackupUiEvent

    data class PasswordConfirm(val value: String) : BackupUiEvent

    /** SAF "create document" returned [uri]. */
    data class CreateTo(val uri: String) : BackupUiEvent

    /** SAF "open document" returned [uri]. */
    data class OpenFrom(val uri: String) : BackupUiEvent

    data class RestorePassword(val value: String) : BackupUiEvent

    data object Unlock : BackupUiEvent

    data object ConfirmRestore : BackupUiEvent

    data object CancelRestore : BackupUiEvent

    data object Restart : BackupUiEvent

    data object MessageShown : BackupUiEvent
}

/**
 * Backup & restore screen (T2.17). Passwords live only in this state while typed and are handed to the
 * use cases as char arrays that are wiped after use; they are never stored (SEC-BK-02).
 */
class BackupViewModel(private val useCases: BackupUseCases) :
    UdfViewModel<BackupUiState, BackupUiEvent, NoEffect>(BackupUiState()) {

    /** Name proposed to the SAF picker. */
    val suggestedFileName: String get() = useCases.suggestFileName()

    init {
        viewModelScope.launch { useCases.observeLastBackup().collect { updateState { copy(lastBackupAtMs = it) } } }
    }

    override fun onEvent(event: BackupUiEvent) {
        when (event) {
            is BackupUiEvent.PasswordEnabled -> updateState { copy(isPasswordEnabled = event.isEnabled) }
            is BackupUiEvent.Password -> updateState { copy(password = event.value) }
            is BackupUiEvent.PasswordConfirm -> updateState { copy(passwordConfirm = event.value) }
            is BackupUiEvent.CreateTo -> create(event.uri)
            is BackupUiEvent.OpenFrom -> open(event.uri)
            is BackupUiEvent.RestorePassword -> updateState { copy(restorePassword = event.value) }
            BackupUiEvent.Unlock ->
                (currentState.restore as? RestoreStep.NeedsPassword)?.let { step -> work { readSummary(step.uri) } }
            BackupUiEvent.ConfirmRestore -> confirmRestore()
            BackupUiEvent.CancelRestore -> updateState { copy(restore = null, restorePassword = "") }
            BackupUiEvent.Restart -> useCases.restart()
            BackupUiEvent.MessageShown -> updateState { copy(message = null) }
        }
    }

    private fun create(uri: String) {
        if (!currentState.canCreate) return
        val password = currentState.password.takeIf { currentState.isPasswordEnabled }?.toCharArray()
        work {
            val message = when (val result = useCases.create(uri, password)) {
                is Outcome.Success -> BackupMessage.Created(result.value)
                is Outcome.Failure -> BackupMessage.Failed(result.error)
            }
            updateState { copy(message = message, password = "", passwordConfirm = "") }
        }
    }

    private fun open(uri: String) {
        work {
            if (useCases.readSummary.isEncrypted(uri)) {
                updateState { copy(restore = RestoreStep.NeedsPassword(uri), restorePassword = "") }
            } else {
                readSummary(uri)
            }
        }
    }

    private suspend fun readSummary(uri: String) {
        val password = currentState.restorePassword.takeIf { it.isNotEmpty() }?.toCharArray()
        when (val result = useCases.readSummary(uri, password)) {
            is Outcome.Success -> updateState { copy(restore = RestoreStep.Summary(uri, result.value)) }
            is Outcome.Failure -> updateState { copy(message = BackupMessage.Failed(result.error)) }
        }
        password?.fill(CLEARED)
    }

    /** Two steps: the first press only arms the final confirmation (12-FEATURE-SPECS §12.5 step 3). */
    private fun confirmRestore() {
        val step = currentState.restore as? RestoreStep.Summary ?: return
        if (!step.isFinalConfirm) {
            updateState { copy(restore = step.copy(isFinalConfirm = true)) }
            return
        }
        val password = currentState.restorePassword.takeIf { step.summary.isEncrypted }?.toCharArray()
        work {
            when (val result = useCases.restore(step.uri, password)) {
                is Outcome.Success -> updateState { copy(restore = RestoreStep.Done, restorePassword = "") }
                is Outcome.Failure ->
                    updateState { copy(restore = null, message = BackupMessage.Failed(result.error)) }
            }
        }
    }

    private fun work(block: suspend () -> Unit) {
        if (currentState.isWorking) return
        updateState { copy(isWorking = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                updateState { copy(isWorking = false) }
            }
        }
    }

    private companion object {
        const val CLEARED = '\u0000'
    }
}
