package com.ridecomp.core.usecase

import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.engine.update.AppVersion
import com.ridecomp.core.engine.update.UpdateCheckResult
import com.ridecomp.core.engine.update.UpdateConfig
import com.ridecomp.core.engine.update.UpdatePreferences
import com.ridecomp.core.source.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Update check state, stored with the settings. Implemented in :data. */
public interface UpdateRepository {
    public fun observe(): Flow<UpdatePreferences>

    public suspend fun get(): UpdatePreferences

    public suspend fun setAutoCheck(isEnabled: Boolean)

    public suspend fun saveCheck(atMs: Long, latest: AppRelease)

    public suspend fun dismiss(version: String)
}

/** The newest published release (GitHub), or null when it cannot be read. Implemented in :platform. */
public interface ReleaseSource {
    public suspend fun latest(): AppRelease?
}

/** The installed app's version name (e.g. "0.9.1"). Implemented in :platform. */
public interface AppInfoSource {
    public val versionName: String
}

/** True when [release] is newer than the installed [versionName]. */
internal fun AppRelease.isNewerThan(versionName: String): Boolean {
    val available = AppVersion.parse(version)
    val installed = AppVersion.parse(versionName)
    return available != null && installed != null && available > installed
}

/**
 * Looks for a new release (T10.9, D-103): automatically when the app is opened, at most once a day and only
 * when the rider left it on; or right away with [force] ("Cek sekarang"). A failed check changes nothing.
 */
public class CheckForUpdateUseCase(
    private val repository: UpdateRepository,
    private val releases: ReleaseSource,
    private val app: AppInfoSource,
    private val clock: Clock,
) {
    public suspend operator fun invoke(force: Boolean = false): UpdateCheckResult? {
        val preferences = repository.get()
        val now = clock.nowMs()
        val isDue = preferences.lastCheckMs?.let { now - it >= UpdateConfig.CHECK_INTERVAL_MS } ?: true
        if (!force && (!preferences.isAutoCheckEnabled || !isDue)) return null
        val latest = releases.latest()
        latest?.let { repository.saveCheck(now, it) }
        return when {
            latest == null -> UpdateCheckResult.FAILED
            latest.isNewerThan(app.versionName) -> UpdateCheckResult.UPDATE_AVAILABLE
            else -> UpdateCheckResult.UP_TO_DATE
        }
    }
}

/** The release to offer on the home screen: newer than this app and not put off with "Nanti". */
public class ObserveAvailableUpdateUseCase(private val repository: UpdateRepository, private val app: AppInfoSource) {
    public operator fun invoke(): Flow<AppRelease?> = repository.observe().map { preferences ->
        preferences.latest?.takeIf { it.isNewerThan(app.versionName) && it.version != preferences.dismissedVersion }
    }.distinctUntilChanged()
}

/** Update check settings with the installed version, for Setelan → Pembaruan aplikasi. */
public class ObserveUpdateSettingsUseCase(private val repository: UpdateRepository, private val app: AppInfoSource) {
    public operator fun invoke(): Flow<UpdateSettings> = repository.observe().map { preferences ->
        UpdateSettings(
            installedVersion = app.versionName,
            isAutoCheckEnabled = preferences.isAutoCheckEnabled,
            lastCheckMs = preferences.lastCheckMs,
            available = preferences.latest?.takeIf { it.isNewerThan(app.versionName) },
        )
    }.distinctUntilChanged()
}

public data class UpdateSettings(
    val installedVersion: String,
    val isAutoCheckEnabled: Boolean,
    val lastCheckMs: Long?,
    val available: AppRelease?,
)

public class SetAutoUpdateCheckUseCase(private val repository: UpdateRepository) {
    public suspend operator fun invoke(isEnabled: Boolean) {
        repository.setAutoCheck(isEnabled)
    }
}

/** "Nanti": hides this version's card; a later release shows again. */
public class DismissUpdateUseCase(private val repository: UpdateRepository) {
    public suspend operator fun invoke(version: String) {
        repository.dismiss(version)
    }
}
