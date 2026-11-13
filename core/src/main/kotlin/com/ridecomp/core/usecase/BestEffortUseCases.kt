package com.ridecomp.core.usecase

import com.ridecomp.core.engine.efforts.BestEffortCalculator
import com.ridecomp.core.engine.efforts.BestEffortConfig
import com.ridecomp.core.engine.efforts.EffortTrack
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.repository.ActivityAnalysisRepository
import com.ridecomp.core.repository.ActivityProfileRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.PersonalRecord
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.util.runCatchingCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Post-ride analysis (Phase 4, D-67). Bump [VERSION] when a later task adds a step, to re-run old rides. */
public object AnalysisConfig {
    /** 1 = best efforts (T4.1). */
    public const val VERSION: Int = 1

    /** Rides analyzed per database query. */
    public const val BATCH_SIZE: Int = 20
}

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
    private val activities: ActivityRepository,
    private val efforts: BestEffortRepository,
    private val kindOf: GetActivityKindUseCase,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(activity: Activity): List<RideEffort> {
        val track = withContext(dispatchers.io) { readTrack(activity.id) }
        val calculated = withContext(dispatchers.default) { BestEffortCalculator.calculate(track, activity.totals) }
        val bests = efforts.bestsBefore(kindOf(activity), activity.startedAtMs)
        return calculated.map { RideEffort(it, isPr = it.key.isBetter(it.value, bests[it.key])) }
    }

    /** Pages through the samples into primitive arrays (bounded, MEM-01). */
    private suspend fun readTrack(activityId: Long): EffortTrack {
        val track = EffortTrack()
        var afterMs = Long.MIN_VALUE
        var hasMore = true
        while (hasMore && !track.isFull) {
            val page = activities.getSamples(activityId, afterMs, BestEffortConfig.PAGE_SIZE)
            page.forEach(track::add)
            afterMs = page.lastOrNull()?.timeMs ?: afterMs
            hasMore = page.size == BestEffortConfig.PAGE_SIZE
        }
        return track
    }
}

/**
 * Analyzes every saved ride not yet at [AnalysisConfig.VERSION], oldest first: after a ride is saved and once
 * for the rides saved before Phase 4 (D-67). Runs only while the app is open (BAT-05) and resumes where it
 * stopped. One failing ride is logged and skipped so it cannot block the others.
 */
public class AnalyzeActivitiesUseCase(
    private val activities: ActivityAnalysisRepository,
    private val efforts: BestEffortRepository,
    private val evaluate: EvaluateBestEffortsUseCase,
    private val kindOf: GetActivityKindUseCase,
    private val logger: Logger,
) {
    private val mutex = Mutex()

    public suspend operator fun invoke() {
        mutex.withLock {
            while (true) {
                val pending = activities.getPendingAnalysis(AnalysisConfig.VERSION, AnalysisConfig.BATCH_SIZE)
                if (pending.isEmpty()) break
                pending.forEach { activity -> analyze(activity) }
            }
        }
    }

    private suspend fun analyze(activity: Activity) {
        runCatchingCancellable {
            efforts.replace(activity.id, kindOf(activity), evaluate(activity))
        }.onFailure { error -> logger.error(TAG, error) { "Analysis failed for ride ${activity.id}; skipped" } }
        activities.markAnalyzed(activity.id, AnalysisConfig.VERSION)
    }

    private companion object {
        const val TAG = "Analysis"
    }
}

/** New records a ride would set, for the summary card before saving (13-UI-SCREENS #10). */
public class PreviewNewRecordsUseCase(
    private val activities: ActivityRepository,
    private val evaluate: EvaluateBestEffortsUseCase,
) {
    public suspend operator fun invoke(activityId: Long): List<RideEffort> {
        val activity = activities.get(activityId) ?: return emptyList()
        return evaluate(activity).filter { it.isPr }
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
