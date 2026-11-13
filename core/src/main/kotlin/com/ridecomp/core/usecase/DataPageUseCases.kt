package com.ridecomp.core.usecase

import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.sanitized
import com.ridecomp.core.repository.ActivityProfileRepository
import com.ridecomp.core.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The data pages to show, sanitized against [knownKeys] (the metrics this app version knows), or null for
 * the built-in defaults (T2.8). With a profile, its own pages win over the general pages (T2.13).
 */
public class ObserveDataPagesUseCase(
    private val settings: SettingsRepository,
    private val profiles: ActivityProfileRepository,
) {
    public operator fun invoke(knownKeys: Set<String>, profileId: Long? = null): Flow<DataPagesLayout?> {
        val general = settings.observeDataPages()
        val own = if (profileId == null) {
            flowOf(null)
        } else {
            profiles.observeAll().map { all -> all.firstOrNull { it.id == profileId }?.dataPages }
        }
        return combine(own, general) { profilePages, generalPages ->
            profilePages?.sanitized(knownKeys) ?: generalPages?.sanitized(knownKeys)
        }
    }
}

/** Where edited pages are saved: the general pages, or one profile's own pages. */
public class SaveDataPagesUseCase(
    private val settings: SettingsRepository,
    private val profiles: ActivityProfileRepository,
) {
    public suspend operator fun invoke(layout: DataPagesLayout, knownKeys: Set<String>, profileId: Long? = null) {
        val clean = layout.sanitized(knownKeys)
        if (profileId == null) {
            settings.saveDataPages(clean)
        } else {
            profiles.get(profileId)?.let { profiles.save(it.copy(dataPages = clean)) }
        }
    }
}

/** General pages back to the built-in defaults; a profile back to the general pages. */
public class ResetDataPagesUseCase(
    private val settings: SettingsRepository,
    private val profiles: ActivityProfileRepository,
) {
    public suspend operator fun invoke(profileId: Long? = null) {
        if (profileId == null) {
            settings.saveDataPages(null)
        } else {
            profiles.get(profileId)?.let { profiles.save(it.copy(dataPages = null)) }
        }
    }
}
