package com.ridecomp.feature.ride.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.ridecomp.feature.ride.model.TurnArrows
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.GradeProfile
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

@Composable
private fun TurnBanner(turn: UpcomingTurn, formatter: UnitFormatter) {
    AlertBanner(
        message = stringResource(
            R.string.ride_turn_banner,
            TurnArrows.of(turn.direction),
            stringResource(turn.labelRes()),
            formatter.shortDistance(turn.distanceM),
            formatter.elevationUnit,
        ),
        severity = AlertSeverity.WARN,
    )
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

/** Climb page panel (§6.2): what is left of the climb and its grade profile with the rider's position. */
@Composable
fun ClimbPanel(climb: ClimbStatus, formatter: UnitFormatter, modifier: Modifier = Modifier) {
    val average = formatter.grade(climb.climb.avgGradePercent)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingS)) {
        Text(
            text = stringResource(
                R.string.ride_climb_remaining,
                formatter.distance(climb.remainingDistanceM),
                formatter.distanceUnit,
                formatter.elevation(climb.remainingGainM),
                formatter.elevationUnit,
                formatter.grade(climb.remainingAvgGradePercent),
            ),
            style = RideCompTheme.typography.body,
            color = RideCompTheme.colors.textPrimary,
        )
        GradeProfile(
            title = stringResource(R.string.ride_climb_title, climb.number, climb.total),
            gradesPercent = climb.climb.gradeProfilePercent.toImmutableList(),
            bandUpperBoundsPercent = ClimbConfig.GRADE_BANDS_PERCENT.toImmutableList(),
            summary = stringResource(R.string.ride_climb_profile_description, average),
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
            ClimbPanel(ClimbStatus(2, 4, 1_200.0, 80.0, PREVIEW_CLIMB), formatter)
        }
    }
}
