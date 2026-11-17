package com.ridecomp.feature.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.BuiltInPlans
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.PlanWeek
import com.ridecomp.core.model.TrainingPlan
import com.ridecomp.core.usecase.ActivePlan
import com.ridecomp.core.usecase.PlanField
import com.ridecomp.feature.training.R
import com.ridecomp.feature.training.model.descriptionRes
import com.ridecomp.feature.training.model.displayName
import com.ridecomp.feature.training.model.howToRes
import com.ridecomp.feature.training.model.summary
import com.ridecomp.feature.training.model.titleRes
import com.ridecomp.feature.training.viewmodel.PlanBuilderEffect
import com.ridecomp.feature.training.viewmodel.PlanBuilderUiEvent
import com.ridecomp.feature.training.viewmodel.PlanBuilderUiState
import com.ridecomp.feature.training.viewmodel.PlanBuilderViewModel
import com.ridecomp.feature.training.viewmodel.PlanDetailEffect
import com.ridecomp.feature.training.viewmodel.PlanDetailUiEvent
import com.ridecomp.feature.training.viewmodel.PlanDetailUiState
import com.ridecomp.feature.training.viewmodel.PlanDetailViewModel
import com.ridecomp.feature.training.viewmodel.PlansUiState
import com.ridecomp.feature.training.viewmodel.PlansViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.Choice
import com.ridecomp.ui.component.ChoiceChips
import com.ridecomp.ui.component.ConfirmDialog
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.RideCompTextField
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The plan being followed and today's ride in it, at the top of the Training tab (T7.4). */
@Composable
fun PlanCard(active: ActivePlan?, onOpenPlans: () -> Unit, onOpenPlan: (PlanRef) -> Unit) {
    val colors = RideCompTheme.colors
    if (active == null) {
        RideCompCard(onClick = onOpenPlans) {
            Text(
                text = stringResource(R.string.plan_card_title),
                style = RideCompTheme.typography.cardTitle,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.plan_card_none),
                style = RideCompTheme.typography.body,
                color = colors.textSecondary,
            )
        }
        return
    }
    val today = active.today
    RideCompCard(onClick = { onOpenPlan(active.plan.ref) }, tone = CardTone.OK) {
        Text(
            text = if (today != null) {
                stringResource(
                    R.string.plan_card_week,
                    active.plan.displayName(),
                    today.weekNumber,
                    active.plan.weeks.size,
                ).uppercase()
            } else {
                active.plan.displayName().uppercase()
            },
            style = RideCompTheme.typography.dataLabel,
            color = colors.ok,
        )
        Text(
            text = today?.let { stringResource(R.string.plan_card_today, it.session.summary()) }
                ?: stringResource(R.string.plan_card_finished),
            style = RideCompTheme.typography.cardTitle,
            color = colors.textPrimary,
        )
        today?.session?.let { session ->
            Text(
                text = stringResource(session.intensity.howToRes()),
                style = RideCompTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
fun PlansRoute(
    onOpenPlan: (PlanRef) -> Unit,
    onBuild: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlansViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlansScreen(state, onOpenPlan, onBuild, onBack, modifier)
}

/** Ready-made plans with what they are for, the rider's own, and "build my own" (T7.4). */
@Composable
fun PlansScreen(
    state: PlansUiState,
    onOpenPlan: (PlanRef) -> Unit,
    onBuild: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.plans_title),
        onBack = onBack,
        backLabel = stringResource(R.string.plans_back),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            Text(
                text = stringResource(R.string.plans_intro),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textSecondary,
            )
            state.plans.forEach { plan ->
                PlanRow(plan, isFollowed = state.active?.plan?.ref == plan.ref, onClick = { onOpenPlan(plan.ref) })
            }
            if (state.isLoaded) {
                BigButton(
                    text = stringResource(R.string.plans_build),
                    onClick = onBuild,
                    style = BigButtonStyle.SECONDARY,
                    size = BigButtonSize.SECONDARY,
                )
            }
        }
    }
}

