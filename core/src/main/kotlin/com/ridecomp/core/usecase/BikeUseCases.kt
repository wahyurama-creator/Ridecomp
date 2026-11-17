package com.ridecomp.core.usecase

import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.model.Bike
import com.ridecomp.core.repository.BikeRepository
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Saved bikes with the one to preselect: the last used, else the first (D-89). */
public data class BikeChoice(val bikes: List<Bike>, val selected: Bike?)

public class ObserveBikeChoiceUseCase(private val repository: BikeRepository) {
    public operator fun invoke(): Flow<BikeChoice> =
        combine(repository.observeAll(), repository.observeLastUsedId()) { bikes, lastId ->
            BikeChoice(bikes, bikes.firstOrNull { it.id == lastId } ?: bikes.firstOrNull())
        }
}

/** Remembers the bike picked before a ride, so it is preselected next time. */
public class SelectBikeUseCase(private val repository: BikeRepository) {
    public suspend operator fun invoke(id: Long) {
        repository.setLastUsedId(id)
    }
}

/** The bike of the ride being started or recovered: the one picked last. */
public class GetSelectedBikeUseCase(private val repository: BikeRepository) {
    public suspend operator fun invoke(): Bike? = ObserveBikeChoiceUseCase(repository)().first().selected
}

public class GetBikeUseCase(private val repository: BikeRepository) {
    public suspend operator fun invoke(id: Long): Bike? = repository.get(id)
}

/** Bike fields that can be invalid. */
public enum class BikeField { WEIGHT, WHEEL_CIRCUMFERENCE }

public data class InvalidBike(val fields: Set<BikeField>)

/**
 * Validates against the ranges in [ProfileDefaults] (SEC-IN-02) and saves; names are trimmed and limited.
 * A new bike becomes the selected one: the rider just added it to ride it. Returns the id.
 */
public class SaveBikeUseCase(private val repository: BikeRepository) {
    public suspend operator fun invoke(bike: Bike): Outcome<Long, InvalidBike> {
        val invalid = buildSet {
            val weightRange = ProfileDefaults.MIN_BIKE_WEIGHT_KG..ProfileDefaults.MAX_BIKE_WEIGHT_KG
            if (!bike.weightKg.isFinite() || bike.weightKg !in weightRange) add(BikeField.WEIGHT)
            if (bike.wheelCircumferenceMm !in ProfileDefaults.MIN_WHEEL_MM..ProfileDefaults.MAX_WHEEL_MM) {
                add(BikeField.WHEEL_CIRCUMFERENCE)
            }
        }
        if (invalid.isNotEmpty()) return InvalidBike(invalid).asFailure()
        val id = repository.save(bike.copy(name = bike.name.trim().take(BikeLimits.NAME_MAX_LENGTH)))
        if (bike.id == 0L) repository.setLastUsedId(id)
        return id.asSuccess()
    }
}

/** Deletes a bike; the last one stays so a ride always has a bike. */
public class DeleteBikeUseCase(private val repository: BikeRepository) {
    public suspend operator fun invoke(id: Long): Boolean {
        val bikes = repository.observeAll().first()
        if (bikes.size <= 1 || bikes.none { it.id == id }) return false
        repository.delete(id)
        return true
    }
}

public object BikeLimits {
    public const val NAME_MAX_LENGTH: Int = 30
}
