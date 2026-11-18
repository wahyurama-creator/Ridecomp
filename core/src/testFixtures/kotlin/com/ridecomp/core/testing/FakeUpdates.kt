package com.ridecomp.core.testing

import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.engine.update.UpdatePreferences
import com.ridecomp.core.usecase.AppInfoSource
import com.ridecomp.core.usecase.ReleaseSource
import com.ridecomp.core.usecase.UpdateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [UpdateRepository]. */
public class FakeUpdateRepository(initial: UpdatePreferences = UpdatePreferences()) : UpdateRepository {
    public val state: MutableStateFlow<UpdatePreferences> = MutableStateFlow(initial)

    override fun observe(): Flow<UpdatePreferences> = state

    override suspend fun get(): UpdatePreferences = state.value

    override suspend fun setAutoCheck(isEnabled: Boolean) {
        state.update { it.copy(isAutoCheckEnabled = isEnabled) }
    }

    override suspend fun saveCheck(atMs: Long, latest: AppRelease) {
        state.update { it.copy(lastCheckMs = atMs, latest = latest) }
    }

    override suspend fun dismiss(version: String) {
        state.update { it.copy(dismissedVersion = version) }
    }
}

/** [ReleaseSource] answering [release] (null = network failure); counts the requests. */
public class FakeReleaseSource(public var release: AppRelease? = null) : ReleaseSource {
    public var requests: Int = 0

    override suspend fun latest(): AppRelease? {
        requests++
        return release
    }
}

public class FakeAppInfoSource(override val versionName: String = "0.9.1") : AppInfoSource
