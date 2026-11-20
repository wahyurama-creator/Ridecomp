package com.ridecomp.core.usecase

import com.ridecomp.core.model.AppTheme
import com.ridecomp.core.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** The chosen app theme, applied at the app root (T11.9). */
public class ObserveAppThemeUseCase(private val settings: SettingsRepository) {
    public operator fun invoke(): Flow<AppTheme> = settings.observeTheme()
}

/** Settings → Display → Theme (T11.9). */
public class SetAppThemeUseCase(private val settings: SettingsRepository) {
    public suspend operator fun invoke(theme: AppTheme) {
        settings.setTheme(theme)
    }
}
