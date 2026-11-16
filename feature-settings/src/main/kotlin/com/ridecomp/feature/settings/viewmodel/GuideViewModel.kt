package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.GuidePreferences
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.core.usecase.DismissTipUseCase
import com.ridecomp.core.usecase.ObserveGuideUseCase
import com.ridecomp.core.usecase.ResetTipsUseCase
import com.ridecomp.core.usecase.SetRiderLevelUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.launch

/** Null [preferences] while loading, so no tip flashes in before the level is known. */
@Immutable
data class GuideUiState(val preferences: GuidePreferences? = null)

sealed interface GuideUiEvent {
    data class SetLevel(val level: RiderLevel) : GuideUiEvent

    data class Dismiss(val tip: GuideTip) : GuideUiEvent

    data object ResetTips : GuideUiEvent
}

/** Rider level, tips and Help (Phase 4G). One instance per screen that shows a tip or the level. */
class GuideViewModel(
    observeGuide: ObserveGuideUseCase,
    private val setLevel: SetRiderLevelUseCase,
    private val dismiss: DismissTipUseCase,
    private val resetTips: ResetTipsUseCase,
) : UdfViewModel<GuideUiState, GuideUiEvent, NoEffect>(GuideUiState()) {
    init {
        viewModelScope.launch { observeGuide().collect { prefs -> updateState { copy(preferences = prefs) } } }
    }

    override fun onEvent(event: GuideUiEvent) {
        viewModelScope.launch {
            when (event) {
                is GuideUiEvent.SetLevel -> setLevel(event.level)
                is GuideUiEvent.Dismiss -> dismiss(event.tip)
                GuideUiEvent.ResetTips -> resetTips()
            }
        }
    }
}
