package com.ridecomp.feature.settings.ui

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.usecase.SafetyError
import com.ridecomp.core.util.PhoneMask
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.SafetyEffect
import com.ridecomp.feature.settings.viewmodel.SafetyMessage
import com.ridecomp.feature.settings.viewmodel.SafetyUiEvent
import com.ridecomp.feature.settings.viewmodel.SafetyUiState
import com.ridecomp.feature.settings.viewmodel.SafetyViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.IconSquareButton
import com.ridecomp.ui.component.ListCard
import com.ridecomp.ui.component.ListCardRow
import com.ridecomp.ui.component.RideCompTextField
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.WindowWidthClass
import com.ridecomp.ui.udf.ObserveEffects
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

/** Safety screen entry point (T2.14): permission and contact picker launchers live here (UI glue only). */
@Composable
fun SafetyRoute(modifier: Modifier = Modifier, onBack: () -> Unit = {}, viewModel: SafetyViewModel = koinViewModel()) {
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
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless Safety screen (02-DESIGN §3.10, 12-FEATURE-SPECS §5.1, 13-UI-SCREENS #19). */
@Composable
fun SafetyScreen(
    state: SafetyUiState,
    onEvent: (SafetyUiEvent) -> Unit,
    onPickContact: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.safety_title),
        onBack = onBack,
        backLabel = stringResource(R.string.onboarding_back_label),
        banner = { state.message?.let { SafetyBanner(it, onEvent) } },
    ) {
        if (!state.isLoaded) return@ScreenScaffold
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) {
            ListCard {
                SwitchRow(
                    label = stringResource(R.string.safety_enabled),
                    description = stringResource(R.string.safety_enabled_description),
                    isChecked = state.isEnabled,
                    onCheckedChange = { onEvent(SafetyUiEvent.Enable(it)) },
                )
            }
            RideCompTextField(
                label = stringResource(R.string.safety_rider_name),
                value = state.riderName,
                onValueChange = { onEvent(SafetyUiEvent.RiderName(it)) },
            )
            Contacts(state, onEvent, onPickContact)
            SmsExample(state.riderName)
            Text(
                text = stringResource(R.string.safety_disclaimer),
                style = RideCompTheme.typography.caption,
                color = RideCompTheme.colors.textSecondary,
            )
            BigButton(
                text = stringResource(R.string.safety_send_test),
                onClick = { onEvent(SafetyUiEvent.SendTest) },
                style = BigButtonStyle.OUTLINE,
                size = BigButtonSize.TERTIARY,
                isEnabled = state.contacts.isNotEmpty() && !state.isSendingTest,
            )
        }
    }
}

@Composable
private fun Contacts(state: SafetyUiState, onEvent: (SafetyUiEvent) -> Unit, onPickContact: () -> Unit) {
    SectionHeader(stringResource(R.string.safety_contacts), horizontalPadding = RideCompDimens.none)
    ListCard {
        state.contacts.forEach { contact ->
            ContactRow(contact, onRemove = { onEvent(SafetyUiEvent.Remove(contact.id)) })
        }
        if (state.canAddContact) {
            ListCardRow(
                title = stringResource(R.string.safety_pick_contact),
                onClick = onPickContact,
                hasDivider = false,
                hasChevron = false,
            )
        }
    }
    if (!state.canAddContact) return
    // Typing a number is the fallback when the contact is not in the phone book.
    val name: @Composable (Modifier) -> Unit = { fieldModifier ->
        RideCompTextField(
            label = stringResource(R.string.safety_contact_name),
            value = state.newName,
            onValueChange = { onEvent(SafetyUiEvent.NewName(it)) },
            modifier = fieldModifier,
        )
    }
    val phone: @Composable (Modifier) -> Unit = { fieldModifier ->
        RideCompTextField(
            label = stringResource(R.string.safety_contact_phone),
            value = state.newPhone,
            onValueChange = { onEvent(SafetyUiEvent.NewPhone(it)) },
            keyboardType = KeyboardType.Phone,
            modifier = fieldModifier,
        )
    }
    when (RideCompTheme.widthClass) {
        // Side by side the fields are too narrow below 360 dp (audit T13.1 #13): one under the other.
        WindowWidthClass.COMPACT -> Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
            name(Modifier.fillMaxWidth())
            phone(Modifier.fillMaxWidth())
        }
        WindowWidthClass.REGULAR -> Row(
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
            verticalAlignment = Alignment.Bottom,
        ) {
            name(Modifier.weight(1f))
            phone(Modifier.weight(1f))
        }
    }
    BigButton(
        text = stringResource(R.string.safety_add_contact),
        onClick = { onEvent(SafetyUiEvent.AddTyped) },
        style = BigButtonStyle.SECONDARY,
        size = BigButtonSize.TERTIARY,
        isEnabled = state.newPhone.isNotBlank(),
    )
}

/** Initial avatar, name and the masked number (never the full number in the list, 13-UI-SCREENS #19). */
@Composable
private fun ContactRow(contact: EmergencyContact, onRemove: () -> Unit) {
    val colors = RideCompTheme.colors
    val masked = PhoneMask.mask(contact.phone)
    // A contact saved without a name carries the number as its name: never show it in full.
    val hasName = contact.displayName.filter(Char::isDigit) != contact.phone.filter(Char::isDigit)
    val name = if (hasName) contact.displayName else masked
    ListCardRow(
        title = name,
        subtitle = if (hasName) masked else null,
        leading = {
            Box(
                modifier = Modifier.size(RideCompDimens.avatarSize).background(colors.divider, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (hasName) name.take(1).uppercase() else PHONE_AVATAR,
                    style = RideCompTheme.typography.cardTitle,
                    color = colors.textPrimary,
                )
            }
        },
        trailing = {
            IconSquareButton(
                iconRes = UiR.drawable.ic_close,
                contentDescription = stringResource(R.string.safety_remove_contact, name),
                onClick = onRemove,
            )
        },
    )
}

/** Avatar of a contact without a name. */
private const val PHONE_AVATAR = "#"

/**
 * Sample of the alert SMS. The real text is `sms_incident` in :platform (built by the recording
 * service); keep this sample in step with it.
 */
@Composable
private fun SmsExample(riderName: String) {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(RideCompDimens.borderThin, colors.indicatorInactive, shape)
            .padding(horizontal = RideCompDimens.spacingSm, vertical = RideCompDimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
    ) {
        Text(
            text = stringResource(R.string.safety_sms_example_title).uppercase(),
            style = RideCompTheme.typography.dataLabel,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(
                R.string.safety_sms_example,
                riderName.ifBlank { stringResource(R.string.safety_sms_rider_unknown) },
            ),
            style = RideCompTheme.typography.caption,
            color = colors.textTertiary,
        )
    }
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
