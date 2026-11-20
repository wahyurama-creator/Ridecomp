package com.ridecomp.core.testing

import com.ridecomp.core.model.ActivePlanState
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.AppTheme
import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.BuiltInProfiles
import com.ridecomp.core.model.ComponentWear
import com.ridecomp.core.model.CustomPlan
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.PeriodTotals
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.repository.ActivityAnalysisRepository
import com.ridecomp.core.repository.ActivityProfileRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BikeComponentRepository
import com.ridecomp.core.repository.BikeRepository
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.repository.PendingAnalysis
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.repository.TrainingPlanRepository
import com.ridecomp.core.source.IdGenerator
import com.ridecomp.core.source.RideNameProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory [ActivityRepository]. Set [isFailing] to simulate a broken database (STB-08). */
public class FakeActivityRepository : ActivityRepository {
    public var isFailing: Boolean = false
    private val activities = MutableStateFlow<Map<Long, Activity>>(emptyMap())
    public val samples: MutableMap<Long, MutableList<RideSample>> = mutableMapOf()
    public val events: MutableMap<Long, MutableList<RideEvent>> = mutableMapOf()
    public val laps: MutableMap<Long, MutableList<Lap>> = mutableMapOf()
    public var checkpointCount: Int = 0
        private set
    private var nextId = 1L

    private fun failIfNeeded() {
        if (isFailing) error("simulated database failure")
    }

    private fun update(id: Long, change: (Activity) -> Activity) {
        failIfNeeded()
        activities.value = activities.value + (id to change(requireNotNull(activities.value[id])))
    }

    /** Puts a stored activity directly (e.g. a ride left recording by a crash). */
    public fun put(activity: Activity) {
        activities.value = activities.value + (activity.id to activity)
        nextId = maxOf(nextId, activity.id + 1)
    }

    override suspend fun create(activity: NewActivity): Long {
        failIfNeeded()
        val id = nextId++
        put(
            Activity(
                id = id,
                uuid = activity.uuid,
                name = activity.name,
                status = ActivityStatus.RECORDING,
                startedAtMs = activity.startedAtMs,
                endedAtMs = null,
                totals = RideTotals(),
                lastCheckpointAtMs = activity.startedAtMs,
                profileId = activity.profileId,
                routeId = activity.routeId,
            ),
        )
        return id
    }

    override suspend fun appendSamples(activityId: Long, samples: List<RideSample>) {
        failIfNeeded()
        val list = this.samples.getOrPut(activityId) { mutableListOf() }
        list.removeAll { existing -> samples.any { it.timeMs == existing.timeMs } }
        list += samples
        list.sortBy { it.timeMs }
    }

    override suspend fun appendEvents(activityId: Long, events: List<RideEvent>) {
        failIfNeeded()
        this.events.getOrPut(activityId) { mutableListOf() } += events
    }

    override suspend fun saveLap(activityId: Long, lap: Lap) {
        failIfNeeded()
        laps.getOrPut(activityId) { mutableListOf() } += lap
    }

    override suspend fun checkpoint(activityId: Long, status: ActivityStatus, totals: RideTotals, atMs: Long) {
        update(activityId) { it.copy(status = status, totals = totals, lastCheckpointAtMs = atMs) }
        checkpointCount++
    }

    override suspend fun finish(activityId: Long, name: String, totals: RideTotals, endedAtMs: Long) {
        update(activityId) {
            it.copy(status = ActivityStatus.FINISHED, name = name, totals = totals, endedAtMs = endedAtMs)
        }
    }

    override suspend fun delete(activityId: Long) {
        failIfNeeded()
        activities.value = activities.value - activityId
        samples.remove(activityId)
        events.remove(activityId)
        laps.remove(activityId)
    }

    override suspend fun rename(activityId: Long, name: String) {
        update(activityId) { it.copy(name = name) }
    }

    override suspend fun markFitExported(activityId: Long, fitPath: String, atMs: Long) {
        update(activityId) { it.copy(fitPath = fitPath, fitExportedAtMs = atMs) }
    }

    override suspend fun get(activityId: Long): Activity? = activities.value[activityId]

    override fun observe(activityId: Long): Flow<Activity?> = activities.map { it[activityId] }

    override suspend fun findRecoverable(): Activity? = activities.value.values
        .filter { it.status == ActivityStatus.RECORDING || it.status == ActivityStatus.PAUSED }
        .maxByOrNull { it.startedAtMs }

    override fun observeFinished(limit: Int, offset: Int): Flow<List<Activity>> = activities.map { all ->
        all.values.filter { it.status == ActivityStatus.FINISHED }
            .sortedByDescending { it.startedAtMs }
            .drop(offset)
            .take(limit)
    }

    override fun observeFinishedCount(): Flow<Int> =
        activities.map { all -> all.values.count { it.status == ActivityStatus.FINISHED } }

