package com.ridecomp.core.usecase

import com.ridecomp.core.model.PeriodTotals
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.repository.ActivityRepository
import kotlinx.coroutines.flow.first

/** What the home screen widget shows when no ride is running (T9.2). */
public data class WidgetSummary(val plan: ActivePlan?, val week: PeriodTotals, val unitSystem: UnitSystem)

/** One read for a widget update: the followed plan's today, this week's rides (from Monday), units. */
public class LoadWidgetSummaryUseCase(
    private val observeActivePlan: ObserveActivePlanUseCase,
    private val activities: ActivityRepository,
    private val observeSettings: ObserveRideSettingsUseCase,
    private val calendar: TrainingCalendar,
) {
    public suspend operator fun invoke(): WidgetSummary = WidgetSummary(
        plan = observeActivePlan().first(),
        week = activities.observeTotalsSince(calendar.startOfDayMs(calendar.weekStart())).first(),
        unitSystem = observeSettings().first().unitSystem,
    )
}
