package com.ridecomp.feature.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.AppTheme
import com.ridecomp.core.usecase.ObserveAppThemeUseCase
import com.ridecomp.core.usecase.SetAppThemeUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.launch

/** Settings → Display (T11.9): the theme; the app root applies a change at once. */
class DisplayViewModel(observeTheme: ObserveAppThemeUseCase, private val setTheme: SetAppThemeUseCase) :
    UdfViewModel<AppTheme, AppTheme, NoEffect>(AppTheme.DEFAULT) {
    init {
        viewModelScope.launch { observeTheme().collect { theme -> updateState { theme } } }
    }

    override fun onEvent(event: AppTheme) {
        if (event == uiState.value) return
        viewModelScope.launch { setTheme(event) }
    }
}
