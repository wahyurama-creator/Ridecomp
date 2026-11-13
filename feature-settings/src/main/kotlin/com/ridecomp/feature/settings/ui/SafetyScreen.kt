package com.ridecomp.feature.settings.ui

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.usecase.SafetyError
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.SafetyEffect
import com.ridecomp.feature.settings.viewmodel.SafetyMessage
import com.ridecomp.feature.settings.viewmodel.SafetyUiEvent
import com.ridecomp.feature.settings.viewmodel.SafetyUiState
import com.ridecomp.feature.settings.viewmodel.SafetyViewModel
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ListRow
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

/** Safety screen entry point (T2.14): permission and contact picker launchers live here (UI glue only). */
@Composable
fun SafetyRoute(modifier: Modifier = Modifier, viewModel: SafetyViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onEvent(SafetyUiEvent.SmsPermissionResult)
    }
    // ACTION_PICK on phone numbers: the system picker returns one row, no READ_CONTACTS (SEC-SMS-05).
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { viewModel.onEvent(SafetyUiEvent.Picked(it.toString())) }
    }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            SafetyEffect.RequestSmsPermission -> permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }
    SafetyScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onPickContact = {
            pickLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        },
        modifier = modifier,
    )
}

/** Stateless Safety screen (02-DESIGN §3.10, 12-FEATURE-SPECS §5.1). */
@Composable
fun SafetyScreen(
    state: SafetyUiState,
    onEvent: (SafetyUiEvent) -> Unit,
    onPickContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.safety_title),
        banner = { state.message?.let { SafetyBanner(it, onEvent) } },
    ) {
        if (!state.isLoaded) return@ScreenScaffold
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = RideCompDimens.spacingL),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            Text(
                text = stringResource(R.string.safety_disclaimer),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
            )
            SwitchRow(
                label = stringResource(R.string.safety_enabled),
                description = stringResource(R.string.safety_enabled_description),
                isChecked = state.isEnabled,
                onCheckedChange = { onEvent(SafetyUiEvent.Enable(it)) },
            )
            OutlinedTextField(
                value = state.riderName,
                onValueChange = { onEvent(SafetyUiEvent.RiderName(it)) },
                label = { Text(stringResource(R.string.safety_rider_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
            )
            Contacts(state, onEvent, onPickContact)
            BigButton(
                text = stringResource(R.string.safety_send_test),
                onClick = { onEvent(SafetyUiEvent.SendTest) },
                style = BigButtonStyle.SECONDARY,
                isEnabled = state.contacts.isNotEmpty() && !state.isSendingTest,
                modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
            )
        }
    }
}

@Composable
private fun Contacts(state: SafetyUiState, onEvent: (SafetyUiEvent) -> Unit, onPickContact: () -> Unit) {
    SectionHeader(stringResource(R.string.safety_contacts))
    state.contacts.forEach { contact -> ContactRow(contact, onRemove = { onEvent(SafetyUiEvent.Remove(contact.id)) }) }
    if (!state.canAddContact) return
    BigButton(
        text = stringResource(R.string.safety_pick_contact),
        onClick = onPickContact,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
    OutlinedTextField(
        value = state.newName,
        onValueChange = { onEvent(SafetyUiEvent.NewName(it)) },
        label = { Text(stringResource(R.string.safety_contact_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
    OutlinedTextField(
        value = state.newPhone,
        onValueChange = { onEvent(SafetyUiEvent.NewPhone(it)) },
        label = { Text(stringResource(R.string.safety_contact_phone)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
    BigButton(
        text = stringResource(R.string.safety_add_contact),
        onClick = { onEvent(SafetyUiEvent.AddTyped) },
        style = BigButtonStyle.SECONDARY,
        isEnabled = state.newPhone.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
}

@Composable
private fun ContactRow(contact: EmergencyContact, onRemove: () -> Unit) {
    ListRow(
        title = contact.displayName,
        subtitle = contact.phone,
        trailing = { TextButton(onClick = onRemove) { Text(stringResource(R.string.safety_remove)) } },
    )
}

@Composable
private fun SafetyBanner(message: SafetyMessage, onEvent: (SafetyUiEvent) -> Unit) {
    val text = when (message) {
        is SafetyMessage.TestSent -> pluralStringResource(R.plurals.safety_test_sent, message.count, message.count)
        is SafetyMessage.Error -> stringResource(message.error.messageRes())
    }
    AlertBanner(
        message = text,
        severity = if (message is SafetyMessage.Error) AlertSeverity.WARN else AlertSeverity.INFO,
        actions = listOf(
            BannerAction(stringResource(R.string.safety_ok)) {
                onEvent(SafetyUiEvent.MessageShown)
            },
        ),
    )
}

@StringRes
private fun SafetyError.messageRes(): Int = when (this) {
    SafetyError.NO_CONTACTS -> R.string.safety_error_no_contacts
    SafetyError.NO_SMS_PERMISSION -> R.string.safety_error_permission
    SafetyError.INVALID_PHONE -> R.string.safety_error_phone
    SafetyError.TOO_MANY_CONTACTS -> R.string.safety_error_too_many
    SafetyError.TOO_SOON -> R.string.safety_error_too_soon
    SafetyError.SEND_FAILED -> R.string.safety_error_send_failed
}

@ThemePreviews
@Composable
private fun SafetyScreenPreview() {
    RideCompPreview {
        SafetyScreen(
            state = SafetyUiState(
                isEnabled = true,
                riderName = "Rama",
                contacts = persistentListOf(EmergencyContact(1, "Ibu", "+6281200000000")),
                isLoaded = true,
            ),
            onEvent = {},
            onPickContact = {},
        )
    }
}
