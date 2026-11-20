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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ridecomp.core.model.RiderLevel
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
import com.ridecomp.ui.component.BigButtonStyle
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

/**
 * The first-bike form of the BIKE step (D-89), supplied by the app from :feature-ride so the features stay
 * independent: it shows [header] above the form and calls `onSaved` once the bike is stored.
 */
typealias OnboardingBikeStep = @Composable (
    header: @Composable () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier,
) -> Unit

/** First launch (02-DESIGN §3.1, 13-UI-SCREENS #1, #2, #22): permissions, profile, bike, sensors & GPS test. */
@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    bikeStep: OnboardingBikeStep,
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
    val header: @Composable () -> Unit = {
        StepHeader(step, onSkip = { viewModel.onEvent(OnboardingUiEvent.Skip) })
    }
    Column(modifier = modifier.fillMaxSize().background(RideCompTheme.colors.bg)) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(onStart = { viewModel.onEvent(OnboardingUiEvent.Next) })
            OnboardingStep.LANGUAGE -> LanguageStep(onNext = { viewModel.onEvent(OnboardingUiEvent.Next) }, header)
            OnboardingStep.PERMISSIONS -> PermissionsStep(onNext = {
                viewModel.onEvent(OnboardingUiEvent.Next)
            }, header)
            OnboardingStep.LEVEL -> LevelStep(
                onChoose = { viewModel.onEvent(OnboardingUiEvent.ChooseLevel(it)) },
                onBack = { viewModel.onEvent(OnboardingUiEvent.Back) },
                header = header,
            )
            OnboardingStep.PROFILE -> ProfileForm(
                state = profileState,
                onEvent = profileViewModel::onEvent,
                isOnboarding = true,
                header = header,
                modifier = Modifier.padding(top = RideCompDimens.screenPaddingTop),
            )
            OnboardingStep.BIKE -> bikeStep(
                header,
                { viewModel.onEvent(OnboardingUiEvent.Next) },
                Modifier.padding(top = RideCompDimens.screenPaddingTop),
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

/**
 * Progress bars and "LANGKAH n DARI 6" with "Lewati" on skippable steps (D-105), then the step's icon in a
 * tinted circle, its title and text (T10.12).
 */
@Composable
private fun StepHeader(step: OnboardingStep, onSkip: () -> Unit) {
    val colors = RideCompTheme.colors
    val number = OnboardingStep.SETUP.indexOf(step) + 1
    val total = OnboardingStep.SETUP.size
    Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OnboardingProgress(
                step = number,
                total = total,
                label = stringResource(R.string.onboarding_step, number, total).uppercase(),
                modifier = Modifier.weight(1f),
            )
            // Sized to its text: a full-width button would squeeze the progress bars to nothing.
            if (step.isSkippable) {
                LinkText(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = onSkip,
                    modifier = Modifier.padding(start = RideCompDimens.spacingSm),
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = RideCompDimens.spacingS)
                .size(RideCompDimens.stepIconCircle)
                .clip(CircleShape)
                .background(colors.accentTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(step.iconRes()),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(RideCompDimens.iconSize),
            )
        }
        Text(
            text = stringResource(step.titleRes()),
            style = RideCompTheme.typography.screenTitle,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(step.textRes()), style = RideCompTheme.typography.body, color = colors.textTertiary)
        if (step.isSkippable) {
            Text(
                text = stringResource(R.string.onboarding_skip_hint),
                style = RideCompTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** Scrollable step body; the main buttons stay pinned at the bottom, always reachable (T10.12). */
@Composable
private fun StepLayout(content: @Composable () -> Unit, buttons: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = RideCompDimens.screenPaddingHorizontal,
                    end = RideCompDimens.screenPaddingHorizontal,
                    top = RideCompDimens.screenPaddingTop,
                    bottom = RideCompDimens.blockGap,
                ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap),
        ) { content() }
        Column(
            modifier = Modifier.padding(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                top = RideCompDimens.spacingS,
                bottom = RideCompDimens.screenPaddingBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        ) { buttons() }
    }
}

/** Language (D-88): applied on tap, so the rest of onboarding is already in it. */
@Composable
private fun LanguageStep(onNext: () -> Unit, header: @Composable () -> Unit) {
    StepLayout(
        content = {
            header()
            LanguagePicker()
        },
        buttons = { BigButton(text = stringResource(R.string.onboarding_next), onClick = onNext) },
    )
}

/** Experience (Phase 4G, owner request): beginner is preselected; it decides how many tips show. */
@Composable
private fun LevelStep(onChoose: (RiderLevel) -> Unit, onBack: () -> Unit, header: @Composable () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(RiderLevel.BEGINNER) }
    StepLayout(
        content = {
            header()
            RiderLevelChooser(selected = selected, onSelect = { selected = it })
        },
        buttons = {
            BigButton(text = stringResource(R.string.onboarding_next), onClick = { onChoose(selected) })
            BigButton(
                text = stringResource(R.string.onboarding_back_label),
                onClick = onBack,
                style = BigButtonStyle.TEXT,
            )
        },
    )
}

/** #1: location (required), nearby devices and notifications, then the "don't kill the app" card. */
@Composable
private fun PermissionsStep(onNext: () -> Unit, header: @Composable () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // "Allow & continue" asks everything at once and moves on as soon as location is granted.
    var isContinueAfterGrant by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        if (isContinueAfterGrant && context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) onNext()
        isContinueAfterGrant = false
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
                text = stringResource(
                    if (isLocationGranted) R.string.onboarding_next else R.string.onboarding_allow_all,
                ),
                onClick = {
                    if (isLocationGranted) {
                        onNext()
                    } else {
                        isContinueAfterGrant = true
                        val missing = PERMISSION_CARDS.flatMap { it.permissions().toList() }
                            .filterNot(context::isGranted)
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                },
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
        PermissionCardSpec.NOTIFICATIONS -> colors.warnText
    }
    val need = stringResource(
        if (card ==
            PermissionCardSpec.LOCATION
        ) {
            R.string.onboarding_required
        } else {
            R.string.onboarding_optional
        },
    )
    StepCard(card.iconRes, tint, stringResource(card.titleRes), "$need · ${stringResource(card.reasonRes)}") {
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

@DrawableRes
private fun OnboardingStep.iconRes(): Int = when (this) {
    OnboardingStep.WELCOME, OnboardingStep.READY -> UiR.drawable.ic_check
    OnboardingStep.LANGUAGE -> UiR.drawable.ic_language
    OnboardingStep.PERMISSIONS -> UiR.drawable.ic_location
    OnboardingStep.LEVEL -> UiR.drawable.ic_trophy
    OnboardingStep.PROFILE -> UiR.drawable.ic_heart
    OnboardingStep.BIKE -> UiR.drawable.ic_nav_ride
}

@StringRes
private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.WELCOME -> R.string.welcome_app_name
    OnboardingStep.LANGUAGE -> R.string.onboarding_language_title
    OnboardingStep.PERMISSIONS -> R.string.onboarding_permissions_title
    OnboardingStep.LEVEL -> R.string.guide_level_title
    OnboardingStep.PROFILE -> R.string.onboarding_profile_title
    OnboardingStep.BIKE -> R.string.onboarding_bike_title
    OnboardingStep.READY -> R.string.onboarding_ready_title
}

@StringRes
private fun OnboardingStep.textRes(): Int = when (this) {
    OnboardingStep.WELCOME -> R.string.welcome_text
    OnboardingStep.LANGUAGE -> R.string.onboarding_language_text
    OnboardingStep.PERMISSIONS -> R.string.onboarding_permissions_text
    OnboardingStep.LEVEL -> R.string.guide_level_text
    OnboardingStep.PROFILE -> R.string.onboarding_profile_text
    OnboardingStep.BIKE -> R.string.onboarding_bike_text
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
    RideCompPreview { StepHeader(OnboardingStep.PROFILE, onSkip = {}) }
}
