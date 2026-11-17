package com.ridecomp.feature.settings.viewmodel

import com.ridecomp.core.model.AppLanguage
import com.ridecomp.core.usecase.GetAppLanguageUseCase
import com.ridecomp.core.usecase.SetAppLanguageUseCase
import com.ridecomp.ui.udf.UdfViewModel

sealed interface LanguageEffect {
    /** Android 8–12: the screen must be recreated to show the new language (13+ does it itself). */
    data object Recreate : LanguageEffect
}

/** App language (D-88): in onboarding and Settings. */
class LanguageViewModel(getLanguage: GetAppLanguageUseCase, private val setLanguage: SetAppLanguageUseCase) :
    UdfViewModel<AppLanguage, AppLanguage, LanguageEffect>(getLanguage()) {
    override fun onEvent(event: AppLanguage) {
        if (event == uiState.value) return
        setLanguage(event)
        updateState { event }
        sendEffect(LanguageEffect.Recreate)
    }
}
