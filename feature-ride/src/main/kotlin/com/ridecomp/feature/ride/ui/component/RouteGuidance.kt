package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ridecomp.core.engine.route.ClimbConfig
import com.ridecomp.core.engine.route.ClimbStatus
import com.ridecomp.core.engine.route.NextClimb
import com.ridecomp.core.engine.route.RouteClimb
import com.ridecomp.core.engine.route.RouteProgress
import com.ridecomp.core.engine.route.TurnDirection
import com.ridecomp.core.engine.route.TurnSeverity
import com.ridecomp.core.engine.route.UpcomingTurn
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.R
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.DataField
import com.ridecomp.ui.component.GradeProfile
import com.ridecomp.ui.component.LabelTone
import com.ridecomp.ui.component.gradeBandColor
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.toImmutableList

/**
 * Turn and climb banners (12-FEATURE-SPECS §6.2, §7.2). Off route has priority: turns are already hidden
 * by the tracker while off route, and the climb notice waits too.
 */
@Composable
fun RouteGuidanceBanners(progress: RouteProgress, formatter: UnitFormatter) {
    if (progress.isOffRoute) return
    progress.nextTurn?.takeIf { it.isAnnounced }?.let { TurnBanner(it, formatter) }
    progress.nextClimb?.takeIf { it.isAnnounced }?.let { ClimbAheadBanner(it, formatter) }
}

/** White turn card with a big arrow, the distance and the turn (13-UI-SCREENS #16, 12-FEATURE-SPECS §7.2). */
@Composable
private fun TurnBanner(turn: UpcomingTurn, formatter: UnitFormatter) {
    val colors = RideCompTheme.colors
    val label = stringResource(turn.labelRes())
    val distance =
        stringResource(R.string.ride_turn_distance, formatter.shortDistance(turn.distanceM), formatter.elevationUnit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RideCompDimens.spacingSm, vertical = RideCompDimens.spacingXs)
            .clip(RoundedCornerShape(RideCompDimens.cornerRadiusButton))
            .background(colors.textPrimary)
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.cardPaddingVertical)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
    ) {
        Icon(
            painterResource(turn.iconRes()),
            contentDescription = null,
            tint = colors.bg,
            modifier = Modifier.size(RideCompDimens.turnArrowSize),
        )
        Column {
            Text(text = distance, style = RideCompTheme.typography.turnDistance, color = colors.bg)
            Text(text = label, style = RideCompTheme.typography.cardTitle, color = colors.bg)
        }
    }
}

private fun UpcomingTurn.iconRes(): Int = when (direction) {
    TurnDirection.UTURN -> UiR.drawable.ic_turn_uturn
    TurnDirection.LEFT -> if (severity ==
        TurnSeverity.NORMAL
    ) {
        UiR.drawable.ic_turn_left
    } else {
        UiR.drawable.ic_turn_sharp_left
    }
    TurnDirection.RIGHT -> if (severity ==
        TurnSeverity.NORMAL
    ) {
        UiR.drawable.ic_turn_right
    } else {
        UiR.drawable.ic_turn_sharp_right
    }
}

private fun UpcomingTurn.labelRes(): Int = when (direction) {
    TurnDirection.UTURN -> R.string.ride_turn_uturn
    TurnDirection.LEFT -> if (severity ==
        TurnSeverity.NORMAL
    ) {
        R.string.ride_turn_left
    } else {
        R.string.ride_turn_sharp_left
    }
    TurnDirection.RIGHT ->
        if (severity == TurnSeverity.NORMAL) R.string.ride_turn_right else R.string.ride_turn_sharp_right
}

@Composable
private fun ClimbAheadBanner(next: NextClimb, formatter: UnitFormatter) {
    AlertBanner(
        message = stringResource(
            R.string.ride_climb_ahead,
            formatter.shortDistance(next.distanceToStartM),
            formatter.elevationUnit,
            formatter.distance(next.climb.lengthM),
            formatter.distanceUnit,
            formatter.grade(next.climb.avgGradePercent),
        ),
    )
}

/**
 * Climb page while on a climb (13-UI-SCREENS #8, 12-FEATURE-SPECS §6.2): "TANJAKAN 2 / 4" with the
 * distance left as hero, gain left and current grade (in its band color), then the climb profile.
 */
