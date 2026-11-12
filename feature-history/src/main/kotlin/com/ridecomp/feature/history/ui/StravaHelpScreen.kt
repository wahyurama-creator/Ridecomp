package com.ridecomp.feature.history.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.ridecomp.feature.history.R
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Strava's upload page, opened in the browser (04-INTEGRATIONS §E.1). The app itself sends nothing. */
private const val STRAVA_UPLOAD_URL = "https://www.strava.com/upload/select"

/** Manual upload guide (T1.15). Steps to be re-checked on a phone browser (04-INTEGRATIONS §E.1.3). */
@Composable
fun StravaHelpRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    StravaHelpScreen(
        onOpenUpload = { context.startActivity(Intent(Intent.ACTION_VIEW, STRAVA_UPLOAD_URL.toUri())) },
        modifier = modifier,
    )
}

@Composable
fun StravaHelpScreen(onOpenUpload: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.strava_title)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            listOf(
                R.string.strava_intro,
                R.string.strava_step_1,
                R.string.strava_step_2,
                R.string.strava_step_3,
            ).forEach { resId ->
                Text(
                    text = stringResource(resId),
                    style = RideCompTheme.typography.body,
                    color = RideCompTheme.colors.textPrimary,
                )
            }
            Text(
                text = stringResource(R.string.strava_note),
                style = RideCompTheme.typography.dataLabel,
                color = RideCompTheme.colors.textSecondary,
            )
            BigButton(
                text = stringResource(R.string.strava_open),
                onClick = onOpenUpload,
                style = BigButtonStyle.SECONDARY,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun StravaHelpPreview() {
    RideCompPreview { StravaHelpScreen(onOpenUpload = {}) }
}
