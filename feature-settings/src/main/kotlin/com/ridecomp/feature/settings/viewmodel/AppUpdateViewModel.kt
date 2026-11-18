package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.update.UpdateCheckResult
import com.ridecomp.core.usecase.CheckForUpdateUseCase
import com.ridecomp.core.usecase.ObserveUpdateSettingsUseCase
import com.ridecomp.core.usecase.SetAutoUpdateCheckUseCase
import com.ridecomp.core.usecase.UpdateSettings
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.launch

@Immutable
data class AppUpdateUiState(
    val settings: UpdateSettings? = null,
    val isChecking: Boolean = false,
    /** Result of the last "Cek sekarang" on this screen; null before any. */
    val lastResult: UpdateCheckResult? = null,
)

sealed interface AppUpdateUiEvent {
    data class SetAutoCheck(val isEnabled: Boolean) : AppUpdateUiEvent

    data object CheckNow : AppUpdateUiEvent

    data object OpenRelease : AppUpdateUiEvent
}

sealed interface AppUpdateEffect {
    data class OpenUrl(val url: String) : AppUpdateEffect
}

/** Use cases of Setelan → Pembaruan aplikasi. */
data class AppUpdateUseCases(
    val observe: ObserveUpdateSettingsUseCase,
    val setAutoCheck: SetAutoUpdateCheckUseCase,
    val check: CheckForUpdateUseCase,
)

/** Installed version, the daily automatic check switch and "Cek sekarang" (T10.9, D-103). */
class AppUpdateViewModel(private val useCases: AppUpdateUseCases) :
    UdfViewModel<AppUpdateUiState, AppUpdateUiEvent, AppUpdateEffect>(AppUpdateUiState()) {
    init {
        viewModelScope.launch { useCases.observe().collect { settings -> updateState { copy(settings = settings) } } }
    }

    override fun onEvent(event: AppUpdateUiEvent) {
        when (event) {
            is AppUpdateUiEvent.SetAutoCheck -> viewModelScope.launch { useCases.setAutoCheck(event.isEnabled) }
            AppUpdateUiEvent.CheckNow -> checkNow()
            AppUpdateUiEvent.OpenRelease ->
                currentState.settings?.available?.let { sendEffect(AppUpdateEffect.OpenUrl(it.pageUrl)) }
        }
    }

    private fun checkNow() {
        if (currentState.isChecking) return
        updateState { copy(isChecking = true, lastResult = null) }
        viewModelScope.launch {
            val result = useCases.check(force = true)
            updateState { copy(isChecking = false, lastResult = result) }
        }
    }
}
