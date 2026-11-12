package com.ridecomp.core.usecase

import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.sanitized
import com.ridecomp.core.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The rider's data pages, already sanitized against [knownKeys] (the metrics this app version knows),
 * or null for the defaults (T2.8).
 */
public class ObserveDataPagesUseCase(private val repository: SettingsRepository) {
    public operator fun invoke(knownKeys: Set<String>): Flow<DataPagesLayout?> =
        repository.observeDataPages().map { it?.sanitized(knownKeys) }
}

/** Saves edited pages after sanitizing them; an empty result restores the defaults. */
public class SaveDataPagesUseCase(private val repository: SettingsRepository) {
    public suspend operator fun invoke(layout: DataPagesLayout, knownKeys: Set<String>) {
        repository.saveDataPages(layout.sanitized(knownKeys))
    }
}

public class ResetDataPagesUseCase(private val repository: SettingsRepository) {
    public suspend operator fun invoke() {
        repository.saveDataPages(null)
    }
}
