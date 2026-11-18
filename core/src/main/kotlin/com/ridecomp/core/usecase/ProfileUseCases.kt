package com.ridecomp.core.usecase

import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.Clock
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow

/** Profile fields that can be invalid. */
public enum class ProfileField { WEIGHT, BIKE_WEIGHT, WHEEL_CIRCUMFERENCE, HR_MAX, HR_REST, FTP, BIRTH_YEAR }

/** Validation result: every invalid field, so the form can mark all of them at once. */
public data class InvalidProfile(val fields: Set<ProfileField>)

public class ObserveProfileUseCase(private val repository: ProfileRepository) {
    public operator fun invoke(): Flow<RiderProfile> = repository.observe()
}

/** True when the rider already saved a profile (first launch → onboarding). */
public class HasProfileUseCase(private val repository: ProfileRepository) {
    public suspend operator fun invoke(): Boolean = repository.hasProfile()
}

/** Live "the rider saved a profile": the pre-ride check offers to fill one in when not (T10.12). */
public class ObserveHasProfileUseCase(private val repository: ProfileRepository) {
    public operator fun invoke(): Flow<Boolean> = repository.observeHasProfile()
}

/** Validates against the ranges in [ProfileDefaults] (SEC-IN-02) and saves. */
public class SaveProfileUseCase(private val repository: ProfileRepository, private val clock: Clock) {
    public suspend operator fun invoke(profile: RiderProfile): Outcome<Unit, InvalidProfile> {
        val invalid = validate(profile)
        if (invalid.isNotEmpty()) return InvalidProfile(invalid).asFailure()
        repository.save(profile)
        return Unit.asSuccess()
    }

    private fun validate(profile: RiderProfile): Set<ProfileField> = buildSet {
        val currentYear = Instant.ofEpochMilli(clock.nowMs()).atZone(ZoneOffset.UTC).year
        if (!profile.weightKg.isWithin(ProfileDefaults.MIN_WEIGHT_KG, ProfileDefaults.MAX_WEIGHT_KG)) {
            add(ProfileField.WEIGHT)
        }
        if (!profile.bikeWeightKg.isWithin(ProfileDefaults.MIN_BIKE_WEIGHT_KG, ProfileDefaults.MAX_BIKE_WEIGHT_KG)) {
            add(ProfileField.BIKE_WEIGHT)
        }
        if (profile.wheelCircumferenceMm !in ProfileDefaults.MIN_WHEEL_MM..ProfileDefaults.MAX_WHEEL_MM) {
            add(ProfileField.WHEEL_CIRCUMFERENCE)
        }
        if (!profile.hrMaxBpm.isNullOrIn(
                ProfileDefaults.MIN_HR_MAX..ProfileDefaults.MAX_HR_MAX,
            )
        ) {
            add(ProfileField.HR_MAX)
        }
        val hrRest = profile.hrRestBpm
        val isRestOutOfRange = hrRest != null && hrRest !in ProfileDefaults.MIN_HR_REST..ProfileDefaults.MAX_HR_REST
        val isRestAboveMax = hrRest != null && hrRest >= (profile.hrMaxBpm ?: ProfileDefaults.MAX_HR_MAX)
        if (isRestOutOfRange || isRestAboveMax) {
            add(ProfileField.HR_REST)
        }
        if (!profile.ftpW.isNullOrIn(ProfileDefaults.MIN_FTP_W..ProfileDefaults.MAX_FTP_W)) add(ProfileField.FTP)
        if (!profile.birthYear.isNullOrIn(ProfileDefaults.MIN_BIRTH_YEAR..currentYear)) add(ProfileField.BIRTH_YEAR)
    }

    private fun Double.isWithin(min: Double, max: Double): Boolean = isFinite() && this in min..max

    private fun Int?.isNullOrIn(range: IntRange): Boolean = this == null || this in range
}

/** Age-predicted HR max (220 − age), offered as an estimate when HR max is empty (05-DATA-MODEL §3.8). */
public class EstimateHrMaxUseCase(private val clock: Clock) {
    public operator fun invoke(birthYear: Int): Int {
        val currentYear = Instant.ofEpochMilli(clock.nowMs()).atZone(ZoneOffset.UTC).year
        return ProfileDefaults.HR_MAX_AGE_BASE - (currentYear - birthYear)
    }
}

public class ObserveRideSettingsUseCase(private val repository: SettingsRepository) {
    public operator fun invoke(): Flow<RideSettings> = repository.observeRideSettings()
}

public class SaveRideSettingsUseCase(private val repository: SettingsRepository) {
    public suspend operator fun invoke(settings: RideSettings) {
        repository.saveRideSettings(settings)
    }
}

public class ObserveOnboardingDoneUseCase(private val repository: SettingsRepository) {
    public operator fun invoke(): Flow<Boolean> = repository.observeOnboardingDone()
}

public class CompleteOnboardingUseCase(private val repository: SettingsRepository) {
    public suspend operator fun invoke() {
        repository.setOnboardingDone()
    }
}
