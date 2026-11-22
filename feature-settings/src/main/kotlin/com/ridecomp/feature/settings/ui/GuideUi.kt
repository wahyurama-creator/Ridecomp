package com.ridecomp.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.GuidePreferences
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.GuideTopic
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.GuideUiEvent
import com.ridecomp.feature.settings.viewmodel.GuideViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.CardTone
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.TipCard
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import org.koin.androidx.compose.koinViewModel

/**
 * A tip for this screen when the rider's level shows it and it was not closed (Phase 4G, TG.3). Other
 * features get it through a slot from the app (ARCH-02).
 */
@Composable
fun GuideTip(
    tip: GuideTip,
    onLearnMore: (GuideTopic) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideViewModel = koinViewModel(key = "tip-${tip.storageKey}"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.preferences?.shows(tip) != true) return
    TipCard(
        title = stringResource(tip.topic.titleRes()),
        text = stringResource(tip.textRes()),
        learnMoreLabel = stringResource(R.string.guide_tip_learn_more),
        closeLabel = stringResource(R.string.guide_tip_close),
        onLearnMore = { onLearnMore(tip.topic) },
        onClose = { viewModel.onEvent(GuideUiEvent.Dismiss(tip)) },
        modifier = modifier,
    )
}

/** Three cards: beginner, intermediate, experienced, each with how long and what it changes (TG.1). */
@Composable
fun RiderLevelChooser(selected: RiderLevel?, onSelect: (RiderLevel) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap)) {
        RiderLevel.entries.forEach { level ->
            val isSelected = level == selected
            RideCompCard(tone = if (isSelected) CardTone.ACCENT else CardTone.NORMAL, onClick = { onSelect(level) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
                ) {
                    LevelIcon(level, isSelected)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(level.titleRes()),
                            style = RideCompTheme.typography.cardTitle,
                            color = RideCompTheme.colors.textPrimary,
                        )
                        Text(
                            text = stringResource(level.hintRes()),
                            style = RideCompTheme.typography.caption,
                            color = RideCompTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** The level's icon in a circle, filled with the accent when chosen (T10.12). */
@Composable
private fun LevelIcon(level: RiderLevel, isSelected: Boolean) {
    val colors = RideCompTheme.colors
    Box(
        modifier = Modifier
            .size(RideCompDimens.welcomeFeatureIcon)
            .clip(CircleShape)
            .background(if (isSelected) colors.accent else colors.accentTint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                when (level) {
                    RiderLevel.BEGINNER -> UiR.drawable.ic_nav_ride
                    RiderLevel.INTERMEDIATE -> UiR.drawable.ic_flag
                    RiderLevel.EXPERIENCED -> UiR.drawable.ic_trophy
                },
            ),
            contentDescription = null,
            tint = if (isSelected) colors.onAccent else colors.accent,
            modifier = Modifier.size(RideCompDimens.iconSize),
        )
    }
}

/** Help & guides (TG.2): the rider's level, every topic, and "show tips again". */
@Composable
fun HelpRoute(
    onOpenTopic: (GuideTopic) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HelpScreen(state.preferences, viewModel::onEvent, onOpenTopic, onBack, modifier)
}

@Composable
fun HelpScreen(
    prefs: GuidePreferences?,
    onEvent: (GuideUiEvent) -> Unit,
    onOpenTopic: (GuideTopic) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isReset by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.guide_help_title),
        onBack = onBack,
        backLabel = stringResource(R.string.onboarding_back_label),
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
            SectionHeader(stringResource(R.string.guide_help_topics), horizontalPadding = RideCompDimens.none)
            ListCard {
                GuideTopic.entries.forEachIndexed { index, topic ->
                    ListCardRow(
                        title = stringResource(topic.titleRes()),
                        subtitle = stringResource(topic.summaryRes()),
                        hasDivider = index < GuideTopic.entries.lastIndex,
                        onClick = { onOpenTopic(topic) },
                    )
                }
            }
            SectionHeader(stringResource(R.string.guide_help_level), horizontalPadding = RideCompDimens.none)
            RiderLevelChooser(selected = prefs?.effectiveLevel, onSelect = { onEvent(GuideUiEvent.SetLevel(it)) })
            BigButton(
                text = stringResource(if (isReset) R.string.guide_help_reset_done else R.string.guide_help_reset),
                onClick = {
                    onEvent(GuideUiEvent.ResetTips)
                    isReset = true
                },
                style = BigButtonStyle.TEXT,
                isEnabled = !isReset,
            )
        }
    }
}

/** Lines of a guide body that start with this are sub-points. */
private const val GUIDE_BULLET = "•"

/** One guide in plain words, step by step (TG.2). */
@Composable
fun HelpTopicScreen(topic: GuideTopic, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(topic.titleRes()),
        subtitle = stringResource(topic.summaryRes()),
        onBack = onBack,
        backLabel = stringResource(R.string.onboarding_back_label),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
        ) {
            stringResource(topic.bodyRes()).lines().filter { it.isNotBlank() }.forEach { line ->
                // "•" lines belong to the line above them: indented under it (T12.3).
                Text(
                    text = line,
                    style = RideCompTheme.typography.body,
                    color = RideCompTheme.colors.textPrimary,
                    modifier = Modifier.padding(
                        start = if (line.startsWith(GUIDE_BULLET)) RideCompDimens.spacingL else RideCompDimens.none,
                    ),
                )
            }
        }
    }
}

