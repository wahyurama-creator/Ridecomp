package com.ridecomp.core.usecase

import com.ridecomp.core.config.ActivityConfig
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.Lap
import com.ridecomp.core.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow

/** Finds a ride left unfinished by a crash/kill, to offer resume/save/discard (03-ARCHITECTURE §4.5). */
public class GetRecoverableRideUseCase(private val repository: ActivityRepository) {
    public suspend operator fun invoke(): Activity? = repository.findRecoverable()
}

/** One page of saved rides, newest first (02-DESIGN §3.7). */
public class ObserveHistoryPageUseCase(private val repository: ActivityRepository) {
    public operator fun invoke(page: Int, pageSize: Int = ActivityConfig.HISTORY_PAGE_SIZE): Flow<List<Activity>> =
        repository.observeFinished(limit = pageSize, offset = page * pageSize)
}

/** Number of saved rides. */
public class ObserveHistoryCountUseCase(private val repository: ActivityRepository) {
    public operator fun invoke(): Flow<Int> = repository.observeFinishedCount()
}

/** A single ride, updated live. */
public class ObserveActivityUseCase(private val repository: ActivityRepository) {
    public operator fun invoke(activityId: Long): Flow<Activity?> = repository.observe(activityId)
}

/** Laps of a ride. */
public class GetLapsUseCase(private val repository: ActivityRepository) {
    public suspend operator fun invoke(activityId: Long): List<Lap> = repository.getLaps(activityId)
}

/** Renames a ride; blank names are ignored. */
public class RenameActivityUseCase(private val repository: ActivityRepository) {
    public suspend operator fun invoke(activityId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) repository.rename(activityId, trimmed.take(ActivityConfig.MAX_NAME_LENGTH))
    }
}

/** Deletes a ride and its files (SEC-STO-06). */
public class DeleteActivityUseCase(private val repository: ActivityRepository) {
    public suspend operator fun invoke(activityId: Long) {
        repository.delete(activityId)
    }
}
