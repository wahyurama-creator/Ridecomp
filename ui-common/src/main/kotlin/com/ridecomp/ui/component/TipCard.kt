package com.ridecomp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
) {
    val colors = RideCompTheme.colors
    RideCompCard(modifier = modifier, tone = CardTone.OK) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.ok)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
            ) {
                Text(text = title, style = RideCompTheme.typography.bodyStrong, color = colors.textPrimary)
                Text(text = text, style = RideCompTheme.typography.caption, color = colors.textTertiary)
            }
            IconSquareButton(
                iconRes = R.drawable.ic_close,
                contentDescription = closeLabel,
                onClick = onClose,
                tint = colors.textSecondary,
                modifier = Modifier.size(RideCompDimens.touchTargetMin),
            )
        }
        LinkText(
            text = learnMoreLabel,
            onClick = onLearnMore,
            modifier = Modifier.padding(start = RideCompDimens.iconSize),
        )
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
