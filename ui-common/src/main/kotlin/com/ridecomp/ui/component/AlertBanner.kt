package com.ridecomp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Severity drives the banner color (02-DESIGN §3.13). */
enum class AlertSeverity { INFO, WARN, DANGER }

/** One optional action button on a banner (e.g. "Done", "Snooze 5 min", "Fix"). */
data class BannerAction(val label: String, val onClick: () -> Unit)

/**
 * Non-blocking top banner for alerts during a ride (sensor lost, off-route, reminders, battery mode).
 * Never covers the data fields full-screen (02-DESIGN §3.4, §3.13).
 */
@Composable
fun AlertBanner(
    message: String,
    modifier: Modifier = Modifier,
    severity: AlertSeverity = AlertSeverity.INFO,
    actions: List<BannerAction> = emptyList(),
) {
    val colors = RideCompTheme.colors
    val (container, content) = when (severity) {
        AlertSeverity.INFO -> colors.surface to colors.textPrimary
        AlertSeverity.WARN -> colors.warn to colors.onWarn
        AlertSeverity.DANGER -> colors.danger to colors.onDanger
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RideCompDimens.bannerMinHeight)
            .background(container)
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingS)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = RideCompTheme.typography.body,
            color = content,
        )
        actions.forEach { action ->
            BannerActionButton(action = action, contentColor = content)
        }
    }
}

@Composable
private fun BannerActionButton(action: BannerAction, contentColor: Color) {
    TextButton(
        onClick = action.onClick,
        modifier = Modifier.heightIn(min = RideCompDimens.touchTargetMin),
    ) {
        Text(text = action.label, style = RideCompTheme.typography.button, color = contentColor)
    }
}

@ThemePreviews
@Composable
private fun AlertBannerWarnPreview() {
    RideCompPreview {
        AlertBanner(message = "HR strap disconnected · reconnecting…", severity = AlertSeverity.WARN)
    }
}

@ThemePreviews
@Composable
private fun AlertBannerActionPreview() {
    RideCompPreview {
        AlertBanner(
            message = "Time to drink",
            actions = listOf(BannerAction("Done") {}, BannerAction("Snooze") {}),
        )
    }
}
