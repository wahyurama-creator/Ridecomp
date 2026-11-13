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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
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
) {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall)
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
    ) {
        Text(text = label.uppercase(), style = RideCompTheme.typography.dataLabel, color = colors.textSecondary)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        ) {
            choices.forEach { choice ->
                val isSelected = choice.value == selected
                Box(
                    modifier = Modifier
                        .heightIn(min = RideCompDimens.touchTargetMin)
                        .clip(shape)
                        .background(if (isSelected) colors.accentTint else colors.surface)
                        .border(
                            width = RideCompDimens.borderSelected,
                            color = if (isSelected) colors.accent else colors.surface,
                            shape = shape,
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(choice.value) }
                        .padding(horizontal = RideCompDimens.spacingM),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = choice.label,
                        style = RideCompTheme.typography.buttonSmall,
                        color = if (isSelected) colors.textPrimary else colors.textTertiary,
                    )
                }
            }
        }
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
