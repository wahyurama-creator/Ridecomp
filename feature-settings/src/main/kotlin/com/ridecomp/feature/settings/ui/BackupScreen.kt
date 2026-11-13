package com.ridecomp.feature.settings.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.engine.backup.BackupError
import com.ridecomp.core.usecase.BackupFileNames
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.BackupMessage
import com.ridecomp.feature.settings.viewmodel.BackupUiEvent
import com.ridecomp.feature.settings.viewmodel.BackupUiState
import com.ridecomp.feature.settings.viewmodel.BackupViewModel
import com.ridecomp.feature.settings.viewmodel.RestoreStep
import com.ridecomp.ui.component.AlertBanner
import com.ridecomp.ui.component.AlertSeverity
import com.ridecomp.ui.component.BannerAction
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.component.SectionHeader
import com.ridecomp.ui.component.SwitchRow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import java.text.DateFormat
import java.util.Date
import org.koin.androidx.compose.koinViewModel

/** MIME type for the SAF pickers: the archive is a (possibly encrypted) ZIP of our own. */
private const val BACKUP_MIME = "application/octet-stream"

/** Backup & restore entry point (T2.17): SAF launchers only, the work happens in the ViewModel. */
@Composable
fun BackupRoute(modifier: Modifier = Modifier, viewModel: BackupViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        uri?.let { viewModel.onEvent(BackupUiEvent.CreateTo(it.toString())) }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onEvent(BackupUiEvent.OpenFrom(it.toString())) }
    }
    BackupScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onCreate = { createLauncher.launch(viewModel.suggestedFileName) },
        onOpen = { openLauncher.launch(arrayOf(BACKUP_MIME, ZIP_MIME)) },
        modifier = modifier,
    )
}

private const val ZIP_MIME = "application/zip"

@Composable
fun BackupScreen(
    state: BackupUiState,
    onEvent: (BackupUiEvent) -> Unit,
    onCreate: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = stringResource(R.string.backup_title),
        banner = { state.message?.let { BackupBanner(it, onEvent) } },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = RideCompDimens.spacingL),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            when (val restore = state.restore) {
                null -> {
                    CreateSection(state, onEvent, onCreate)
                    SectionHeader(stringResource(R.string.backup_restore_header))
                    Hint(stringResource(R.string.backup_restore_hint))
                    BigButton(
                        text = stringResource(R.string.backup_restore_choose),
                        onClick = onOpen,
                        style = BigButtonStyle.SECONDARY,
                        isEnabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
                    )
                }
                is RestoreStep.NeedsPassword -> UnlockSection(state, onEvent)
                is RestoreStep.Summary -> SummarySection(restore, state.isWorking, onEvent)
                RestoreStep.Done -> DoneSection(onEvent)
            }
            if (state.isWorking) Hint(stringResource(R.string.backup_working))
        }
    }
}

