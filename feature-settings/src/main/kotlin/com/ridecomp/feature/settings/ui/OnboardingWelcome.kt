package com.ridecomp.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.ridecomp.feature.settings.R
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.WindowWidthClass

/**
 * First screen of onboarding (T10.12, D-105): the mark, a short promise, a sample of the ride screen and
 * three highlights, then "Mulai". The content slides in once when first shown; nothing loops.
 */
@Composable
fun WelcomeStep(onStart: () -> Unit) {
    var isShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { isShown = true }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    top = RideCompDimens.screenPaddingTop,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AnimatedVisibility(
                visible = isShown,
                enter = fadeIn(tween(ENTER_MS)) + slideInVertically(tween(ENTER_MS)) { it / SLIDE_FRACTION },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap)) {
                    Brand()
                    Headline()
                    // The sample card is only a picture: below 360 dp it gives way to the button (T13.5).
                    if (RideCompTheme.widthClass == WindowWidthClass.REGULAR) SampleRideCard()
                    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
                        WELCOME_FEATURES.forEach { Feature(it) }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(top = RideCompDimens.blockGap),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BigButton(
                    text = stringResource(R.string.welcome_start),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.welcome_footnote),
                    style = RideCompTheme.typography.caption,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Brand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        Box(
            modifier = Modifier
                .size(RideCompDimens.welcomeMarkSize)
                .clip(RoundedCornerShape(RideCompDimens.cornerRadiusCard))
                .background(RideCompTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                // White needle on the dark theme, black on the light one (T11.10).
                painter = painterResource(
                    if (RideCompTheme.colors.isDark) UiR.drawable.ic_ridecomp_mark else MARK_LIGHT,
                ),
                contentDescription = null,
                modifier = Modifier.size(RideCompDimens.iconTileSize),
            )
        }
        Text(
            text = stringResource(R.string.welcome_app_name),
            style = RideCompTheme.typography.title,
            color = RideCompTheme.colors.textPrimary,
        )
    }
}

/** "Ubah HP-mu jadi cycling computer." with the last words in the accent colour. */
@Composable
private fun Headline() {
    val colors = RideCompTheme.colors
    val start = stringResource(R.string.welcome_title_start)
    val accent = stringResource(R.string.welcome_title_accent)
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        Text(
            text = buildAnnotatedString {
                append(start)
                append(" ")
                withStyle(SpanStyle(color = colors.accent)) { append(accent) }
            },
            style = RideCompTheme.typography.display.copy(
                fontSize = RideCompTheme.dimens.displaySize,
                lineHeight = RideCompTheme.dimens.displayLineHeight,
            ),
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.welcome_text),
            style = RideCompTheme.typography.body,
            color = colors.textTertiary,
        )
    }
}

/** A small, static picture of the ride screen: what the rider will get. */
@Composable
private fun SampleRideCard() {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusHero)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceAlt)
            .border(RideCompDimens.borderThin, colors.divider, shape)
            .padding(RideCompDimens.spacingM),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(RideCompDimens.markerDotRadius * 2).clip(CircleShape).background(colors.danger))
            Text(
                text = stringResource(R.string.welcome_sample_rec),
                style = RideCompTheme.typography.dataLabel,
                color = colors.danger,
                modifier = Modifier.padding(start = RideCompDimens.spacingS),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = SAMPLE_SPEED, style = RideCompTheme.typography.welcomeSpeed, color = colors.textPrimary)
            Text(
                text = stringResource(R.string.welcome_sample_speed_unit),
                style = RideCompTheme.typography.bodyStrong,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = RideCompDimens.spacingS, bottom = RideCompDimens.spacingS),
            )
        }
        Text(
            text = stringResource(R.string.welcome_sample_line),
            style = RideCompTheme.typography.bodyStrong,
            color = colors.accent,
        )
    }
}

private enum class WelcomeFeature(@param:DrawableRes val iconRes: Int, @param:StringRes val textRes: Int) {
    RECORD(UiR.drawable.ic_location, R.string.welcome_feature_record),
    NAVIGATE(UiR.drawable.ic_map, R.string.welcome_feature_navigate),
    FREE(UiR.drawable.ic_heart, R.string.welcome_feature_free),
}

private val WELCOME_FEATURES = WelcomeFeature.entries

@Composable
private fun Feature(feature: WelcomeFeature) {
    val colors = RideCompTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        Box(
            modifier = Modifier
                .size(RideCompDimens.welcomeFeatureIcon)
                .clip(CircleShape)
                .background(colors.accentTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(feature.iconRes),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(RideCompDimens.iconSize),
            )
        }
        Text(
            text = stringResource(feature.textRes),
            style = RideCompTheme.typography.bodyStrong,
            color = colors.textPrimary,
        )
    }
}

/** A sample number on a picture of the screen, not data: same in every language. */
private const val SAMPLE_SPEED = "27.4"
private const val ENTER_MS = 450
private const val SLIDE_FRACTION = 8

@ThemePreviews
@Composable
private fun WelcomeStepPreview() {
    RideCompPreview { WelcomeStep(onStart = {}) }
}

private val MARK_LIGHT = UiR.drawable.ic_ridecomp_mark_light
