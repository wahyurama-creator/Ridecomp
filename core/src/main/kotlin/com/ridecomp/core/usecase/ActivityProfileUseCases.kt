package com.ridecomp.core.usecase

import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.repository.ActivityProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Profiles for the pre-ride picker and the settings list, with the one to preselect. */
public data class ProfileChoice(val profiles: List<ActivityProfile>, val selected: ActivityProfile?)

public class ObserveActivityProfilesUseCase(private val repository: ActivityProfileRepository) {
    public operator fun invoke(): Flow<List<ActivityProfile>> = repository.observeAll()
}

/** The last used profile is the default; if it was deleted, the first profile (Road) (12-FEATURE-SPECS §4). */
public class ObserveProfileChoiceUseCase(private val repository: ActivityProfileRepository) {
    public operator fun invoke(): Flow<ProfileChoice> =
        combine(repository.observeAll(), repository.observeLastUsedId()) { profiles, lastId ->
            ProfileChoice(profiles, profiles.firstOrNull { it.id == lastId } ?: profiles.firstOrNull())
        }
}

public class SelectActivityProfileUseCase(private val repository: ActivityProfileRepository) {
    public suspend operator fun invoke(id: Long) {
        repository.setLastUsedId(id)
    }
}

/** Profile for a ride: [id] when it still exists, else the default choice (recovery of an old ride). */
public class GetRideProfileUseCase(private val repository: ActivityProfileRepository) {
    public suspend operator fun invoke(id: Long?): ActivityProfile? =
        id?.let { repository.get(it) } ?: ObserveProfileChoiceUseCase(repository)().first().selected
}

public class ObserveActivityProfileUseCase(private val repository: ActivityProfileRepository) {
    public suspend operator fun invoke(id: Long): ActivityProfile? = repository.get(id)
}

/** Saves an edited profile; names are trimmed and limited ([ProfileLimits]). */
public class SaveActivityProfileUseCase(private val repository: ActivityProfileRepository) {
    public suspend operator fun invoke(profile: ActivityProfile): Long =
        repository.save(profile.copy(name = profile.name.trim().take(ProfileLimits.NAME_MAX_LENGTH)))
}

/** Copies a profile as a custom one placed last (12-FEATURE-SPECS §4: "menduplikasi profil"). */
public class DuplicateActivityProfileUseCase(private val repository: ActivityProfileRepository) {
    public suspend operator fun invoke(id: Long, copyName: String): Long? {
        val source = repository.get(id) ?: return null
        val nextOrder = (repository.observeAll().first().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return repository.save(
            source.copy(
                id = 0,
                name = copyName.trim().take(ProfileLimits.NAME_MAX_LENGTH),
                kind = ActivityKind.CUSTOM,
                sortOrder = nextOrder,
            ),
        )
    }
}

/** Only custom profiles can be deleted; built-ins stay so the picker is never empty. */
public class DeleteActivityProfileUseCase(private val repository: ActivityProfileRepository) {
    public suspend operator fun invoke(id: Long): Boolean {
        val profile = repository.get(id)
        if (profile == null || profile.isBuiltIn) return false
        repository.delete(id)
        return true
    }
}

public object ProfileLimits {
    public const val NAME_MAX_LENGTH: Int = 30
}
