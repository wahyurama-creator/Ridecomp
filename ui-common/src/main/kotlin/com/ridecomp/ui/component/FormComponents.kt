package com.ridecomp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
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

/** Single choice from a few options (auto-pause delay, units, screen mode…), glove-friendly height. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    label: String,
    choices: ImmutableList<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM)) {
        Text(text = label, style = RideCompTheme.typography.body, color = RideCompTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice.value == selected,
                    onClick = { onSelect(choice.value) },
                    label = { Text(choice.label) },
                    modifier = Modifier.heightIn(min = RideCompDimens.touchTargetMin),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RideCompTheme.colors.accent,
                        selectedLabelColor = RideCompTheme.colors.onAccent,
                    ),
                )
            }
        }
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
            .padding(horizontal = RideCompDimens.spacingM)
            .semantics { role = Role.Switch },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = RideCompTheme.typography.body, color = RideCompTheme.colors.textPrimary)
            if (description != null) {
                Text(
                    text = description,
                    style = RideCompTheme.typography.dataLabel,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = RideCompTheme.colors.accent),
        )
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
