package com.ridecomp.core.usecase

import com.ridecomp.core.engine.efforts.BestEffortCalculator
import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.repository.ActivityProfileRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.PersonalRecord
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.source.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The profile kind of a ride: records are kept per kind (12-FEATURE-SPECS §8). */
public class GetActivityKindUseCase(private val profiles: ActivityProfileRepository) {
    public suspend operator fun invoke(activity: Activity): ActivityKind {
        val profileId = activity.profileId ?: return ActivityKind.ROAD
        // A deleted profile can only have been a rider's copy.
        return profiles.get(profileId)?.kind ?: ActivityKind.CUSTOM
    }
}

/** Best efforts of one ride compared with the records set before it; nothing is stored. */
public class EvaluateBestEffortsUseCase(
    private val efforts: BestEffortRepository,
    private val kindOf: GetActivityKindUseCase,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(activity: Activity, track: RideTrack): List<RideEffort> {
        val calculated = withContext(dispatchers.default) { BestEffortCalculator.calculate(track, activity.totals) }
        val bests = efforts.bestsBefore(kindOf(activity), activity.startedAtMs)
        return calculated.map { RideEffort(it, isPr = it.key.isBetter(it.value, bests[it.key])) }
    }
}

/** Analysis step (T4.1, last since T11.8): stores the ride's best efforts with their record flags. */
public class BestEffortsStep(
    private val efforts: BestEffortRepository,
    private val evaluate: EvaluateBestEffortsUseCase,
    private val kindOf: GetActivityKindUseCase,
) : AnalysisStep {
    override val version: Int = AnalysisConfig.BEST_EFFORTS_VERSION

    override suspend fun analyze(activity: Activity, track: RideTrack) {
        efforts.replace(activity.id, kindOf(activity), evaluate(activity, track))
    }
}

/** New records a ride would set, for the summary card before saving (13-UI-SCREENS #10). */
public class PreviewNewRecordsUseCase(
    private val activities: ActivityRepository,
    private val readTrack: ReadRideTrackUseCase,
    private val evaluate: EvaluateBestEffortsUseCase,
) {
    public suspend operator fun invoke(activityId: Long): List<RideEffort> {
        val activity = activities.get(activityId) ?: return emptyList()
        return evaluate(activity, readTrack(activityId)).filter { it.isPr }
    }
}

/** Personal records of one profile kind (Records tab, 12-FEATURE-SPECS §8). */
public class ObservePersonalRecordsUseCase(private val efforts: BestEffortRepository) {
    public operator fun invoke(kind: ActivityKind): Flow<List<PersonalRecord>> = efforts.observeRecords(kind)
}

/** Profile kinds that have records (the kind chips of the Records tab). */
public class ObserveRecordKindsUseCase(private val efforts: BestEffortRepository) {
    public operator fun invoke(): Flow<List<ActivityKind>> = efforts.observeKinds()
}
