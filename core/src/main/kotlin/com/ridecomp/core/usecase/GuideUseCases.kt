package com.ridecomp.core.usecase

import com.ridecomp.core.model.GuidePreferences
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.core.repository.GuideRepository
import kotlinx.coroutines.flow.Flow

public class ObserveGuideUseCase(private val guide: GuideRepository) {
    public operator fun invoke(): Flow<GuidePreferences> = guide.observe()
}

public class SetRiderLevelUseCase(private val guide: GuideRepository) {
    public suspend operator fun invoke(level: RiderLevel) {
        guide.setLevel(level)
    }
}

public class DismissTipUseCase(private val guide: GuideRepository) {
    public suspend operator fun invoke(tip: GuideTip) {
        guide.dismiss(tip)
    }
}

/** "Tampilkan lagi semua tips" in Help. */
public class ResetTipsUseCase(private val guide: GuideRepository) {
    public suspend operator fun invoke() {
        guide.resetTips()
    }
}