@Composable
private fun PlanRow(plan: TrainingPlan, isFollowed: Boolean, onClick: () -> Unit) {
    val colors = RideCompTheme.colors
    RideCompCard(onClick = onClick, tone = if (isFollowed) CardTone.OK else CardTone.NORMAL) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = plan.displayName(),
                style = RideCompTheme.typography.cardTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (isFollowed) {
                Text(
                    text = stringResource(R.string.plans_followed),
                    style = RideCompTheme.typography.caption,
                    color = colors.ok,
                )
            }
        }
        val ref = plan.ref
        if (ref is PlanRef.BuiltIn) {
            Text(
                text = stringResource(ref.plan.descriptionRes()),
                style = RideCompTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
        Text(
            text = pluralStringResource(
                R.plurals.plans_size,
                plan.sessionCount,
                pluralStringResource(R.plurals.plans_weeks, plan.weeks.size, plan.weeks.size),
                plan.sessionCount,
            ),
            style = RideCompTheme.typography.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
fun PlanDetailRoute(
    ref: PlanRef,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanDetailViewModel = koinViewModel(key = ref.toString()) { parametersOf(ref) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            PlanDetailEffect.Deleted -> onBack()
        }
    }
    PlanDetailScreen(state, viewModel::onEvent, onEdit, onBack, modifier)
}

/** A plan week by week, today marked when it is followed; start/stop; edit or delete one's own. */
@Composable
fun PlanDetailScreen(
    state: PlanDetailUiState,
    onEvent: (PlanDetailUiEvent) -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    ScreenScaffold(
        modifier = modifier,
        title = plan?.displayName() ?: stringResource(R.string.plans_title),
        onBack = onBack,
        backLabel = stringResource(R.string.plans_back),
    ) {
        if (plan == null) return@ScreenScaffold
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            val ref = plan.ref
            if (ref is PlanRef.BuiltIn) {
                Text(
                    text = stringResource(ref.plan.descriptionRes()),
                    style = RideCompTheme.typography.body,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
            PlanActions(state, onEvent, onEdit)
            val today = state.active?.today.takeIf { state.isFollowed }
            plan.weeks.forEachIndexed { index, week ->
                WeekCard(index, week, todayIndex = today?.dayIndex?.takeIf { today.weekIndex == index })
            }
            IntensityGuide()
        }
    }
    if (state.isDeleteAsked) {
        ConfirmDialog(
            title = stringResource(R.string.plan_delete_title),
            message = stringResource(R.string.plan_delete_message),
            confirmLabel = stringResource(R.string.plan_delete),
            dismissLabel = stringResource(R.string.plan_cancel),
            onConfirm = { onEvent(PlanDetailUiEvent.ConfirmDelete) },
            onDismiss = { onEvent(PlanDetailUiEvent.DismissDelete) },
        )
    }
}

@Composable
private fun PlanActions(state: PlanDetailUiState, onEvent: (PlanDetailUiEvent) -> Unit, onEdit: (Long) -> Unit) {
    if (state.isFollowed) {
        Text(
            text = stringResource(R.string.plan_followed_since),
            style = RideCompTheme.typography.bodyStrong,
            color = RideCompTheme.colors.ok,
        )
        BigButton(
            text = stringResource(R.string.plan_stop),
            onClick = { onEvent(PlanDetailUiEvent.Stop) },
            style = BigButtonStyle.SECONDARY,
            size = BigButtonSize.SECONDARY,
        )
    } else {
        BigButton(
            text = stringResource(R.string.plan_start),
            onClick = { onEvent(PlanDetailUiEvent.Start) },
            size = BigButtonSize.SECONDARY,
        )
        if (state.active != null) {
            Text(
                text = stringResource(R.string.plan_start_replaces),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
            )
        }
    }
    (state.plan?.ref as? PlanRef.Custom)?.let { custom ->
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
            TextButton(onClick = { onEdit(custom.id) }) { Text(stringResource(R.string.plan_edit)) }
            TextButton(onClick = { onEvent(PlanDetailUiEvent.AskDelete) }) {
                Text(stringResource(R.string.plan_delete), color = RideCompTheme.colors.danger)
            }
        }
    }
}

/** "MINGGU 2" with its seven days; today in green. */
@Composable
private fun WeekCard(index: Int, week: PlanWeek, todayIndex: Int?) {
    val colors = RideCompTheme.colors
    RideCompCard {
        Text(
            text = stringResource(R.string.plan_week, index + 1).uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = colors.textSecondary,
        )
        week.days.forEachIndexed { day, session ->
            val isToday = day == todayIndex
            Row(modifier = Modifier.padding(top = RideCompDimens.spacingXs)) {
                Text(
                    text = stringResource(if (isToday) R.string.plan_today_label else R.string.plan_day, day + 1),
                    style = RideCompTheme.typography.caption,
                    color = if (isToday) colors.ok else colors.textTertiary,
                    modifier = Modifier.weight(DAY_LABEL_WEIGHT),
                )
                Text(
                    text = session.summary(),
                    style = if (session != null) RideCompTheme.typography.bodyStrong else RideCompTheme.typography.body,
                    color = when {
                        isToday -> colors.ok
                        session != null -> colors.textPrimary
                        else -> colors.textTertiary
                    },
                    modifier = Modifier.weight(SESSION_WEIGHT),
                )
            }
        }
    }
}

/** What each zone means and how to ride it, also without sensors. */
@Composable
private fun IntensityGuide() {
    SectionHeader(stringResource(R.string.plan_zones_title), horizontalPadding = RideCompDimens.none)
    Intensity.entries.forEach { intensity ->
        Column {
            Text(
                text = stringResource(intensity.titleRes()),
                style = RideCompTheme.typography.bodyStrong,
                color = RideCompTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(intensity.howToRes()),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
fun PlanBuilderRoute(
    planId: Long?,
    onSaved: (PlanRef) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanBuilderViewModel = koinViewModel(key = planId.toString()) { parametersOf(planId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is PlanBuilderEffect.Saved -> onSaved(effect.ref)
        }
    }
    PlanBuilderScreen(state, viewModel::onEvent, onBack, modifier)
}

/** Name, weeks, and one week of rides that repeats (T7.4 simple builder). */
@Composable
fun PlanBuilderScreen(
    state: PlanBuilderUiState,
    onEvent: (PlanBuilderUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(if (state.id == 0L) R.string.plan_builder_title else R.string.plan_builder_edit_title),
        onBack = onBack,
        backLabel = stringResource(R.string.plans_back),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) {
            RideCompTextField(
                label = stringResource(R.string.plan_builder_name),
                value = state.name,
                onValueChange = { onEvent(PlanBuilderUiEvent.NameChanged(it)) },
            )
            ChoiceChips(
                label = stringResource(R.string.plan_builder_weeks),
                choices = WEEK_CHOICES.map { Choice(it, pluralStringResource(R.plurals.plans_weeks, it, it)) }
                    .toImmutableList(),
                selected = state.weekCount,
                onSelect = { onEvent(PlanBuilderUiEvent.WeeksPicked(it)) },
                horizontalPadding = RideCompDimens.none,
                columns = WEEK_COLUMNS,
            )
            BuilderWeek(state, onEvent)
            BigButton(
                text = stringResource(R.string.plan_builder_save),
                onClick = { onEvent(PlanBuilderUiEvent.Save) },
                isEnabled = state.isLoaded,
                size = BigButtonSize.SECONDARY,
            )
        }
    }
    state.editingDay?.let { day -> DayDialog(day, state.days[day], onEvent) }
}

@Composable
private fun BuilderWeek(state: PlanBuilderUiState, onEvent: (PlanBuilderUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        SectionHeader(stringResource(R.string.plan_builder_week), horizontalPadding = RideCompDimens.none)
        Text(
            text = stringResource(R.string.plan_builder_week_hint),
            style = RideCompTheme.typography.caption,
            color = RideCompTheme.colors.textSecondary,
        )
        state.days.forEachIndexed { day, session ->
            RideCompCard(onClick = { onEvent(PlanBuilderUiEvent.EditDay(day)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.plan_day, day + 1),
                        style = RideCompTheme.typography.caption,
                        color = RideCompTheme.colors.textTertiary,
                        modifier = Modifier.weight(DAY_LABEL_WEIGHT),
                    )
                    Text(
                        text = session.summary(),
                        style = RideCompTheme.typography.bodyStrong,
                        color = if (session !=
                            null
                        ) {
                            RideCompTheme.colors.textPrimary
                        } else {
                            RideCompTheme.colors.textTertiary
                        },
                        modifier = Modifier.weight(SESSION_WEIGHT),
                    )
                }
            }
        }
        if (PlanField.SESSIONS in state.invalidFields) {
            Text(
                text = stringResource(R.string.plan_builder_no_rides),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.danger,
            )
        }
    }
}

/** Rest, or a zone and a duration for one day. */
@Composable
private fun DayDialog(day: Int, current: PlanSession?, onEvent: (PlanBuilderUiEvent) -> Unit) {
    var intensity by rememberSaveable(day) { mutableStateOf(current?.intensity) }
    var minutes by rememberSaveable(day) { mutableIntStateOf(current?.durationMin ?: DEFAULT_MINUTES) }
    AlertDialog(
        onDismissRequest = { onEvent(PlanBuilderUiEvent.DismissDay) },
        title = { Text(stringResource(R.string.plan_day, day + 1)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
                ChoiceChips(
                    label = stringResource(R.string.plan_builder_intensity),
                    choices = (
                        listOf(Choice<Intensity?>(null, stringResource(R.string.plan_rest_day))) +
                            Intensity.entries.map { Choice<Intensity?>(it, stringResource(it.titleRes())) }
                        ).toImmutableList(),
                    selected = intensity,
                    onSelect = { intensity = it },
                    horizontalPadding = RideCompDimens.none,
                    columns = 2,
                )
                if (intensity != null) {
                    ChoiceChips(
                        label = stringResource(R.string.plan_builder_duration),
                        choices = DURATION_CHOICES.map {
                            Choice(it, stringResource(R.string.plan_minutes, it))
                        }.toImmutableList(),
                        selected = minutes,
                        onSelect = { minutes = it },
                        horizontalPadding = RideCompDimens.none,
                        columns = DURATION_COLUMNS,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(PlanBuilderUiEvent.DaySet(intensity?.let { PlanSession(it, minutes) })) }) {
                Text(stringResource(R.string.plan_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onEvent(PlanBuilderUiEvent.DismissDay)
            }) { Text(stringResource(R.string.plan_cancel)) }
        },
    )
}

private val WEEK_CHOICES = listOf(2, 4, 6, 8, 12)
private const val WEEK_COLUMNS = 5
private val DURATION_CHOICES = listOf(30, 45, 60, 90, 120, 180, 240)
private const val DURATION_COLUMNS = 4
private const val DEFAULT_MINUTES = 60
private const val DAY_LABEL_WEIGHT = 0.3f
private const val SESSION_WEIGHT = 0.7f

@ThemePreviews
@Composable
private fun PlansPreview() {
    val plans = BuiltInPlans.ALL
    RideCompPreview {
        PlansScreen(
            state = PlansUiState(plans.toImmutableList(), isLoaded = true),
            onOpenPlan = {},
            onBuild = {},
            onBack = {},
        )
    }
}

@ThemePreviews
@Composable
private fun PlanBuilderPreview() {
    RideCompPreview { PlanBuilderScreen(PlanBuilderUiState(isLoaded = true), onEvent = {}, onBack = {}) }
}
