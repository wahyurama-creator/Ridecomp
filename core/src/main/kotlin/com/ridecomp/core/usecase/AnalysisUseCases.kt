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
 * has passed, so a new step (or a new segment, D-70) re-runs only what is missing on old rides. A step whose
 * results change gets the next free version: old rides then re-run that step once (D-125).
 */
public object AnalysisConfig {
    public const val SEGMENTS_VERSION: Int = 2
    public const val TRAINING_VERSION: Int = 3

    /** Was 1; 4 since the speed and extra power efforts (T11.8) so old rides gain them. */
    public const val BEST_EFFORTS_VERSION: Int = 4

    /** The version a fully analyzed ride has. */
    public const val VERSION: Int = BEST_EFFORTS_VERSION

    /** Rides analyzed per database query. */
    public const val BATCH_SIZE: Int = 20
}

/** One part of the post-ride analysis; [version] orders the steps. */
public interface AnalysisStep {
    public val version: Int

    public suspend fun analyze(activity: Activity, track: RideTrack)

    /** Called once after each batch of rides (work that should not repeat per ride). */
    public suspend fun afterBatch() {}
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

/**
 * Asks the running app to analyze again (e.g. after a segment was created) and remembers which steps were
 * asked for, so a ride being analyzed at that moment runs them again too. Single instance.
 */
public class AnalysisRequests {
    private val requests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val lock = Any()
    private var generation = 0L
    private val recent = ArrayDeque<Pair<Long, Int>>()

    public val flow: Flow<Unit> = requests.asSharedFlow()

    /** Steps from [fromVersion] on must run again on every ride. */
    public fun request(fromVersion: Int) {
        synchronized(lock) {
            generation++
            recent.addLast(generation to fromVersion)
            while (recent.size > MAX_REMEMBERED) recent.removeFirst()
        }
        requests.tryEmit(Unit)
    }

    public fun generation(): Long = synchronized(lock) { generation }

    /** The lowest step version asked for after [since], or null when nothing was asked. */
    public fun lowestRequestedSince(since: Long): Int? =
        synchronized(lock) { recent.filter { it.first > since }.minOfOrNull { it.second } }

    private companion object {
        const val MAX_REMEMBERED = 32
    }
}

/** Marks saved rides for re-running the steps from [fromVersion] on, then wakes the analysis. */
public class RequestReanalysisUseCase(
    private val analysis: ActivityAnalysisRepository,
    private val requests: AnalysisRequests,
) {
    public suspend operator fun invoke(fromVersion: Int) {
        analysis.resetAnalysis(toVersion = fromVersion - 1)
        requests.request(fromVersion)
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
    private val requests: AnalysisRequests,
    private val logger: Logger,
) {
    private val steps = steps.sortedBy { it.version }
    private val mutex = Mutex()

    public suspend operator fun invoke() {
        mutex.withLock {
            // A database failure here must not crash the app: the next start retries (D-67).
            runCatchingCancellable { analyzePending() }
                .onFailure { error -> logger.error(TAG, error) { "Analysis stopped; retried at the next start" } }
        }
    }

    private suspend fun analyzePending() {
        while (true) {
            val pending = analysis.getPendingAnalysis(AnalysisConfig.VERSION, AnalysisConfig.BATCH_SIZE)
            if (pending.isEmpty()) break
            pending.forEach { (activity, doneVersion) -> analyze(activity, doneVersion) }
            steps.forEach { step ->
                runCatchingCancellable { step.afterBatch() }
                    .onFailure { error -> logger.error(TAG, error) { "Analysis batch step failed" } }
            }
        }
    }

    private suspend fun analyze(activity: Activity, doneVersion: Int) {
        val generation = requests.generation()
        runCatchingCancellable {
            val track = readTrack(activity.id)
            steps.filter { it.version > doneVersion }.forEach { it.analyze(activity, track) }
        }.onFailure { error -> logger.error(TAG, error) { "Analysis failed for ride ${activity.id}; skipped" } }
        // A segment created meanwhile: stop before the asked step so the ride is picked up again. The
        // expected version guards against a reset of rides already above it.
        val reached = requests.lowestRequestedSince(generation)?.let { minOf(AnalysisConfig.VERSION, it - 1) }
            ?: AnalysisConfig.VERSION
        analysis.markAnalyzed(activity.id, reached, expectedVersion = doneVersion)
    }

    private companion object {
        const val TAG = "Analysis"
    }
}
