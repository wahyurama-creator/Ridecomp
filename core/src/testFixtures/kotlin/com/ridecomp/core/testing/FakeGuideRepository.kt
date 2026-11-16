package com.ridecomp.core.testing

import com.ridecomp.core.model.GuidePreferences
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.core.repository.GuideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [GuideRepository]. */
public class FakeGuideRepository(initial: GuidePreferences = GuidePreferences()) : GuideRepository {
    public val state: MutableStateFlow<GuidePreferences> = MutableStateFlow(initial)

    override fun observe(): Flow<GuidePreferences> = state

    override suspend fun setLevel(level: RiderLevel) {
        state.value = state.value.copy(level = level)
    }

    override suspend fun dismiss(tip: GuideTip) {
        state.value = state.value.copy(dismissed = state.value.dismissed + tip)
    }

    override suspend fun resetTips() {
        state.value = state.value.copy(dismissed = emptySet())
    }
}