@Composable
fun ClimbPage(
    climb: ClimbStatus,
    currentGradePercent: Double?,
    formatter: UnitFormatter,
    modifier: Modifier = Modifier,
) {
    val colors = RideCompTheme.colors
    val divider = if (colors.isDark) RideCompDimens.dividerThickness else RideCompDimens.dividerThicknessLight
    Column(
        modifier = modifier.fillMaxSize().background(colors.divider),
        verticalArrangement = Arrangement.spacedBy(divider),
    ) {
        DataField(
            label = stringResource(R.string.ride_climb_hero, climb.number, climb.total),
            value = formatter.distance(climb.remainingDistanceM),
            tag = stringResource(R.string.ride_climb_hero_tag, formatter.distanceUnit),
            isHero = true,
            labelTone = LabelTone.ACCENT,
            modifier = Modifier.fillMaxWidth().weight(CLIMB_HERO_WEIGHT),
        )
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(divider)) {
            DataField(
                label = stringResource(R.string.ride_climb_gain_left),
                value = formatter.elevation(climb.remainingGainM),
                unit = formatter.elevationUnit,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            DataField(
                label = stringResource(R.string.metric_grade),
                value = formatter.grade(currentGradePercent),
                tag = stringResource(R.string.ride_climb_avg_left, formatter.grade(climb.remainingAvgGradePercent)),
                valueColor = gradeBandColor(currentGradePercent, GRADE_BANDS),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        ClimbProfilePanel(climb, formatter, Modifier.fillMaxWidth().weight(CLIMB_PROFILE_WEIGHT))
    }
}

private const val CLIMB_HERO_WEIGHT = 1.4f
private const val CLIMB_PROFILE_WEIGHT = 1.6f

@Composable
private fun ClimbProfilePanel(climb: ClimbStatus, formatter: UnitFormatter, modifier: Modifier) {
    val profile = remember(climb.climb) { climb.climb.displayProfile() }
    val grades = remember(profile) { profile.gradesPercent.toImmutableList() }
    val summary = stringResource(
        R.string.ride_climb_summary,
        formatter.distance(climb.climb.lengthM),
        formatter.distanceUnit,
        formatter.grade(climb.climb.avgGradePercent),
        formatter.elevation(climb.climb.gainM),
        formatter.elevationUnit,
    )
    Column(
        modifier = modifier
            .background(RideCompTheme.colors.bg)
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingSm),
    ) {
        Row {
            Text(
                text = stringResource(R.string.ride_climb_profile).uppercase(),
                style = RideCompTheme.typography.dataLabel,
                color = RideCompTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(text = summary, style = RideCompTheme.typography.dataLabel, color = RideCompTheme.colors.textSecondary)
        }
        GradeProfile(
            title = stringResource(
                R.string.ride_climb_section,
                formatter.shortDistance(profile.sectionM),
                formatter.elevationUnit,
            ),
            gradesPercent = grades,
            bandUpperBoundsPercent = GRADE_BANDS,
            summary = stringResource(
                R.string.ride_climb_profile_description,
                formatter.grade(climb.climb.avgGradePercent),
            ),
            markerFraction = climb.progressFraction,
        )
    }
}

/** "Next climb: 3.4 km away" shown outside climbs (§6.2). */
@Composable
fun NextClimbInfo(next: NextClimb, formatter: UnitFormatter, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            R.string.ride_next_climb,
            next.number,
            next.total,
            formatter.distance(next.distanceToStartM),
            formatter.distanceUnit,
        ),
        style = RideCompTheme.typography.body,
        color = RideCompTheme.colors.textSecondary,
        modifier = modifier.padding(horizontal = RideCompDimens.spacingS),
    )
}

private val GRADE_BANDS = ClimbConfig.GRADE_BANDS_PERCENT.toImmutableList()

private val PREVIEW_CLIMB = RouteClimb(
    index = 1,
    startDistanceM = 4_000.0,
    endDistanceM = 6_100.0,
    gainM = 132.0,
    avgGradePercent = 6.3,
    maxGradePercent = 11.2,
    gradeProfilePercent = listOf(3.1, 4.8, 6.0, 7.5, 9.4, 11.2, 8.0, 6.1, 5.2, 4.0, 3.5),
)

@ThemePreviews
@Composable
private fun RouteGuidancePreview() {
    RideCompPreview {
        val formatter = UnitFormatter(UnitSystem.METRIC)
        Column {
            TurnBanner(UpcomingTurn(TurnDirection.LEFT, TurnSeverity.SHARP, 150.0, isAnnounced = true), formatter)
            ClimbAheadBanner(NextClimb(2, 4, 480.0, PREVIEW_CLIMB, isAnnounced = true), formatter)
            ClimbPage(
                ClimbStatus(2, 4, 1_200.0, 80.0, PREVIEW_CLIMB),
                7.0,
                formatter,
                Modifier.height(RideCompDimens.previewPageHeight),
            )
        }
    }
}
