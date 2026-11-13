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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.feature.settings.R
import com.ridecomp.feature.settings.viewmodel.OnboardingEffect
import com.ridecomp.feature.settings.viewmodel.OnboardingStep
import com.ridecomp.feature.settings.viewmodel.OnboardingUiEvent
import com.ridecomp.feature.settings.viewmodel.OnboardingViewModel
import com.ridecomp.feature.settings.viewmodel.ProfileEffect
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.ui.R as UiR
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonSize
import com.ridecomp.ui.component.LinkText
import com.ridecomp.ui.component.OnboardingProgress
import com.ridecomp.ui.component.RideCompCard
import com.ridecomp.ui.component.WarningCard
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.udf.ObserveEffects
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

/** URI scheme for the battery-optimisation exemption request. */
private const val PACKAGE_SCHEME = "package:"

/** First launch (02-DESIGN §3.1, 13-UI-SCREENS #1, #2, #22): permissions, profile, sensors & GPS test. */
@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSensors: () -> Unit = {},
    viewModel: OnboardingViewModel = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel(),
) {
    val step by viewModel.uiState.collectAsStateWithLifecycle()
    val gps by viewModel.gpsStatus.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.Done -> onDone()
        }
    }
    ObserveEffects(profileViewModel.effects) { effect ->
        when (effect) {
            ProfileEffect.Saved -> viewModel.onEvent(OnboardingUiEvent.Next)
        }
    }
    val header: @Composable () -> Unit = { StepHeader(step) }
    Column(modifier = modifier.fillMaxSize().background(RideCompTheme.colors.bg)) {
        when (step) {
            OnboardingStep.PERMISSIONS -> PermissionsStep(onNext = {
                viewModel.onEvent(OnboardingUiEvent.Next)
            }, header)
            OnboardingStep.PROFILE -> ProfileForm(
                state = profileState,
                onEvent = profileViewModel::onEvent,
                isOnboarding = true,
                header = header,
                modifier = Modifier.padding(top = RideCompDimens.screenPaddingTop),
            )
            OnboardingStep.READY -> ReadyStep(
                gps = gps,
                onOpenSensors = onOpenSensors,
                onFinish = { viewModel.onEvent(OnboardingUiEvent.Finished) },
                header = header,
            )
        }
    }
}

/** Progress bars, "LANGKAH n DARI 3", title and text of each step. */
@Composable
private fun StepHeader(step: OnboardingStep) {
    val colors = RideCompTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        OnboardingProgress(
            step = step.ordinal + 1,
            total = OnboardingStep.entries.size,
            label = stringResource(R.string.onboarding_step, step.ordinal + 1, OnboardingStep.entries.size).uppercase(),
        )
        Text(
            text = stringResource(step.titleRes()),
            style = RideCompTheme.typography.screenTitle,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(step.textRes()), style = RideCompTheme.typography.body, color = colors.textTertiary)
    }
}

/** Scrollable step body with its main button kept at the bottom, like the design. */
@Composable
private fun StepLayout(content: @Composable () -> Unit, buttons: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    top = RideCompDimens.screenPaddingTop,
                    bottom = RideCompDimens.screenPaddingBottom,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap)) { content() }
            Column(
                modifier = Modifier.padding(top = RideCompDimens.blockGap),
                verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
            ) { buttons() }
        }
    }
}

/** #1: location (required), nearby devices and notifications, then the "don't kill the app" card. */
@Composable
private fun PermissionsStep(onNext: () -> Unit, header: @Composable () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh++
    }
    val isLocationGranted = remember(refresh) { context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) }
    StepLayout(
        content = {
            header()
            PERMISSION_CARDS.forEach { card ->
                PermissionCard(
                    card = card,
                    isGranted = remember(refresh, card) { card.permissions().all(context::isGranted) },
                    onRequest = { permissionLauncher.launch(card.permissions()) },
                )
            }
            BatteryCard(
                isExempt = remember(refresh) { context.isBatteryExempt() },
                onOpen = { settingsLauncher.launch(context.batteryExemptionIntent()) },
            )
        },
        buttons = {
            BigButton(
                text = stringResource(R.string.onboarding_next),
                onClick = onNext,
                isEnabled = isLocationGranted,
                size = BigButtonSize.SECONDARY,
            )
            if (!isLocationGranted) {
                Text(
                    text = stringResource(R.string.onboarding_location_required),
                    style = RideCompTheme.typography.caption,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
        },
    )
}

/** One permission card of #1. */
private enum class PermissionCardSpec(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val reasonRes: Int,
) {
    LOCATION(UiR.drawable.ic_location, R.string.onboarding_location_title, R.string.onboarding_location_reason),
    NEARBY(UiR.drawable.ic_nav_sensors, R.string.onboarding_nearby_title, R.string.onboarding_nearby_reason),
    NOTIFICATIONS(
        UiR.drawable.ic_bell,
        R.string.onboarding_notifications_title,
        R.string.onboarding_notifications_reason,
    ),
    ;

    /** Runtime permissions of this card on this Android version; empty = nothing to ask (always granted). */
    fun permissions(): Array<String> = when (this) {
        LOCATION -> LOCATION_PERMISSIONS
        // Android 8–11 scan with the location permission (D-29).
        NEARBY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
        NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }
}

private val PERMISSION_CARDS = PermissionCardSpec.entries

@Composable
private fun PermissionCard(card: PermissionCardSpec, isGranted: Boolean, onRequest: () -> Unit) {
    val colors = RideCompTheme.colors
    val tint = when (card) {
        PermissionCardSpec.LOCATION -> colors.ok
        PermissionCardSpec.NEARBY -> colors.track
        PermissionCardSpec.NOTIFICATIONS -> colors.warn
    }
    StepCard(card.iconRes, tint, stringResource(card.titleRes), stringResource(card.reasonRes)) {
        if (isGranted) {
            Text(
                text = stringResource(R.string.onboarding_granted),
                style = RideCompTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.ok,
            )
        } else {
            BigButton(
                text = stringResource(R.string.onboarding_allow),
                onClick = onRequest,
                size = BigButtonSize.TERTIARY,
                modifier = Modifier.fillMaxWidth(ALLOW_BUTTON_FRACTION),
            )
        }
    }
}

/** The allow button takes this share of the card's right side. */
private const val ALLOW_BUTTON_FRACTION = 0.4f

/** Icon, title and reason, with a status or button on the right (13-UI-SCREENS #1, #22). */
@Composable
private fun StepCard(
    @DrawableRes iconRes: Int,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    RideCompCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(RideCompDimens.iconSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = RideCompTheme.typography.cardTitle, color = RideCompTheme.colors.textPrimary)
                Text(
                    text = subtitle,
                    style = RideCompTheme.typography.caption,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
            trailing()
        }
    }
}

