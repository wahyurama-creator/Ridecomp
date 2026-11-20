package com.ridecomp.feature.settings

import app.cash.turbine.test
import com.ridecomp.core.model.AppLanguage
import com.ridecomp.core.model.AppTheme
import com.ridecomp.core.model.GuidePreferences
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.Surface
import com.ridecomp.core.source.AppLanguageStore
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeGuideRepository
import com.ridecomp.core.testing.FakeLocationSource
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.usecase.CompleteOnboardingUseCase
import com.ridecomp.core.usecase.DismissTipUseCase
import com.ridecomp.core.usecase.EstimateHrMaxUseCase
import com.ridecomp.core.usecase.GetAppLanguageUseCase
import com.ridecomp.core.usecase.ObserveAppThemeUseCase
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.ObserveGuideUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ProfileField
import com.ridecomp.core.usecase.ResetTipsUseCase
import com.ridecomp.core.usecase.SaveProfileUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.SetAppLanguageUseCase
import com.ridecomp.core.usecase.SetAppThemeUseCase
import com.ridecomp.core.usecase.SetRiderLevelUseCase
import com.ridecomp.feature.settings.ui.PhoneBrand
import com.ridecomp.feature.settings.viewmodel.DisplayViewModel
import com.ridecomp.feature.settings.viewmodel.GuideUiEvent
import com.ridecomp.feature.settings.viewmodel.GuideViewModel
import com.ridecomp.feature.settings.viewmodel.LanguageEffect
import com.ridecomp.feature.settings.viewmodel.LanguageViewModel
import com.ridecomp.feature.settings.viewmodel.OnboardingEffect
import com.ridecomp.feature.settings.viewmodel.OnboardingStep
import com.ridecomp.feature.settings.viewmodel.OnboardingUiEvent
import com.ridecomp.feature.settings.viewmodel.OnboardingViewModel
import com.ridecomp.feature.settings.viewmodel.ProfileEffect
import com.ridecomp.feature.settings.viewmodel.ProfileUiEvent
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.feature.settings.viewmodel.RideSettingsUiEvent
import com.ridecomp.feature.settings.viewmodel.RideSettingsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelsTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val profiles = FakeProfileRepository(RiderProfile(weightKg = 70.0))
    private val clock = FakeClock()

    private fun profileViewModel() = ProfileViewModel(
        ObserveProfileUseCase(profiles),
        SaveProfileUseCase(profiles, clock),
        EstimateHrMaxUseCase(clock),
    )

    @Test
    fun `profile loads the stored values as text`() {
        val viewModel = profileViewModel()

        assertEquals("70", viewModel.uiState.value.weightKg)
        assertTrue(viewModel.uiState.value.isLoaded)
    }

    @Test
    fun `indonesian decimal comma is accepted and saved`() = runTest {
        val viewModel = profileViewModel()

        viewModel.effects.test {
            viewModel.onEvent(ProfileUiEvent.FieldChanged(ProfileField.WEIGHT, "68,5"))
            viewModel.onEvent(ProfileUiEvent.Save)

            assertEquals(ProfileEffect.Saved, awaitItem())
        }
        assertEquals(68.5, profiles.get().weightKg)
    }

    @Test
    fun `saving keeps the surface and saves the hand position`() = runTest {
        profiles.save(RiderProfile(weightKg = 70.0, surface = Surface.GRAVEL, ridingPosition = RidingPosition.DROPS))
        val viewModel = profileViewModel()
        assertEquals(RidingPosition.DROPS, viewModel.uiState.value.ridingPosition)

        viewModel.effects.test {
            viewModel.onEvent(ProfileUiEvent.PositionChanged(RidingPosition.TOPS))
            viewModel.onEvent(ProfileUiEvent.Save)
            assertEquals(ProfileEffect.Saved, awaitItem())
        }

        assertEquals(RidingPosition.TOPS, profiles.get().ridingPosition)
        assertEquals(Surface.GRAVEL, profiles.get().surface)
    }

    @Test
    fun `text that is not a number marks the field invalid and nothing is saved`() {
        val viewModel = profileViewModel()

        viewModel.onEvent(ProfileUiEvent.FieldChanged(ProfileField.FTP, "abc"))
        viewModel.onEvent(ProfileUiEvent.Save)

        assertEquals(setOf(ProfileField.FTP), viewModel.uiState.value.invalidFields)
    }

    @Test
    fun `out of range value is reported by the use case`() {
        val viewModel = profileViewModel()

        viewModel.onEvent(ProfileUiEvent.FieldChanged(ProfileField.WEIGHT, "5"))
        viewModel.onEvent(ProfileUiEvent.Save)

        assertEquals(setOf(ProfileField.WEIGHT), viewModel.uiState.value.invalidFields)
    }

    @Test
    fun `hr max estimate needs a birth year`() {
        val viewModel = profileViewModel()
        // Not offered while the birth year is empty or still being typed (T11.5a).
        assertFalse(viewModel.uiState.value.canEstimateHrMax)
        viewModel.onEvent(ProfileUiEvent.FieldChanged(ProfileField.BIRTH_YEAR, "19"))
        assertFalse(viewModel.uiState.value.canEstimateHrMax)
        viewModel.onEvent(ProfileUiEvent.FieldChanged(ProfileField.BIRTH_YEAR, ""))

        viewModel.onEvent(ProfileUiEvent.EstimateHrMax)
        assertTrue(ProfileField.BIRTH_YEAR in viewModel.uiState.value.invalidFields)

        viewModel.onEvent(ProfileUiEvent.FieldChanged(ProfileField.BIRTH_YEAR, "1996"))
        assertTrue(viewModel.uiState.value.canEstimateHrMax)
        viewModel.onEvent(ProfileUiEvent.EstimateHrMax)
        assertEquals("190", viewModel.uiState.value.hrMaxBpm)
        assertTrue(viewModel.uiState.value.isHrMaxEstimated)
    }

    @Test
    fun `ride settings changes are saved immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel =
            RideSettingsViewModel(ObserveRideSettingsUseCase(repository), SaveRideSettingsUseCase(repository))

        viewModel.onEvent(RideSettingsUiEvent.Screen(ScreenMode.ALWAYS_ON))
        viewModel.onEvent(RideSettingsUiEvent.DrinkInterval(null))

        assertEquals(
            RideSettings(screenMode = ScreenMode.ALWAYS_ON, drinkIntervalMin = null),
            repository.getRideSettings(),
        )
    }

    @Test
    fun `onboarding walks through the steps and completes`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel =
            OnboardingViewModel(
                CompleteOnboardingUseCase(settings),
                ObserveGpsStatusUseCase(FakeLocationSource()),
                SetRiderLevelUseCase(FakeGuideRepository()),
            )

        viewModel.onEvent(OnboardingUiEvent.Back)
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value)
        repeat(6) { viewModel.onEvent(OnboardingUiEvent.Next) }
        assertEquals(OnboardingStep.READY, viewModel.uiState.value)

        viewModel.effects.test {
            viewModel.onEvent(OnboardingUiEvent.Finished)
            assertEquals(OnboardingEffect.Done, awaitItem())
        }
        assertTrue(settings.observeOnboardingDone().first())
    }

    @Test
    fun `the level step stores the rider's experience and moves on`() = runTest {
        val guide = FakeGuideRepository()
        val viewModel = OnboardingViewModel(
            CompleteOnboardingUseCase(FakeSettingsRepository()),
            ObserveGpsStatusUseCase(FakeLocationSource()),
            SetRiderLevelUseCase(guide),
        )
        repeat(3) { viewModel.onEvent(OnboardingUiEvent.Next) }
        assertEquals(OnboardingStep.LEVEL, viewModel.uiState.value)

        viewModel.onEvent(OnboardingUiEvent.ChooseLevel(RiderLevel.EXPERIENCED))
        // A double tap must not skip the profile step.
        viewModel.onEvent(OnboardingUiEvent.ChooseLevel(RiderLevel.EXPERIENCED))

        assertEquals(OnboardingStep.PROFILE, viewModel.uiState.value)
        assertEquals(RiderLevel.EXPERIENCED, guide.state.value.level)
    }

    @Test
    fun `level, profile and bike can be skipped, permissions and language cannot`() = runTest {
        val guide = FakeGuideRepository()
        val viewModel = OnboardingViewModel(
            CompleteOnboardingUseCase(FakeSettingsRepository()),
            ObserveGpsStatusUseCase(FakeLocationSource()),
            SetRiderLevelUseCase(guide),
        )
        repeat(2) { viewModel.onEvent(OnboardingUiEvent.Next) }
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value)
        viewModel.onEvent(OnboardingUiEvent.Skip)
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.uiState.value)

        viewModel.onEvent(OnboardingUiEvent.Next)
        repeat(3) { viewModel.onEvent(OnboardingUiEvent.Skip) }
        assertEquals(OnboardingStep.READY, viewModel.uiState.value)
        assertEquals(null, guide.state.value.level)
        assertEquals(
            listOf(OnboardingStep.LEVEL, OnboardingStep.PROFILE, OnboardingStep.BIKE),
            OnboardingStep.entries.filter {
                it.isSkippable
            },
        )
        assertEquals(6, OnboardingStep.SETUP.size)
    }

    @Test
    fun `closing a tip hides it, resetting shows it again`() = runTest {
        val guide = FakeGuideRepository(GuidePreferences(RiderLevel.BEGINNER))
        val viewModel = GuideViewModel(
            ObserveGuideUseCase(guide),
            SetRiderLevelUseCase(guide),
            DismissTipUseCase(guide),
            ResetTipsUseCase(guide),
        )

        viewModel.onEvent(GuideUiEvent.Dismiss(GuideTip.ROUTES))
        assertEquals(false, viewModel.uiState.value.preferences?.shows(GuideTip.ROUTES))
        viewModel.onEvent(GuideUiEvent.ResetTips)
        assertEquals(true, viewModel.uiState.value.preferences?.shows(GuideTip.ROUTES))
    }

    @Test
    fun `phone brands are recognised from the manufacturer`() {
        assertEquals(PhoneBrand.XIAOMI, PhoneBrand.of("Xiaomi"))
        assertEquals(PhoneBrand.XIAOMI, PhoneBrand.of("POCO"))
        assertEquals(PhoneBrand.OPPO, PhoneBrand.of("realme"))
        assertEquals(PhoneBrand.TRANSSION, PhoneBrand.of("INFINIX MOBILITY LIMITED"))
        assertEquals(PhoneBrand.OTHER, PhoneBrand.of("Google"))
    }

    @Test
    fun `choosing a language applies it once and asks for a redraw`() = runTest {
        val store = object : AppLanguageStore {
            var language = AppLanguage.SYSTEM
            var sets = 0

            override fun current() = language

            override fun set(language: AppLanguage) {
                this.language = language
                sets++
            }
        }
        val viewModel = LanguageViewModel(GetAppLanguageUseCase(store), SetAppLanguageUseCase(store))
        assertEquals(AppLanguage.SYSTEM, viewModel.uiState.value)

        viewModel.effects.test {
            viewModel.onEvent(AppLanguage.INDONESIAN)
            assertEquals(LanguageEffect.Recreate, awaitItem())
            viewModel.onEvent(AppLanguage.INDONESIAN)
            expectNoEvents()
        }
        assertEquals(AppLanguage.INDONESIAN, store.language)
        assertEquals(1, store.sets)
    }

    @Test
    fun `the theme starts dark, a choice is stored and shown`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = DisplayViewModel(ObserveAppThemeUseCase(settings), SetAppThemeUseCase(settings))
        assertEquals(AppTheme.DARK, viewModel.uiState.value)

        viewModel.onEvent(AppTheme.LIGHT)

        assertEquals(AppTheme.LIGHT, settings.theme.value)
        assertEquals(AppTheme.LIGHT, viewModel.uiState.value)
    }
}