@Composable
private fun CreateSection(state: BackupUiState, onEvent: (BackupUiEvent) -> Unit, onCreate: () -> Unit) {
    val last = state.lastBackupAtMs?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
    Hint(last?.let { stringResource(R.string.backup_last, it) } ?: stringResource(R.string.backup_never))
    SwitchRow(
        label = stringResource(R.string.backup_password_enabled),
        description = stringResource(R.string.backup_password_warning),
        isChecked = state.isPasswordEnabled,
        onCheckedChange = { onEvent(BackupUiEvent.PasswordEnabled(it)) },
    )
    if (state.isPasswordEnabled) {
        PasswordField(state.password, R.string.backup_password) { onEvent(BackupUiEvent.Password(it)) }
        PasswordField(state.passwordConfirm, R.string.backup_password_confirm) {
            onEvent(BackupUiEvent.PasswordConfirm(it))
        }
        Hint(
            pluralStringResource(
                R.plurals.backup_password_rule,
                BackupFileNames.MIN_PASSWORD_LENGTH,
                BackupFileNames.MIN_PASSWORD_LENGTH,
            ),
        )
    }
    BigButton(
        text = stringResource(R.string.backup_create),
        onClick = onCreate,
        isEnabled = state.canCreate,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
}

@Composable
private fun UnlockSection(state: BackupUiState, onEvent: (BackupUiEvent) -> Unit) {
    Hint(stringResource(R.string.backup_encrypted_hint))
    PasswordField(state.restorePassword, R.string.backup_password) { onEvent(BackupUiEvent.RestorePassword(it)) }
    BigButton(
        text = stringResource(R.string.backup_unlock),
        onClick = { onEvent(BackupUiEvent.Unlock) },
        isEnabled = state.restorePassword.isNotEmpty() && !state.isWorking,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
    CancelButton(onEvent)
}

@Composable
private fun SummarySection(step: RestoreStep.Summary, isWorking: Boolean, onEvent: (BackupUiEvent) -> Unit) {
    val manifest = step.summary.manifest
    Hint(
        pluralStringResource(
            R.plurals.backup_summary,
            manifest.activityCount,
            DateFormat.getDateTimeInstance().format(Date(manifest.createdAtMs)),
            manifest.activityCount,
            manifest.appVersion,
        ),
    )
    Text(
        text = stringResource(
            if (step.isFinalConfirm) R.string.backup_restore_final else R.string.backup_restore_warning,
        ),
        style = RideCompTheme.typography.body,
        color = RideCompTheme.colors.danger,
        modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
    )
    BigButton(
        text = stringResource(
            if (step.isFinalConfirm) R.string.backup_restore_confirm_final else R.string.backup_restore_confirm,
        ),
        onClick = { onEvent(BackupUiEvent.ConfirmRestore) },
        style = BigButtonStyle.DANGER,
        isEnabled = !isWorking,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
    CancelButton(onEvent)
}

@Composable
private fun DoneSection(onEvent: (BackupUiEvent) -> Unit) {
    Hint(stringResource(R.string.backup_restored))
    BigButton(
        text = stringResource(R.string.backup_restart),
        onClick = { onEvent(BackupUiEvent.Restart) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
}

@Composable
private fun CancelButton(onEvent: (BackupUiEvent) -> Unit) {
    BigButton(
        text = stringResource(R.string.backup_cancel),
        onClick = { onEvent(BackupUiEvent.CancelRestore) },
        style = BigButtonStyle.SECONDARY,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
}

@Composable
private fun PasswordField(value: String, @StringRes label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(horizontal = RideCompDimens.spacingM),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = RideCompTheme.typography.body,
        color = RideCompTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = RideCompDimens.spacingM),
    )
}

@Composable
private fun BackupBanner(message: BackupMessage, onEvent: (BackupUiEvent) -> Unit) {
    val text = when (message) {
        is BackupMessage.Created ->
            pluralStringResource(R.plurals.backup_created, message.activityCount, message.activityCount)
        is BackupMessage.Failed -> stringResource(message.error.messageRes())
    }
    AlertBanner(
        message = text,
        severity = if (message is BackupMessage.Failed) AlertSeverity.WARN else AlertSeverity.INFO,
        actions = listOf(BannerAction(stringResource(R.string.safety_ok)) { onEvent(BackupUiEvent.MessageShown) }),
    )
}

@StringRes
private fun BackupError.messageRes(): Int = when (this) {
    BackupError.WRONG_PASSWORD_OR_DAMAGED -> R.string.backup_error_password
    BackupError.NOT_A_BACKUP, BackupError.UNSAFE_ENTRY, BackupError.MISSING_ENTRY,
    BackupError.CHECKSUM_MISMATCH, BackupError.INVALID_DATABASE,
    -> R.string.backup_error_invalid
    BackupError.TOO_LARGE -> R.string.backup_error_too_large
    BackupError.NEWER_SCHEMA -> R.string.backup_error_newer
    BackupError.RIDE_IN_PROGRESS -> R.string.backup_error_ride
    BackupError.WRITE_FAILED -> R.string.backup_error_write
}

@ThemePreviews
@Composable
private fun BackupScreenPreview() {
    RideCompPreview {
        BackupScreen(
            state = BackupUiState(lastBackupAtMs = 1_700_000_000_000L),
            onEvent = {},
            onCreate = {},
            onOpen = {},
        )
    }
}
