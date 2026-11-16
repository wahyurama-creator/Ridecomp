package com.ridecomp.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompColors
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Visual variants of [BigButton] (13-UI-SCREENS §1.4, §4). */
enum class BigButtonStyle {
    /** Main action (MULAI RIDE, PAUSE, LANJUTKAN): accent fill, dark text. */
    PRIMARY,

    /** Secondary action on a card: surface fill. */
    SECONDARY,

    /** Outlined in the text color (SELESAI & SIMPAN, LAP, Export FIT). */
    OUTLINE,

    /** Destructive fill (confirm delete). */
    DANGER,

    /** Destructive outline (SOS · tahan 3 detik). */
    DANGER_OUTLINE,

    /** Text only, destructive (Buang ride, Hapus aktivitas). */
    TEXT_DANGER,

    /** Text only, accent (Lewati…, links). */
    TEXT,

    /** Warning fill (recovery: Lanjutkan). */
    WARN,

    /** Warning outline (recovery: Simpan). */
    WARN_OUTLINE,

    /** Light fill on a dark danger screen (SAYA BAIK-BAIK SAJA). */
    INVERSE,

    /** White fill, black text: always light on the deep-red incident screen, in both themes. */
    INVERSE_LIGHT,

    /** White outline: on the deep-red incident screen, in both themes. */
    OUTLINE_LIGHT,
}

/** Heights from 13-UI-SCREENS §1.4; every size stays ≥ 56 dp (UI-07). */
enum class BigButtonSize(val height: Dp) {
    HERO(RideCompDimens.buttonHeightHero),
    PRIMARY(RideCompDimens.buttonHeightPrimary),
    SECONDARY(RideCompDimens.buttonHeightSecondary),
    TERTIARY(RideCompDimens.buttonHeightTertiary),

    /** Small actions off the ride screen (share page). */
    COMPACT(RideCompDimens.buttonHeightCompact),
    INCIDENT(RideCompDimens.buttonHeightIncident),
}

/** Large, glove-friendly button (UI-07, 02-DESIGN §4.3). Text doubles as the accessible label. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BigButtonStyle = BigButtonStyle.PRIMARY,
    isEnabled: Boolean = true,
    size: BigButtonSize = style.defaultSize(),
    @DrawableRes iconRes: Int? = null,
) {
    val colors = RideCompTheme.colors
    val look = style.look(colors)
    val isLarge = size == BigButtonSize.HERO || size == BigButtonSize.INCIDENT
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier.fillMaxWidth().heightIn(min = size.height),
        shape = RoundedCornerShape(if (isLarge) RideCompDimens.cornerRadiusHero else RideCompDimens.cornerRadiusButton),
        border = look.border?.let { BorderStroke(RideCompDimens.borderSelected, it) },
        // Compact buttons sit side by side: less padding so the label stays on one line.
        contentPadding = if (size == BigButtonSize.COMPACT) {
            PaddingValues(horizontal = RideCompDimens.spacingSm)
        } else {
            ButtonDefaults.ContentPadding
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = look.container,
            contentColor = look.content,
            disabledContainerColor = colors.divider,
            disabledContentColor = colors.textSecondary,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconRes?.let {
                // Decorative: the button text is the label.
                Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(RideCompDimens.iconSize))
            }
            Text(
                text = text,
                style = when {
                    isLarge -> RideCompTheme.typography.buttonLarge
                    size == BigButtonSize.TERTIARY || size == BigButtonSize.COMPACT ->
                        RideCompTheme.typography.buttonSmall
                    else -> RideCompTheme.typography.button
                },
                color = Color.Unspecified,
                textAlign = TextAlign.Center,
                maxLines = if (size == BigButtonSize.COMPACT) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Square icon button (remove route, delete contact): 56 dp (UI-07; the design's 44 dp is too small for
 * gloves, D-54). [contentDescription] is required: the icon is the only label (UI-08).
 */
@Composable
fun IconSquareButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = RideCompTheme.colors.textSecondary,
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(RideCompDimens.touchTargetMin),
        shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = RideCompTheme.colors.surfaceRaised, contentColor = tint),
    ) {
        Icon(painterResource(iconRes), contentDescription = contentDescription, tint = tint)
    }
}

private data class ButtonLook(val container: Color, val content: Color, val border: Color? = null)

private fun BigButtonStyle.look(colors: RideCompColors): ButtonLook = when (this) {
    BigButtonStyle.PRIMARY -> ButtonLook(colors.accent, colors.onAccent)
    BigButtonStyle.SECONDARY -> ButtonLook(colors.surface, colors.textPrimary)
    BigButtonStyle.OUTLINE -> ButtonLook(Color.Transparent, colors.textPrimary, colors.textPrimary)
    BigButtonStyle.DANGER -> ButtonLook(colors.danger, colors.onDanger)
    BigButtonStyle.DANGER_OUTLINE -> ButtonLook(Color.Transparent, colors.danger, colors.danger)
    BigButtonStyle.TEXT_DANGER -> ButtonLook(Color.Transparent, colors.danger)
    BigButtonStyle.TEXT -> ButtonLook(Color.Transparent, colors.accent)
    BigButtonStyle.WARN -> ButtonLook(colors.warn, colors.onWarn)
    BigButtonStyle.WARN_OUTLINE -> ButtonLook(Color.Transparent, colors.warnText, colors.warn)
    BigButtonStyle.INVERSE -> ButtonLook(colors.textPrimary, colors.bg)
    BigButtonStyle.INVERSE_LIGHT -> ButtonLook(colors.onDanger, colors.dangerDeep)
    BigButtonStyle.OUTLINE_LIGHT -> ButtonLook(Color.Transparent, colors.onDanger, colors.onDanger)
}

private fun BigButtonStyle.defaultSize(): BigButtonSize = when (this) {
    BigButtonStyle.PRIMARY -> BigButtonSize.PRIMARY
    BigButtonStyle.OUTLINE, BigButtonStyle.SECONDARY, BigButtonStyle.DANGER -> BigButtonSize.SECONDARY
    else -> BigButtonSize.TERTIARY
}

@ThemePreviews
@Composable
private fun BigButtonPreview() {
    RideCompPreview {
        Column(
            modifier = Modifier.background(RideCompTheme.colors.bg).padding(RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        ) {
            BigButton(text = "MULAI RIDE", onClick = {}, size = BigButtonSize.HERO, iconRes = R.drawable.ic_play)
            BigButton(text = "SELESAI & SIMPAN", onClick = {}, style = BigButtonStyle.OUTLINE)
            BigButton(text = "SOS · tahan 3 detik", onClick = {}, style = BigButtonStyle.DANGER_OUTLINE)
            BigButton(text = "Buang ride", onClick = {}, style = BigButtonStyle.TEXT_DANGER)
            Box { IconSquareButton(R.drawable.ic_close, contentDescription = "Hapus", onClick = {}) }
        }
    }
}
