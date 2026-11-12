package com.ridecomp.feature.settings.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.OnboardingEffect
import com.ridecomp.feature.settings.viewmodel.OnboardingStep
import com.ridecomp.feature.settings.viewmodel.OnboardingUiEvent
import com.ridecomp.feature.settings.viewmodel.OnboardingViewModel
import com.ridecomp.feature.settings.viewmodel.ProfileEffect
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import org.koin.androidx.compose.koinViewModel

/** URI scheme for the battery-optimisation exemption request. */
private const val PACKAGE_SCHEME = "package:"

/** Permissions asked on first launch (02-DESIGN §3.1, T1.1). */
@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel(),
) {
    val step by viewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.Done -> onDone()
        }
    }
    ObserveEffects(profileViewModel.effects) { effect ->
        when (effect) {
            ProfileEffect.Saved -> viewModel.onEvent(OnboardingUiEvent.Finished)
        }
    }
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh++
    }
    ScreenScaffold(modifier = modifier, title = stringResource(step.titleRes())) {
        when (step) {
            OnboardingStep.PROFILE -> ProfileForm(state = profileState, onEvent = profileViewModel::onEvent)
            OnboardingStep.LOCATION, OnboardingStep.NOTIFICATIONS, OnboardingStep.BATTERY -> PermissionStep(
                step = step,
                isGranted = remember(refresh, step) { context.isStepDone(step) },
                onRequest = {
                    when (step) {
                        OnboardingStep.LOCATION -> permissionLauncher.launch(LOCATION_PERMISSIONS)
                        OnboardingStep.NOTIFICATIONS -> permissionLauncher.launch(notificationPermissions())
                        OnboardingStep.BATTERY -> settingsLauncher.launch(context.batteryExemptionIntent())
                        OnboardingStep.PROFILE -> Unit
                    }
                },
                onNext = { viewModel.onEvent(OnboardingUiEvent.Next) },
                onBack = { viewModel.onEvent(OnboardingUiEvent.Back) },
            )
        }
    }
}

@Composable
private fun PermissionStep(
    step: OnboardingStep,
    isGranted: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(RideCompDimens.spacingM),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingL),
    ) {
        Text(
            text = stringResource(step.textRes()),
            style = RideCompTheme.typography.body,
            color = RideCompTheme.colors.textPrimary,
        )
        if (isGranted) {
            Text(
                text = stringResource(step.grantedRes()),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.ok,
            )
        } else {
            BigButton(text = stringResource(step.buttonRes()), onClick = onRequest)
        }
        BigButton(
            text = stringResource(R.string.onboarding_next),
            onClick = onNext,
            style = if (isGranted) BigButtonStyle.PRIMARY else BigButtonStyle.SECONDARY,
        )
        if (step != OnboardingStep.LOCATION) {
            Row {
                TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
            }
        }
    }
}

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun notificationPermissions(): Array<String> = if (Build.VERSION.SDK_INT >=
    Build.VERSION_CODES.TIRAMISU
) {
    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
} else {
    emptyArray()
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.isStepDone(step: OnboardingStep): Boolean = when (step) {
    OnboardingStep.LOCATION -> isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
    OnboardingStep.NOTIFICATIONS -> notificationPermissions().all(::isGranted)
    OnboardingStep.BATTERY -> getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ==
        true
    OnboardingStep.PROFILE -> false
}

// The exemption is required by the spec (03-ARCHITECTURE §7) for aggressive OEMs; sideload distribution.
@SuppressLint("BatteryLife")
private fun Context.batteryExemptionIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, (PACKAGE_SCHEME + packageName).toUri())

private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.LOCATION -> R.string.onboarding_location_title
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_title
    OnboardingStep.BATTERY -> R.string.onboarding_battery_title
    OnboardingStep.PROFILE -> R.string.onboarding_profile_title
}

private fun OnboardingStep.textRes(): Int = when (this) {
    OnboardingStep.LOCATION -> R.string.onboarding_location_text
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_text
    OnboardingStep.BATTERY, OnboardingStep.PROFILE -> R.string.onboarding_battery_text
}

private fun OnboardingStep.buttonRes(): Int = when (this) {
    OnboardingStep.LOCATION -> R.string.onboarding_location_button
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_button
    OnboardingStep.BATTERY, OnboardingStep.PROFILE -> R.string.onboarding_battery_button
}

private fun OnboardingStep.grantedRes(): Int = when (this) {
    OnboardingStep.LOCATION -> R.string.onboarding_location_granted
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_granted
    OnboardingStep.BATTERY, OnboardingStep.PROFILE -> R.string.onboarding_battery_granted
}

@ThemePreviews
@Composable
private fun PermissionStepPreview() {
    RideCompPreview {
        PermissionStep(step = OnboardingStep.LOCATION, isGranted = false, onRequest = {}, onNext = {}, onBack = {})
    }
}
