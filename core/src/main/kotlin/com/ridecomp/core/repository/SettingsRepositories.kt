package com.ridecomp.core.repository

import com.ridecomp.core.engine.route.NewRoute
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.IncidentRecord
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.SafetySettings
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

/** Activity profiles (05-DATA-MODEL `activity_profiles`) and the last one used (`settings`). */
public interface ActivityProfileRepository {
    /** All profiles in display order. */
    public fun observeAll(): Flow<List<ActivityProfile>>

    public suspend fun get(id: Long): ActivityProfile?

    /** Inserts when `id == 0`, otherwise updates; returns the id. */
    public suspend fun save(profile: ActivityProfile): Long

    public suspend fun delete(id: Long)

    public fun observeLastUsedId(): Flow<Long?>

    public suspend fun setLastUsedId(id: Long)
}

/** Safety settings and emergency contacts (05-DATA-MODEL `emergency_contacts`). */
public interface SafetyRepository {
    public fun observeSettings(): Flow<SafetySettings>

    public suspend fun getSettings(): SafetySettings

    public suspend fun saveSettings(settings: SafetySettings)

    public fun observeContacts(): Flow<List<EmergencyContact>>

    public suspend fun getContacts(): List<EmergencyContact>

    public suspend fun addContact(contact: EmergencyContact)

    public suspend fun removeContact(id: Long)

    public suspend fun getLastTestSmsAtMs(): Long?

    public suspend fun setLastTestSmsAtMs(atMs: Long)
}

/** Incidents of a ride (05-DATA-MODEL `incidents`). */
public interface IncidentRepository {
    /** Inserts when `id == 0`, otherwise updates; returns the id. */
    public suspend fun save(incident: IncidentRecord): Long

    public suspend fun getForActivity(activityId: Long): List<IncidentRecord>
}

/** Imported routes and their points (05-DATA-MODEL `routes`, `route_points`). */
public interface RouteRepository {
    public fun observeAll(): Flow<List<Route>>

    public fun observe(id: Long): Flow<Route?>

    public suspend fun getPoints(id: Long): List<RoutePoint>

    /** Saves route and points in one transaction; returns the id. */
    public suspend fun save(route: NewRoute): Long

    public suspend fun rename(id: Long, name: String)

    public suspend fun delete(id: Long)
}