    override fun observeTotalsSince(sinceMs: Long): Flow<PeriodTotals> = activities.map { all ->
        val rides = all.values.filter { it.status == ActivityStatus.FINISHED && it.startedAtMs >= sinceMs }
        PeriodTotals(rides.size, rides.sumOf { it.totals.distanceM }, rides.sumOf { it.totals.ascentM })
    }

    override suspend fun getLaps(activityId: Long): List<Lap> = laps[activityId].orEmpty().toList()

    override suspend fun getEvents(activityId: Long): List<RideEvent> = events[activityId].orEmpty().toList()

    override suspend fun getSamples(activityId: Long, afterTimeMs: Long, limit: Int): List<RideSample> =
        samples[activityId].orEmpty().filter { it.timeMs > afterTimeMs }.take(limit)

    override suspend fun getSampleCount(activityId: Long): Int = samples[activityId].orEmpty().size

    /** Analysis version per ride; absent = never analyzed (see [FakeActivityAnalysisRepository]). */
    public val analyzedVersions: MutableMap<Long, Int> = mutableMapOf()

    /** Every stored ride (other fakes read them). */
    public fun all(): Collection<Activity> = activities.value.values
}

/** [ActivityAnalysisRepository] over the rides of a [FakeActivityRepository]. */
public class FakeActivityAnalysisRepository(private val activities: FakeActivityRepository) :
    ActivityAnalysisRepository {
    override suspend fun getPendingAnalysis(version: Int, limit: Int): List<PendingAnalysis> = activities.all()
        .filter { it.status == ActivityStatus.FINISHED && (activities.analyzedVersions[it.id] ?: 0) < version }
        .sortedBy { it.startedAtMs }
        .take(limit)
        .map { PendingAnalysis(it, activities.analyzedVersions[it.id] ?: 0) }

    override suspend fun markAnalyzed(activityId: Long, version: Int, expectedVersion: Int) {
        if ((activities.analyzedVersions[activityId] ?: 0) == expectedVersion) {
            activities.analyzedVersions[activityId] = version
        }
    }

    override suspend fun resetAnalysis(toVersion: Int) {
        activities.analyzedVersions.replaceAll { _, version -> minOf(version, toVersion) }
    }
}

/** In-memory [ProfileRepository]. */
public class FakeProfileRepository(initial: RiderProfile? = null) : ProfileRepository {
    private val profile = MutableStateFlow(initial)

    override fun observe(): Flow<RiderProfile> = profile.map { it ?: RiderProfile() }

    override suspend fun get(): RiderProfile = profile.value ?: RiderProfile()

    override suspend fun save(profile: RiderProfile) {
        this.profile.value = profile
    }

    override suspend fun hasProfile(): Boolean = profile.value != null

    override fun observeHasProfile(): Flow<Boolean> = profile.map { it != null }
}

/** In-memory [SettingsRepository]. */
public class FakeSettingsRepository(initial: RideSettings = RideSettings()) : SettingsRepository {
    private val settings = MutableStateFlow(initial)
    private val onboarding = MutableStateFlow(false)

    override fun observeRideSettings(): Flow<RideSettings> = settings

    override suspend fun getRideSettings(): RideSettings = settings.value

    override suspend fun saveRideSettings(settings: RideSettings) {
        this.settings.value = settings
    }

    override fun observeOnboardingDone(): Flow<Boolean> = onboarding

    override suspend fun setOnboardingDone() {
        onboarding.value = true
    }

    public val dataPages: MutableStateFlow<DataPagesLayout?> = MutableStateFlow(null)

    override fun observeDataPages(): Flow<DataPagesLayout?> = dataPages

    override suspend fun saveDataPages(layout: DataPagesLayout?) {
        dataPages.value = layout
    }

    public val theme: MutableStateFlow<AppTheme> = MutableStateFlow(AppTheme.DEFAULT)

    override fun observeTheme(): Flow<AppTheme> = theme

    override suspend fun setTheme(theme: AppTheme) {
        this.theme.value = theme
    }

    public val mapCacheFilled: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override fun observeMapCacheFilled(): Flow<Boolean> = mapCacheFilled

    override suspend fun setMapCacheFilled(isFilled: Boolean) {
        mapCacheFilled.value = isFilled
    }
}

/** Sequential ids: "id-1", "id-2"… */
public class FakeIdGenerator : IdGenerator {
    private var next = 1

    override fun newId(): String = "id-${next++}"
}

/** Name "Ride <startedAtMs>". */
public class FakeRideNameProvider : RideNameProvider {
    override fun defaultName(startedAtMs: Long): String = "Ride $startedAtMs"
}