/**
 * "Jangan biarkan HP mematikan RideComp" with the detected brand's steps (03-ARCHITECTURE §7). Shown on
 * every phone: the exemption matters everywhere; the brand line only for known aggressive makers.
 */
@Composable
private fun BatteryCard(isExempt: Boolean, onOpen: () -> Unit) {
    val brand = remember { PhoneBrand.of(Build.MANUFACTURER) }
    val steps = stringResource(brand.stepsRes)
    WarningCard(
        title = stringResource(R.string.onboarding_battery_card_title),
        message = if (brand == PhoneBrand.OTHER) {
            steps
        } else {
            stringResource(R.string.onboarding_battery_detected, Build.MANUFACTURER, steps)
        },
        actionLabel = stringResource(
            if (isExempt) R.string.onboarding_battery_done else R.string.onboarding_battery_open,
        ),
        onAction = onOpen,
    )
}

/** #22: sensors (optional), GPS test, mounting tips. */
@Composable
private fun ReadyStep(
    gps: GpsStatus?,
    onOpenSensors: () -> Unit,
    onFinish: () -> Unit,
    header: @Composable () -> Unit,
) {
    val colors = RideCompTheme.colors
    StepLayout(
        content = {
            header()
            StepCard(
                UiR.drawable.ic_nav_sensors,
                colors.track,
                stringResource(R.string.onboarding_sensors_title),
                stringResource(R.string.onboarding_sensors_text),
            ) {
                BigButton(
                    text = stringResource(R.string.onboarding_scan),
                    onClick = onOpenSensors,
                    size = BigButtonSize.TERTIARY,
                    modifier = Modifier.fillMaxWidth(ALLOW_BUTTON_FRACTION),
                )
            }
            GpsTestCard(gps)
            RideCompCard {
                Text(
                    text = stringResource(R.string.onboarding_tips_title),
                    style = RideCompTheme.typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.onboarding_tips),
                    style = RideCompTheme.typography.body,
                    color = colors.textTertiary,
                )
            }
        },
        buttons = {
            BigButton(
                text = stringResource(R.string.onboarding_finish),
                onClick = onFinish,
                size = BigButtonSize.SECONDARY,
            )
            LinkText(stringResource(R.string.onboarding_skip), onClick = onFinish)
        },
    )
}

/** Searching (yellow) / OK (green) / permission or location off (red), as in 13-UI-SCREENS #22. */
@Composable
private fun GpsTestCard(gps: GpsStatus?) {
    val colors = RideCompTheme.colors
    val (detail, status, tint) = when (gps) {
        null, is GpsStatus.Searching -> Triple(
            stringResource(R.string.onboarding_gps_searching),
            stringResource(R.string.onboarding_gps_wait),
            colors.warnText,
        )
        is GpsStatus.Ready -> Triple(
            gps.accuracyM?.let { stringResource(R.string.onboarding_gps_ready, it.roundToInt()) }
                ?: stringResource(R.string.onboarding_gps_ready_unknown),
            stringResource(R.string.onboarding_gps_ok),
            colors.ok,
        )
        GpsStatus.NoPermission, GpsStatus.Disabled, GpsStatus.NotUsed -> Triple(
            stringResource(R.string.onboarding_gps_unavailable),
            stringResource(R.string.onboarding_gps_fail),
            colors.danger,
        )
    }
    StepCard(UiR.drawable.ic_location, tint, stringResource(R.string.onboarding_gps_title), detail) {
        Text(text = status, style = RideCompTheme.typography.caption.copy(fontWeight = FontWeight.Bold), color = tint)
    }
}

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.isBatteryExempt(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

// The exemption is required by the spec (03-ARCHITECTURE §7) for aggressive OEMs; sideload distribution.
@SuppressLint("BatteryLife")
private fun Context.batteryExemptionIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, (PACKAGE_SCHEME + packageName).toUri())

@StringRes
private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.PERMISSIONS -> R.string.onboarding_permissions_title
    OnboardingStep.PROFILE -> R.string.onboarding_profile_title
    OnboardingStep.READY -> R.string.onboarding_ready_title
}

@StringRes
private fun OnboardingStep.textRes(): Int = when (this) {
    OnboardingStep.PERMISSIONS -> R.string.onboarding_permissions_text
    OnboardingStep.PROFILE -> R.string.onboarding_profile_text
    OnboardingStep.READY -> R.string.onboarding_ready_text
}

@ThemePreviews
@Composable
private fun GpsTestCardPreview() {
    RideCompPreview { GpsTestCard(GpsStatus.Ready(5.0)) }
}

@ThemePreviews
@Composable
private fun StepHeaderPreview() {
    RideCompPreview { StepHeader(OnboardingStep.PERMISSIONS) }
}
