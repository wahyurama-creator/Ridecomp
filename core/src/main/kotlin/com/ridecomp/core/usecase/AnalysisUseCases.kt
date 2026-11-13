package com.ridecomp.core.usecase

import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.engine.track.RideTrackConfig
import com.ridecomp.core.model.Activity
import com.ridecomp.core.repository.ActivityAnalysisRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.util.runCatchingCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Post-ride analysis versions (Phase 4, D-67). Each step has its own version; a ride stores the highest it
 * has passed, so a new step (or a new segment, D-70) re-runs only what is missing on old rides.
 */
public object AnalysisConfig {
    public const val BEST_EFFORTS_VERSION: Int = 1
    public const val SEGMENTS_VERSION: Int = 2
    public const val TRAINING_VERSION: Int = 3

    /** The version a fully analyzed ride has. */
    public const val VERSION: Int = TRAINING_VERSION

    /** Rides analyzed per database query. */
    public const val BATCH_SIZE: Int = 20
}

/** One part of the post-ride analysis; [version] orders the steps. */
public interface AnalysisStep {
    public val version: Int

    public suspend fun analyze(activity: Activity, track: RideTrack)
}

/** Reads a ride's whole track into a [RideTrack], page by page on the IO dispatcher (bounded, MEM-01). */
public class ReadRideTrackUseCase(
    private val activities: ActivityRepository,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(activityId: Long): RideTrack = withContext(dispatchers.io) {
        val track = RideTrack()
        var afterMs = Long.MIN_VALUE
        var hasMore = true
        while (hasMore && !track.isFull) {
            val page = activities.getSamples(activityId, afterMs, RideTrackConfig.PAGE_SIZE)
            page.forEach(track::add)
            afterMs = page.lastOrNull()?.timeMs ?: afterMs
            hasMore = page.size == RideTrackConfig.PAGE_SIZE
        }
        track
    }
}

/** Asks the running app to analyze again (e.g. after a segment was created). Single instance. */
public class AnalysisRequests {
    private val requests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    public val flow: Flow<Unit> = requests.asSharedFlow()

    public fun request() {
        requests.tryEmit(Unit)
    }
}

/** Marks saved rides for re-running the steps from [fromVersion] on, then wakes the analysis. */
public class RequestReanalysisUseCase(
    private val analysis: ActivityAnalysisRepository,
    private val requests: AnalysisRequests,
) {
    public suspend operator fun invoke(fromVersion: Int) {
        analysis.resetAnalysis(toVersion = fromVersion - 1)
        requests.request()
    }
}

/**
 * Analyzes every saved ride not yet at [AnalysisConfig.VERSION], oldest first so "new record" flags follow
 * the order the rides happened: after a ride is saved and once for rides saved before Phase 4 (D-67). Runs
 * only while the app is open (BAT-05) and resumes where it stopped. The track is read once per ride; a
 * failing ride is logged and skipped so it cannot block the others. Single instance (one mutex).
 */
public class AnalyzeActivitiesUseCase(
    private val analysis: ActivityAnalysisRepository,
    private val readTrack: ReadRideTrackUseCase,
    steps: List<AnalysisStep>,
    private val logger: Logger,
) {
    private val steps = steps.sortedBy { it.version }
    private val mutex = Mutex()

    public suspend operator fun invoke() {
        mutex.withLock {
            while (true) {
                val pending = analysis.getPendingAnalysis(AnalysisConfig.VERSION, AnalysisConfig.BATCH_SIZE)
                if (pending.isEmpty()) break
                pending.forEach { (activity, doneVersion) -> analyze(activity, doneVersion) }
            }
        }
    }

    private suspend fun analyze(activity: Activity, doneVersion: Int) {
        runCatchingCancellable {
            val track = readTrack(activity.id)
            steps.filter { it.version > doneVersion }.forEach { it.analyze(activity, track) }
        }.onFailure { error -> logger.error(TAG, error) { "Analysis failed for ride ${activity.id}; skipped" } }
        analysis.markAnalyzed(activity.id, AnalysisConfig.VERSION)
    }

    private companion object {
        const val TAG = "Analysis"
    }
}
