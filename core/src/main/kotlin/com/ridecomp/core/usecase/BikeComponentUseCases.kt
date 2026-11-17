package com.ridecomp.core.usecase

import com.ridecomp.core.config.Units
import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.ComponentWear
import com.ridecomp.core.repository.BikeComponentRepository
import com.ridecomp.core.source.Clock
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Parts of one bike with their distance (T7.3). */
public class ObserveComponentWearUseCase(private val repository: BikeComponentRepository) {
    public operator fun invoke(bikeId: Long): Flow<List<ComponentWear>> =
        repository.observeWear().map { all -> all.filter { it.component.bikeId == bikeId } }

    /** Every bike's parts that reached their limit, for the bike list and the pre-ride check. */
    public fun due(): Flow<List<ComponentWear>> = repository.observeWear().map { all -> all.filter { it.isDue } }
}

public class ObserveBikeDistanceUseCase(private val repository: BikeComponentRepository) {
    public operator fun invoke(bikeId: Long): Flow<Double> = repository.observeBikeDistanceM(bikeId)
}

public class GetComponentUseCase(private val repository: BikeComponentRepository) {
    public suspend operator fun invoke(id: Long): BikeComponent? = repository.get(id)
}

/** Component fields that can be invalid. */
public enum class ComponentField { LIMIT, INITIAL_DISTANCE }

public data class InvalidComponent(val fields: Set<ComponentField>)

/** Validates the limit and the distance already done (0..[ComponentLimits.MAX_KM]) and saves. */
public class SaveComponentUseCase(private val repository: BikeComponentRepository) {
    public suspend operator fun invoke(component: BikeComponent): Outcome<Long, InvalidComponent> {
        val maxM = ComponentLimits.MAX_KM * Units.METERS_PER_KM
        val invalid = buildSet {
            // Comparisons are false for NaN, so NaN is invalid too.
            val limit = component.limitM
            if (limit != null && !(limit > 0.0 && limit <= maxM)) add(ComponentField.LIMIT)
            val initial = component.initialDistanceM
            if (!(initial >= 0.0 && initial <= maxM)) add(ComponentField.INITIAL_DISTANCE)
        }
        if (invalid.isNotEmpty()) return InvalidComponent(invalid).asFailure()
        val name = component.name.trim().take(ComponentLimits.NAME_MAX_LENGTH)
        return repository.save(component.copy(name = name)).asSuccess()
    }
}

/** "Just replaced": counting starts again from now at 0 km. */
public class ReplaceComponentUseCase(private val repository: BikeComponentRepository, private val clock: Clock) {
    public suspend operator fun invoke(id: Long) {
        val component = repository.get(id) ?: return
        repository.save(component.copy(installedAtMs = clock.nowMs(), initialDistanceM = 0.0))
    }
}

public class DeleteComponentUseCase(private val repository: BikeComponentRepository) {
    public suspend operator fun invoke(id: Long) {
        repository.delete(id)
    }
}

public object ComponentLimits {
    public const val NAME_MAX_LENGTH: Int = 30

    /** Upper bound of a limit or a used part's distance. */
    public const val MAX_KM: Int = 100_000
}
