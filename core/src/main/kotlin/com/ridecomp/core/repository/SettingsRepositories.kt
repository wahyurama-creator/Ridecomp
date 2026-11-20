package com.ridecomp.core.repository

import com.ridecomp.core.engine.route.NewRoute
import com.ridecomp.core.engine.route.Route
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RoutePoint
import com.ridecomp.core.engine.route.RouteTurn
import com.ridecomp.core.model.ActivePlanState
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.AppTheme
import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.ComponentWear
import com.ridecomp.core.model.CustomPlan
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.IncidentRecord
import com.ridecomp.core.model.PlanSession
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

    /** Whether a profile was saved; false while onboarding's profile step was skipped (T10.12). */
    public fun observeHasProfile(): Flow<Boolean>
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

    /** The chosen app theme (T11.9); [AppTheme.DEFAULT] until one is chosen. */
    public fun observeTheme(): Flow<AppTheme>

    public suspend fun setTheme(theme: AppTheme)

    /** True once an online map was shown, so its tiles may be in the cache (T11.11). */
    public fun observeMapCacheFilled(): Flow<Boolean>

    public suspend fun setMapCacheFilled(isFilled: Boolean)
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

/** Saved bikes (`bikes`, schema v8, D-89); the last used one is preselected before a ride. */
public interface BikeRepository {
    /** All bikes in the order they were added. */
    public fun observeAll(): Flow<List<Bike>>

    public suspend fun get(id: Long): Bike?

    /** Inserts when `id == 0`, otherwise updates; returns the id. */
    public suspend fun save(bike: Bike): Long

    public suspend fun delete(id: Long)

    public fun observeLastUsedId(): Flow<Long?>

    public suspend fun setLastUsedId(id: Long)
}

/** Parts of the saved bikes and their wear (`bike_components`, schema v9, T7.3). */
public interface BikeComponentRepository {
    /** Every part of every bike with its distance, updated when rides are saved or deleted. */
    public fun observeWear(): Flow<List<ComponentWear>>

    public suspend fun get(id: Long): BikeComponent?

    /** Inserts when `id == 0`, otherwise updates; returns the id. */
    public suspend fun save(component: BikeComponent): Long

    public suspend fun delete(id: Long)

    /** Total distance of the finished rides on a bike. */
    public fun observeBikeDistanceM(bikeId: Long): Flow<Double>
}

/** Plans the rider built and the plan being followed (T7.4, stored in `settings`). */
public interface TrainingPlanRepository {
    public fun observeCustomPlans(): Flow<List<CustomPlan>>

    /** Inserts when `id == 0`, otherwise updates; returns the id. */
    public suspend fun saveCustomPlan(plan: CustomPlan): Long

    public suspend fun deleteCustomPlan(id: Long)

    public fun observeActivePlan(): Flow<ActivePlanState?>

    /** Null stops following a plan. */
    public suspend fun setActivePlan(state: ActivePlanState?)

    /** The session the current ride is measured against; kept so a recovered ride keeps it (T7.4b). */
    public suspend fun getRideTarget(): PlanSession?

    public suspend fun setRideTarget(session: PlanSession?)
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

    public suspend fun getClimbs(id: Long): List<RouteClimb>

    public suspend fun getTurns(id: Long): List<RouteTurn>

    /** Saves route and points in one transaction; returns the id. */
    public suspend fun save(route: NewRoute): Long

    public suspend fun rename(id: Long, name: String)

    public suspend fun delete(id: Long)
}