/** In-memory profiles, seeded with the built-ins (ids 1..5 in their order). */
public class FakeActivityProfileRepository(
    seed: List<ActivityProfile> = BuiltInProfiles.ALL.mapIndexed { index, profile -> profile.copy(id = index + 1L) },
) : ActivityProfileRepository {
    private val profiles = MutableStateFlow(seed)
    private val lastUsed = MutableStateFlow<Long?>(null)

    override fun observeAll(): Flow<List<ActivityProfile>> = profiles.map { list -> list.sortedBy { it.sortOrder } }

    override suspend fun get(id: Long): ActivityProfile? = profiles.value.firstOrNull { it.id == id }

    override suspend fun save(profile: ActivityProfile): Long {
        val id = if (profile.id == 0L) (profiles.value.maxOfOrNull { it.id } ?: 0L) + 1 else profile.id
        profiles.value = profiles.value.filterNot { it.id == id } + profile.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        profiles.value = profiles.value.filterNot { it.id == id }
    }

    override fun observeLastUsedId(): Flow<Long?> = lastUsed

    override suspend fun setLastUsedId(id: Long) {
        lastUsed.value = id
    }
}

/** In-memory bikes; seeded like a fresh install with one bike of type Road (id 1). */
public class FakeBikeRepository(seed: List<Bike> = listOf(Bike(id = 1, profileId = 1))) : BikeRepository {
    private val bikes = MutableStateFlow(seed)
    private val lastUsed = MutableStateFlow<Long?>(null)

    override fun observeAll(): Flow<List<Bike>> = bikes.map { list -> list.sortedBy { it.id } }

    override suspend fun get(id: Long): Bike? = bikes.value.firstOrNull { it.id == id }

    override suspend fun save(bike: Bike): Long {
        val id = if (bike.id == 0L) (bikes.value.maxOfOrNull { it.id } ?: 0L) + 1 else bike.id
        bikes.value = bikes.value.filterNot { it.id == id } + bike.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        bikes.value = bikes.value.filterNot { it.id == id }
    }

    override fun observeLastUsedId(): Flow<Long?> = lastUsed

    override suspend fun setLastUsedId(id: Long) {
        lastUsed.value = id
    }
}

/** A finished ride as the component wear sees it. */
public data class FakeBikeRide(val bikeId: Long, val startedAtMs: Long, val distanceM: Double)

/** In-memory parts; wear is summed from [rides] like the SQL query (finished rides since fitted). */
public class FakeBikeComponentRepository : BikeComponentRepository {
    private val components = MutableStateFlow<List<BikeComponent>>(emptyList())
    public val rides: MutableStateFlow<List<FakeBikeRide>> = MutableStateFlow(emptyList())

    override fun observeWear(): Flow<List<ComponentWear>> = combine(components, rides) { parts, done ->
        parts.sortedBy { it.id }.map { part ->
            val ridden = done.filter { it.bikeId == part.bikeId && it.startedAtMs >= part.installedAtMs }
                .sumOf { it.distanceM }
            ComponentWear(part, part.initialDistanceM + ridden)
        }
    }

    override suspend fun get(id: Long): BikeComponent? = components.value.firstOrNull { it.id == id }

    override suspend fun save(component: BikeComponent): Long {
        val id = if (component.id == 0L) (components.value.maxOfOrNull { it.id } ?: 0L) + 1 else component.id
        components.value = components.value.filterNot { it.id == id } + component.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        components.value = components.value.filterNot { it.id == id }
    }

    override fun observeBikeDistanceM(bikeId: Long): Flow<Double> =
        rides.map { done -> done.filter { it.bikeId == bikeId }.sumOf { it.distanceM } }
}

/** In-memory plans and the plan being followed. */
public class FakeTrainingPlanRepository : TrainingPlanRepository {
    public val custom: MutableStateFlow<List<CustomPlan>> = MutableStateFlow(emptyList())
    public val active: MutableStateFlow<ActivePlanState?> = MutableStateFlow(null)

    override fun observeCustomPlans(): Flow<List<CustomPlan>> = custom

    override suspend fun saveCustomPlan(plan: CustomPlan): Long {
        val id = if (plan.id == 0L) (custom.value.maxOfOrNull { it.id } ?: 0L) + 1 else plan.id
        custom.value = custom.value.filterNot { it.id == id } + plan.copy(id = id)
        return id
    }

    override suspend fun deleteCustomPlan(id: Long) {
        custom.value = custom.value.filterNot { it.id == id }
    }

    override fun observeActivePlan(): Flow<ActivePlanState?> = active

    override suspend fun setActivePlan(state: ActivePlanState?) {
        active.value = state
    }

    public var rideTarget: PlanSession? = null

    override suspend fun getRideTarget(): PlanSession? = rideTarget

    override suspend fun setRideTarget(session: PlanSession?) {
        rideTarget = session
    }
}
