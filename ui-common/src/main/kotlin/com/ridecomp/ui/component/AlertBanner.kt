package com.ridecomp.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompColors
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Banner variants (02-DESIGN §3.13, 13-UI-SCREENS §2 RideBanner). Light backgrounds carry dark text. */
enum class AlertSeverity {
    /** Blue (drink/eat reminder, climb ahead). */
    INFO,

    /** Yellow (auto pause, sensor lost). */
    WARN,

    /** Red (off route, GPS lost). */
    DANGER,

    /** White (turn ahead). */
    NEUTRAL,

    /** Green (back on route, sensor reconnected). */
    OK,

    /** Thin `warnTint` strip (battery saver). */
    SAVER,
}

/** One action button on a banner (e.g. "Sudah", "Tunda 5 mnt"); the first one is filled. */
data class BannerAction(val label: String, val onClick: () -> Unit)

/**
 * Non-blocking banner card under the status strip (sensor lost, off-route, reminders, battery mode).
 * Never covers the data fields full-screen (02-DESIGN §3.4, §3.13). Announced politely by TalkBack.
 */
@Composable
fun AlertBanner(
    message: String,
    modifier: Modifier = Modifier,
    severity: AlertSeverity = AlertSeverity.INFO,
    actions: List<BannerAction> = emptyList(),
    title: String? = null,
    @DrawableRes iconRes: Int? = null,
) {
    val colors = RideCompTheme.colors
    val (container, content) = severity.look(colors)
    val isStrip = severity == AlertSeverity.SAVER
    val shape = RoundedCornerShape(if (isStrip) RideCompDimens.cornerRadius else RideCompDimens.cornerRadiusCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RideCompDimens.spacingSm, vertical = RideCompDimens.spacingXs)
            .clip(shape)
            .background(container)
            .heightIn(min = if (isStrip) RideCompDimens.iconSize else RideCompDimens.bannerMinHeight)
            .padding(
                horizontal = RideCompDimens.cardPaddingHorizontal,
                vertical = if (isStrip) RideCompDimens.spacingS else RideCompDimens.spacingSm,
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            iconRes?.let {
                // Decorative: the banner text says the same.
                Icon(
                    painterResource(it),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(
                        if (title !=
                            null
                        ) {
                            RideCompDimens.bannerIconSize
                        } else {
                            RideCompDimens.iconSize
                        },
                    ),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) Text(text = title, style = RideCompTheme.typography.title, color = content)
                Text(
                    text = message,
                    style = if (title == null) RideCompTheme.typography.bodyStrong else RideCompTheme.typography.body,
                    color = content,
                )
            }
        }
        if (actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
                actions.forEachIndexed { index, action ->
                    BannerActionButton(action, content, container, isFilled = index == 0, Modifier.weight(1f))
                }
            }
        }
    }
}

/** First action: filled with the banner's text color; others outlined. */
@Composable
private fun BannerActionButton(
    action: BannerAction,
    contentColor: Color,
    containerColor: Color,
    isFilled: Boolean,
    modifier: Modifier,
) {
    Button(
        onClick = action.onClick,
        modifier = modifier.heightIn(min = RideCompDimens.touchTargetMin),
        shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall),
        border = if (isFilled) null else BorderStroke(RideCompDimens.borderSelected, contentColor),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFilled) contentColor else Color.Transparent,
            contentColor = if (isFilled) containerColor else contentColor,
        ),
    ) {
        Text(text = action.label, style = RideCompTheme.typography.buttonSmall)
    }
}

private fun AlertSeverity.look(colors: RideCompColors): Pair<Color, Color> = when (this) {
    AlertSeverity.INFO -> colors.track to colors.onTrack
    AlertSeverity.WARN -> colors.warn to colors.onWarn
    AlertSeverity.DANGER -> colors.danger to colors.onDangerBanner
    AlertSeverity.NEUTRAL -> colors.textPrimary to colors.bg
    AlertSeverity.OK -> colors.ok to colors.onOk
    AlertSeverity.SAVER -> colors.warnTint to colors.warnText
}

@ThemePreviews
@Composable
private fun AlertBannerPreview() {
    RideCompPreview {
        Column {
            AlertBanner(
                title = "Waktunya minum",
                message = "20 menit bergerak sejak terakhir",
                iconRes = R.drawable.ic_bottle,
                actions = listOf(BannerAction("Sudah") {}, BannerAction("Tunda 5 mnt") {}),
            )
            AlertBanner(
                message = "HR Strap terputus · menyambung ulang…",
                severity = AlertSeverity.WARN,
                iconRes = R.drawable.ic_bluetooth_off,
            )
            AlertBanner(
                title = "Keluar jalur",
                message = "74 m dari rute",
                severity = AlertSeverity.DANGER,
                iconRes = R.drawable.ic_warning,
            )
            AlertBanner(message = "Mode hemat aktif · layar redup", severity = AlertSeverity.SAVER)
        }
    }
}
