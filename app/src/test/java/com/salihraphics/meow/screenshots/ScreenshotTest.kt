package com.salihraphics.meow.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.StudyProfile
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

/** Renders each screen to build/outputs/roborazzi for visual review (light and dark). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
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
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_${if (dark) "dark" else "light"}.png")
    }

    private fun step(step: OnboardingStep, dark: Boolean, state: OnboardingUiState = answered) =
        shoot("onboarding_${step.ordinal}_${step.name.lowercase()}", dark) {
            OnboardingContent(state.copy(step = step), OnboardingActions())
        }

    @Test fun welcome() = step(OnboardingStep.WELCOME, dark = false)
    @Test fun meetLight() = step(OnboardingStep.MEET, dark = false)
    @Test fun meetDark() = step(OnboardingStep.MEET, dark = true)
    @Test fun examLight() = step(OnboardingStep.EXAM, dark = false)
    @Test fun examDark() = step(OnboardingStep.EXAM, dark = true)
    @Test fun subjectsLight() = step(OnboardingStep.SUBJECTS, dark = false)
    @Test fun subjectsDark() = step(OnboardingStep.SUBJECTS, dark = true)
    @Test fun levelLight() = step(OnboardingStep.LEVEL, dark = false)
    @Test fun sessionLight() = step(OnboardingStep.SESSION, dark = false)
    @Test fun sessionDark() = step(OnboardingStep.SESSION, dark = true)
    @Test fun nameLight() = step(OnboardingStep.NAME, dark = false)
    @Test fun nameEmptyDark() = step(OnboardingStep.NAME, dark = true, state = OnboardingUiState())
    @Test fun readyLight() = step(OnboardingStep.READY, dark = false)
    @Test fun readyDark() = step(OnboardingStep.READY, dark = true)

    @Test fun homeLight() = shoot("home", dark = false) { HomeScreen(answered.toProfile(), onOpenSettings = {}) }
    @Test fun homeEmptyDark() = shoot("home_empty", dark = true) { HomeScreen(StudyProfile(), onOpenSettings = {}) }

    @Test fun settingsLight() = shoot("settings", dark = false) {
        val repo = FakeUserPreferencesRepository(UserPreferences(onboardingCompleted = true, profile = answered.toProfile()))
        SettingsRoute(SettingsViewModel(repo), onBack = {}, onRestartOnboarding = {})
    }

    @Test fun moodsLight() = moods(dark = false)
    @Test fun moodsDark() = moods(dark = true)

    @OptIn(ExperimentalLayoutApi::class)
    private fun moods(dark: Boolean) = shoot("mascot_moods", dark) {
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MascotMood.entries.forEach { mood ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MeowCat(mood, Modifier.size(118.dp))
                    Text(mood.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}
