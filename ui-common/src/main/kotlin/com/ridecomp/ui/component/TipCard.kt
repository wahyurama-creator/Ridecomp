package com.ridecomp.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/**
 * A dismissible tip in plain words for new riders (Phase 4G): what this screen is for, a link to the full
 * Help topic and a close button. Shown only for the rider's level (decided by the caller).
 */
@Composable
fun TipCard(
    title: String,
    text: String,
    learnMoreLabel: String,
    closeLabel: String,
    onLearnMore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The icon at the left; the info icon in green by default. */
    @DrawableRes iconRes: Int = R.drawable.ic_info,
    iconTint: Color? = null,
) {
    val colors = RideCompTheme.colors
    // The whole card opens the Help topic (a large touch target, UI-07); the link only says so.
    RideCompCard(modifier = modifier, onClick = onLearnMore) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
            IconTile(iconRes, tint = iconTint ?: colors.ok)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
            ) {
                Text(text = title, style = RideCompTheme.typography.bodyStrong, color = colors.textPrimary)
                Text(text = text, style = RideCompTheme.typography.caption, color = colors.textSecondary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
                ) {
                    Text(text = learnMoreLabel, style = RideCompTheme.typography.navLabel, color = colors.accent)
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(RideCompDimens.statusIconSize),
                    )
                }
            }
            // A light close icon: the tip should not look like an action of the screen.
            Box(
                modifier = Modifier
                    .size(RideCompDimens.touchTargetMin)
                    .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = onClose)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.TopEnd,
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(RideCompDimens.iconSize),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun TipCardPreview() {
    RideCompPreview {
        TipCard(
            title = "Apa itu GPX?",
            text = "File berisi garis rute. Buat di Strava/Komoot, lalu import di sini.",
            learnMoreLabel = "Pelajari",
            closeLabel = "Tutup tips",
            onLearnMore = {},
            onClose = {},
        )
    }
}
