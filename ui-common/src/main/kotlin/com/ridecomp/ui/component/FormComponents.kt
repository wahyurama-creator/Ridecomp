package com.ridecomp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One option of [ChoiceChips]. */
@Immutable
data class Choice<T>(val value: T, val label: String)

/**
 * OptionChips (13-UI-SCREENS §2): single choice from a few options (activity profile, screen mode,
 * hand position…). Selected = 2 dp `accent` border on `accentTint`. Glove-friendly height (UI-07).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    label: String,
    choices: ImmutableList<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Zero inside a screen that already pads its content. */
    horizontalPadding: Dp = RideCompDimens.spacingM,
    /** Equal-width chips in rows of this many (13-UI-SCREENS #5: 5 profiles, 3 screen modes); null = wrap. */
    columns: Int? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        Text(
            text = label.uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = RideCompTheme.colors.textSecondary,
        )
        if (columns == null) {
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
            ) {
                choices.forEach { choice ->
                    OptionChip(choice, choice.value == selected, onSelect, Modifier, isCompact = false)
                }
            }
        } else {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
            ) {
                choices.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
                        row.forEach { choice ->
                            OptionChip(
                                choice,
                                choice.value == selected,
                                onSelect,
                                Modifier.weight(1f),
                                isCompact = true,
                            )
                        }
                        // Keep the last row's chips the same width as the others.
                        repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> OptionChip(
    choice: Choice<T>,
    isSelected: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    isCompact: Boolean,
) {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall)
    Box(
        modifier = modifier
            .heightIn(min = RideCompDimens.touchTargetMin)
            .clip(shape)
            .background(if (isSelected) colors.accentTint else colors.surface)
            .border(
                width = RideCompDimens.borderSelected,
                color = if (isSelected) colors.accent else colors.surface,
                shape = shape,
            )
            .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(choice.value) }
            .padding(horizontal = if (isCompact) RideCompDimens.spacingXs else RideCompDimens.spacingM),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = choice.label,
            style = if (isCompact) RideCompTheme.typography.chipCompact else RideCompTheme.typography.buttonSmall,
            color = if (isSelected) colors.textPrimary else colors.textTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Toggle (13-UI-SCREENS §1.4, §2): 56 × 34; on = `ok` track with the dark knob on the right. */
@Composable
fun RideCompToggle(isChecked: Boolean, modifier: Modifier = Modifier) {
    val colors = RideCompTheme.colors
    Box(
        modifier = modifier
            .size(width = RideCompDimens.toggleWidth, height = RideCompDimens.toggleHeight)
            .clip(CircleShape)
            .background(if (isChecked) colors.ok else colors.divider)
            .padding(RideCompDimens.toggleKnobInset),
        contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(RideCompDimens.toggleKnobSize)
                .background(if (isChecked) colors.bg else colors.textSecondary, CircleShape),
        )
    }
}

/** A labelled on/off row; the whole row toggles. */
@Composable
fun SwitchRow(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RideCompDimens.touchTargetMin)
            .toggleable(value = isChecked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = RideCompTheme.typography.bodyStrong, color = RideCompTheme.colors.textPrimary)
            if (description != null) {
                Text(
                    text = description,
                    style = RideCompTheme.typography.caption,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
        }
        RideCompToggle(isChecked = isChecked)
    }
}

/**
 * Text input in the design style (13-UI-SCREENS #10, #2): uppercase label above, `surface` box with a
 * `divider` border, large bold text. [error] turns the border red and is read by TalkBack.
 */
@Composable
fun RideCompTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isLarge: Boolean = false,
    /** Hides the characters (backup password). */
    isPassword: Boolean = false,
) {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall)
    val textStyle = (if (isLarge) RideCompTheme.typography.title else RideCompTheme.typography.cardTitle)
        .copy(color = colors.textPrimary)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs)) {
        Text(text = label.uppercase(), style = RideCompTheme.typography.dataLabel, color = colors.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (error != null) stateDescription = error
                },
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RideCompDimens.touchTargetMin)
                        .clip(shape)
                        .background(colors.surface)
                        .border(RideCompDimens.borderThin, if (error != null) colors.danger else colors.divider, shape)
                        .padding(horizontal = RideCompDimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) { inner() }
                    if (suffix != null) {
                        Text(text = suffix, style = RideCompTheme.typography.body, color = colors.textSecondary)
                    }
                }
            },
        )
        if (error != null) Text(text = error, style = RideCompTheme.typography.caption, color = colors.danger)
    }
}

/** Numeric input with an error message (profile form). Text stays text until saved. */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    error: String? = null,
    isDecimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = unit?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = RideCompTheme.colors.danger) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RideCompDimens.spacingM)
            .semantics { if (error != null) stateDescription = error },
    )
}

/** Label/value row for summaries ("Distance … 42.10 km"). */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingXs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = RideCompTheme.typography.body, color = RideCompTheme.colors.textSecondary)
        Text(text = value, style = RideCompTheme.typography.body, color = RideCompTheme.colors.textPrimary)
    }
}

/**
 * Confirmation dialog for destructive actions (discard ride, delete, restore): the user must confirm
 * explicitly (2-step, 02-DESIGN §3.5).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = RideCompDimens.touchTargetMin)) {
                Text(confirmLabel, color = RideCompTheme.colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = RideCompDimens.touchTargetMin)) {
                Text(dismissLabel)
            }
        },
        containerColor = RideCompTheme.colors.surface,
    )
}

/** Pick one value from a longer list (metric for a data field, …); the current value is highlighted. */
@Composable
fun <T> PickerDialog(
    title: String,
    choices: ImmutableList<Choice<T>>,
    selected: T?,
    dismissLabel: String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(choices, key = { it.label }) { choice ->
                    Text(
                        text = choice.label,
                        style = RideCompTheme.typography.body,
                        color = if (choice.value ==
                            selected
                        ) {
                            RideCompTheme.colors.accent
                        } else {
                            RideCompTheme.colors.textPrimary
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RideCompDimens.touchTargetMin)
                            .clickable(role = Role.Button) { onPick(choice.value) }
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = RideCompDimens.touchTargetMin)) {
                Text(dismissLabel)
            }
        },
        containerColor = RideCompTheme.colors.surface,
    )
}

@ThemePreviews
@Composable
private fun FormComponentsPreview() {
    RideCompPreview {
        Column {
            ChoiceChips(
                label = "Pause after",
                choices = persistentListOf(Choice(3, "3 s"), Choice(5, "5 s"), Choice(10, "10 s")),
                selected = 5,
                onSelect = {},
            )
            SwitchRow(label = "Auto pause", isChecked = true, onCheckedChange = {})
            NumberField(label = "Weight", value = "70", onValueChange = {}, unit = "kg")
            StatRow(label = "Distance", value = "42.10 km")
        }
    }
}
