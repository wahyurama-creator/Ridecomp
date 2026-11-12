package com.ridecomp.core.repository

import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import kotlinx.coroutines.flow.Flow

/** Rider profile, single row (05-DATA-MODEL `profile`). */
public interface ProfileRepository {
    /** Emits the stored profile, or defaults when none was saved yet. */
    public fun observe(): Flow<RiderProfile>

    public suspend fun get(): RiderProfile

    public suspend fun save(profile: RiderProfile)

    /** True once the user saved a profile (onboarding done). */
    public suspend fun hasProfile(): Boolean
}

/** App and ride settings (05-DATA-MODEL `settings`). */
public interface SettingsRepository {
    public fun observeRideSettings(): Flow<RideSettings>

    public suspend fun getRideSettings(): RideSettings

    public suspend fun saveRideSettings(settings: RideSettings)

    public fun observeOnboardingDone(): Flow<Boolean>

    public suspend fun setOnboardingDone()

    /** Custom data pages, or null when the rider uses the defaults (T2.8). */
    public fun observeDataPages(): Flow<DataPagesLayout?>

    /** Saves custom pages; null restores the defaults. */
    public suspend fun saveDataPages(layout: DataPagesLayout?)
}
