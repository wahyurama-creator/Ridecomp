package com.ridecomp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompColors
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.util.thenIf
import kotlinx.collections.immutable.ImmutableList

/** Look of a [RideCompCard] (13-UI-SCREENS §2). */
enum class CardTone {
    /** Plain `surface` card. */
    NORMAL,

    /** WarningCard: `warnTint` background, 1 dp `warn` border. */
    WARNING,

    /** Highlight (new PR): `surface` with a 2 dp `accent` border. */
    ACCENT,

    /** Positive info (map available offline): `okTint`, 1 dp `ok` border. */
    OK,
}

/** Rounded card container used by every non-ride screen. */
@Composable
fun RideCompCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.NORMAL,
    onClick: (() -> Unit)? = null,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusCard)
    val (background, border) = tone.look(colors)
    val cardPadding = RideCompTheme.dimens.cardPaddingHorizontal
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .thenIf(onClick != null) { clickable(role = Role.Button) { onClick?.invoke() } }
            .thenIf(contentPadding) {
                padding(horizontal = cardPadding, vertical = RideCompDimens.cardPaddingVertical)
            },
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.cardGap),
        content = content,
    )
}

private fun CardTone.look(colors: RideCompColors): Pair<Color, BorderStroke?> = when (this) {
    CardTone.NORMAL -> colors.surface to null
    CardTone.WARNING -> colors.warnTint to BorderStroke(RideCompDimens.borderThin, colors.warn)
    CardTone.ACCENT -> colors.surface to BorderStroke(RideCompDimens.borderSelected, colors.accent)
    CardTone.OK -> colors.okTint to BorderStroke(RideCompDimens.borderThin, colors.ok)
}

/**
 * WarningCard (13-UI-SCREENS §2): bold `warn` title, explanation, optional link-style action or a
 * chevron when the whole card opens a screen.
 */
@Composable
fun WarningCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    RideCompCard(modifier = modifier, tone = CardTone.WARNING, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
            ) {
                Text(text = title, style = RideCompTheme.typography.cardTitle, color = RideCompTheme.colors.warnText)
                Text(text = message, style = RideCompTheme.typography.body, color = RideCompTheme.colors.textTertiary)
            }
            if (onClick != null) ChevronIcon()
        }
        if (actionLabel != null && onAction != null) {
            BigButton(text = actionLabel, onClick = onAction, style = BigButtonStyle.TEXT)
        }
    }
}

/** Tone of a value on the right of a [ListCardRow] ("Aktif · 2 kontak" is green). */
enum class ValueTone { NORMAL, OK, WARN, DANGER }

/** ListCard (13-UI-SCREENS §2): rows separated by dividers inside one `surface` card. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusCard)
    Column(
        modifier = modifier.fillMaxWidth().clip(shape).background(RideCompTheme.colors.surface),
        content = content,
    )
}

/** One row of a [ListCard]: title (+ subtitle) on the left, value or custom trailing content on the right. */
@Composable
fun ListCardRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueTone: ValueTone = ValueTone.NORMAL,
    onClick: (() -> Unit)? = null,
    hasDivider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    /** False for pick lists, where the row selects instead of opening a screen. */
    hasChevron: Boolean = true,
    /** Danger-colored title (delete). */
    isDestructive: Boolean = false,
) {
    val colors = RideCompTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RideCompDimens.touchTargetMin)
                .thenIf(onClick != null) { clickable(role = Role.Button) { onClick?.invoke() } }
                .padding(horizontal = RideCompDimens.cardPaddingHorizontal, vertical = RideCompDimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = RideCompTheme.typography.bodyStrong,
                    color = if (isDestructive) colors.danger else colors.textPrimary,
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = RideCompTheme.typography.caption, color = colors.textSecondary)
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    style = RideCompTheme.typography.caption,
                    color = when (valueTone) {
                        ValueTone.NORMAL -> colors.textSecondary
                        ValueTone.OK -> colors.ok
                        ValueTone.WARN -> colors.warnText
                        ValueTone.DANGER -> colors.danger
                    },
                )
            }
            trailing?.invoke()
            if (onClick != null && hasChevron) ChevronIcon()
        }
        if (hasDivider) HorizontalDivider(thickness = RideCompDimens.dividerThickness, color = colors.divider)
    }
}

/** Small statistic card (pause screen, summary): uppercase label, big value with its unit. */
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null) {
    val colors = RideCompTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RideCompDimens.cornerRadiusCard))
            .background(colors.surface)
            .padding(horizontal = RideCompDimens.cardPaddingHorizontal, vertical = RideCompDimens.cardPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
    ) {
        Text(text = label.uppercase(), style = RideCompTheme.typography.dataLabel, color = colors.textSecondary)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
        ) {
            Text(text = value, style = RideCompTheme.typography.statValue, color = colors.textPrimary)
            if (unit != null) Text(text = unit, style = RideCompTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

/** One cell of a [StatGrid]: label, big value, small caption ("max 54.3 km/h"). */
@Immutable
data class StatItem(
    val label: String,
    val value: String,
    val caption: String? = null,
    val isLabelWarn: Boolean = false,
    /** Drawn small after the value ("24.6 km/h"), so a narrow cell keeps the number on one line. */
    val unit: String? = null,
)

/** Statistics grid with 1 dp dividers inside a rounded `surface` card (13-UI-SCREENS #10). */
@Composable
fun StatGrid(items: ImmutableList<StatItem>, modifier: Modifier = Modifier, columns: Int = STAT_GRID_COLUMNS) {
    val colors = RideCompTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RideCompDimens.cornerRadiusCard))
            .background(colors.divider),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.dividerThickness),
    ) {
        items.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(RideCompDimens.dividerThickness),
            ) {
                row.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(colors.surface)
                            .padding(horizontal = RideCompDimens.spacingSm, vertical = RideCompDimens.cardGap)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Text(
                            text = item.label.uppercase(),
                            style = RideCompTheme.typography.navLabel,
                            color = if (item.isLabelWarn) colors.warnText else colors.textSecondary,
                        )
                        Text(
                            text = buildAnnotatedString {
                                append(item.value)
                                item.unit?.let { unit ->
                                    withStyle(
                                        SpanStyle(fontSize = RideCompDimens.captionSize, color = colors.textSecondary),
                                    ) {
                                        append(" ")
                                        append(unit)
                                    }
                                }
                            },
                            style = RideCompTheme.typography.statValue,
                            color = colors.textPrimary,
                        )
                        item.caption?.let {
                            Text(text = it, style = RideCompTheme.typography.navLabel, color = colors.textSecondary)
                        }
                    }
                }
                repeat(columns - row.size) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(colors.surface))
                }
            }
        }
    }
}

private const val STAT_GRID_COLUMNS = 3

/** Decorative chevron of a clickable row or card (its text is the label). */
@Composable
private fun ChevronIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = RideCompTheme.colors.textSecondary,
        modifier = Modifier.size(RideCompDimens.iconSize),
    )
}

@ThemePreviews
@Composable
private fun CardsPreview() {
    RideCompPreview {
        Column(
            modifier = Modifier.padding(RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) {
            WarningCard(
                title = "HP saya mematikan app",
                message = "Panduan untuk Xiaomi: izinkan Autostart.",
                onClick = {},
            )
            ListCard {
                ListCardRow(title = "Profil pengendara", subtitle = "70 kg · 700×25c", onClick = {})
                ListCardRow(title = "Keselamatan", value = "Aktif · 2 kontak", valueTone = ValueTone.OK, onClick = {})
                ListCardRow(title = "Peta offline", value = "1 area", hasDivider = false, onClick = {})
            }
        }
    }
}
