package com.ridecomp.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Visual variants of [BigButton]. */
enum class BigButtonStyle {
    /** Main action (e.g. START RIDE): accent color, ≥ 72 dp. */
    PRIMARY,

    /** Secondary action: surface color, ≥ 56 dp. */
    SECONDARY,

    /** Destructive action (e.g. DISCARD): danger color, ≥ 56 dp. */
    DANGER,
}

/** Large, glove-friendly button (UI-07, 02-DESIGN §4.3). Text doubles as the accessible label. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BigButtonStyle = BigButtonStyle.PRIMARY,
    isEnabled: Boolean = true,
) {
    val colors = RideCompTheme.colors
    val (container, content) = when (style) {
        BigButtonStyle.PRIMARY -> colors.accent to colors.onAccent
        BigButtonStyle.SECONDARY -> colors.surface to colors.textPrimary
        BigButtonStyle.DANGER -> colors.danger to colors.onDanger
    }
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier.fillMaxWidth().heightIn(min = style.minHeight()),
        shape = RoundedCornerShape(RideCompDimens.cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.divider,
            disabledContentColor = colors.textSecondary,
        ),
    ) {
        Text(
            text = text,
            style = if (style == BigButtonStyle.PRIMARY) {
                RideCompTheme.typography.buttonLarge
            } else {
                RideCompTheme.typography.button
            },
            color = Color.Unspecified,
            textAlign = TextAlign.Center,
        )
    }
}

private fun BigButtonStyle.minHeight(): Dp = when (this) {
    BigButtonStyle.PRIMARY -> RideCompDimens.touchTargetPrimary
    BigButtonStyle.SECONDARY, BigButtonStyle.DANGER -> RideCompDimens.touchTargetMin
}

@ThemePreviews
@Composable
private fun BigButtonPreview() {
    RideCompPreview {
        BigButton(text = "START RIDE", onClick = {})
    }
}

@ThemePreviews
@Composable
private fun BigButtonDangerPreview() {
    RideCompPreview {
        BigButton(text = "DISCARD", onClick = {}, style = BigButtonStyle.DANGER)
    }
}
