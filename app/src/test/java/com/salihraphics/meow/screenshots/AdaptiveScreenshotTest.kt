package com.salihraphics.meow.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.data.account.LocalOnlyAccountRepository
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.UserPreferences
import com.salihraphics.meow.feature.home.HomeScreen
import com.salihraphics.meow.feature.onboarding.OnboardingActions
import com.salihraphics.meow.feature.onboarding.OnboardingContent
import com.salihraphics.meow.feature.onboarding.OnboardingStep
import com.salihraphics.meow.feature.onboarding.OnboardingUiState
import com.salihraphics.meow.feature.settings.SettingsRoute
import com.salihraphics.meow.feature.settings.SettingsViewModel
import com.salihraphics.meow.testing.FakeUserPreferencesRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE_LANDSCAPE = "w852dp-h393dp-land-xhdpi"
private const val TABLET_PORTRAIT = "w800dp-h1280dp-port-mdpi"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-mdpi"

/** The same screens at phone-landscape and tablet sizes, to check the adaptive layouts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AdaptiveScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val answered = OnboardingUiState(
        exam = ExamType.YKS,
        subjects = setOf(Subject.MATH, Subject.PHYSICS, Subject.CHEMISTRY),
        level = StudyLevel.FOUNDATION,
        sessionLength = SessionLength.MIN_30,
        nameInput = "Salih",
    )

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            MeowTheme(darkTheme = dark, mascotAnimationsEnabled = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/adaptive/$name.png")
    }

    private fun step(device: String, step: OnboardingStep, dark: Boolean = false) =
        shoot("${device}_onboarding_${step.ordinal}_${step.name.lowercase()}", dark) {
            OnboardingContent(answered.copy(step = step), OnboardingActions())
        }

    private fun home(device: String) = shoot("${device}_home") { HomeScreen(answered.toProfile(), onOpenSettings = {}) }

    private fun settings(device: String) = shoot("${device}_settings") {
        val repo = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true, profile = answered.toProfile()))
        SettingsRoute(SettingsViewModel(repo, LocalOnlyAccountRepository()), onBack = {}, onRestartOnboarding = {})
    }

    @Test @Config(qualifiers = PHONE_LANDSCAPE) fun phoneLandscapeWelcome() = step("phone_land", OnboardingStep.WELCOME)
    @Test @Config(qualifiers = PHONE_LANDSCAPE) fun phoneLandscapeMeet() = step("phone_land", OnboardingStep.MEET)
    @Test @Config(qualifiers = PHONE_LANDSCAPE) fun phoneLandscapeExam() = step("phone_land", OnboardingStep.EXAM)
    @Test @Config(qualifiers = PHONE_LANDSCAPE) fun phoneLandscapeName() = step("phone_land", OnboardingStep.NAME)
    @Test @Config(qualifiers = PHONE_LANDSCAPE) fun phoneLandscapeHome() = home("phone_land")

    @Test @Config(qualifiers = TABLET_PORTRAIT) fun tabletPortraitMeet() = step("tablet_port", OnboardingStep.MEET)
    @Test @Config(qualifiers = TABLET_PORTRAIT) fun tabletPortraitLevel() = step("tablet_port", OnboardingStep.LEVEL)
    @Test @Config(qualifiers = TABLET_PORTRAIT) fun tabletPortraitHome() = home("tablet_port")

    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeWelcome() = step("tablet_land", OnboardingStep.WELCOME)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeMeet() = step("tablet_land", OnboardingStep.MEET)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeExam() = step("tablet_land", OnboardingStep.EXAM)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeSubjects() = step("tablet_land", OnboardingStep.SUBJECTS, dark = true)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeSession() = step("tablet_land", OnboardingStep.SESSION)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeReady() = step("tablet_land", OnboardingStep.READY)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeHome() = home("tablet_land")
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeSettings() = settings("tablet_land")
}