@StringRes
private fun RiderLevel.titleRes(): Int = when (this) {
    RiderLevel.BEGINNER -> R.string.guide_level_beginner
    RiderLevel.INTERMEDIATE -> R.string.guide_level_intermediate
    RiderLevel.EXPERIENCED -> R.string.guide_level_experienced
}

@StringRes
private fun RiderLevel.hintRes(): Int = when (this) {
    RiderLevel.BEGINNER -> R.string.guide_level_beginner_hint
    RiderLevel.INTERMEDIATE -> R.string.guide_level_intermediate_hint
    RiderLevel.EXPERIENCED -> R.string.guide_level_experienced_hint
}

@StringRes
private fun GuideTip.textRes(): Int = when (this) {
    GuideTip.FIRST_RIDE -> R.string.guide_tip_first_ride
    GuideTip.ROUTES -> R.string.guide_tip_routes
    GuideTip.SENSORS -> R.string.guide_tip_sensors
    GuideTip.EXPORT -> R.string.guide_tip_export
    GuideTip.BACKUP -> R.string.guide_tip_backup
    GuideTip.OFFLINE_MAPS -> R.string.guide_tip_offline_maps
    GuideTip.HEART_RATE_ZONES -> R.string.guide_tip_heart_rate_zones
}

/** Title, summary and body of a Help topic. */
internal class GuideTexts(@StringRes val title: Int, @StringRes val summary: Int, @StringRes val body: Int)

/** The texts of every Help topic; a table, not three `when`s (a test checks that no topic is missing). */
internal val GUIDE_TEXTS: Map<GuideTopic, GuideTexts> = mapOf(
    GuideTopic.FIRST_RIDE to GuideTexts(
        R.string.guide_first_ride_title,
        R.string.guide_first_ride_summary,
        R.string.guide_first_ride_body,
    ),
    GuideTopic.ROUTES_GPX to GuideTexts(
        R.string.guide_routes_gpx_title,
        R.string.guide_routes_gpx_summary,
        R.string.guide_routes_gpx_body,
    ),
    GuideTopic.NAVIGATION to GuideTexts(
        R.string.guide_navigation_title,
        R.string.guide_navigation_summary,
        R.string.guide_navigation_body,
    ),
    GuideTopic.OFFLINE_MAPS to GuideTexts(
        R.string.guide_offline_maps_title,
        R.string.guide_offline_maps_summary,
        R.string.guide_offline_maps_body,
    ),
    GuideTopic.SENSORS to GuideTexts(
        R.string.guide_sensors_title,
        R.string.guide_sensors_summary,
        R.string.guide_sensors_body,
    ),
    GuideTopic.WATCHES to GuideTexts(
        R.string.guide_watches_title,
        R.string.guide_watches_summary,
        R.string.guide_watches_body,
    ),
    GuideTopic.EXPORT_STRAVA to GuideTexts(
        R.string.guide_export_strava_title,
        R.string.guide_export_strava_summary,
        R.string.guide_export_strava_body,
    ),
    GuideTopic.BACKUP to GuideTexts(
        R.string.guide_backup_title,
        R.string.guide_backup_summary,
        R.string.guide_backup_body,
    ),
    GuideTopic.RECORDS_SEGMENTS to GuideTexts(
        R.string.guide_records_segments_title,
        R.string.guide_records_segments_summary,
        R.string.guide_records_segments_body,
    ),
    GuideTopic.TRAINING to GuideTexts(
        R.string.guide_training_title,
        R.string.guide_training_summary,
        R.string.guide_training_body,
    ),
    GuideTopic.SHARE_CARD to GuideTexts(
        R.string.guide_share_card_title,
        R.string.guide_share_card_summary,
        R.string.guide_share_card_body,
    ),
    GuideTopic.SAFETY to GuideTexts(
        R.string.guide_safety_title,
        R.string.guide_safety_summary,
        R.string.guide_safety_body,
    ),
    GuideTopic.BATTERY to GuideTexts(
        R.string.guide_battery_title,
        R.string.guide_battery_summary,
        R.string.guide_battery_body,
    ),
    GuideTopic.GPS_SIGNAL to GuideTexts(
        R.string.guide_gps_signal_title,
        R.string.guide_gps_signal_summary,
        R.string.guide_gps_signal_body,
    ),
)

@StringRes
private fun GuideTopic.titleRes(): Int = GUIDE_TEXTS.getValue(this).title

@StringRes
private fun GuideTopic.summaryRes(): Int = GUIDE_TEXTS.getValue(this).summary

@StringRes
private fun GuideTopic.bodyRes(): Int = GUIDE_TEXTS.getValue(this).body

@ThemePreviews
@Composable
private fun HelpPreview() {
    RideCompPreview { HelpScreen(GuidePreferences(RiderLevel.BEGINNER), onEvent = {}, onOpenTopic = {}, onBack = {}) }
}
