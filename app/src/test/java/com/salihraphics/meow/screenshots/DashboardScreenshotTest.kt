package com.salihraphics.meow.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.domain.model.ExamDate
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.ExamGoalType
import com.salihraphics.meow.domain.model.ExamSession
import com.salihraphics.meow.domain.model.StudyProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w393dp-h852dp-xhdpi"
private const val PHONE_TALL = "w393dp-h1700dp-xhdpi"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-mdpi"
private const val TABLET_PORTRAIT = "w800dp-h1280dp-port-mdpi"

/** The dashboard in its states. PHONE_TALL shows the whole scrolling page at once. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DashboardScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val profile = StudyProfile(displayName = "Salih")

    private fun shoot(name: String, dark: Boolean = false, act: (() -> Unit)? = null, content: @Composable () -> Unit) {
        rule.setContent {
            MeowTheme(darkTheme = dark, mascotAnimationsEnabled = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        if (act != null) {
            act()
            rule.mainClock.advanceTimeBy(600)
            captureScreenRoboImage("build/outputs/roborazzi/dashboard/$name.png")
        } else {
            rule.onRoot().captureRoboImage("build/outputs/roborazzi/dashboard/$name.png")
        }
    }

    @Test @Config(qualifiers = PHONE_TALL) fun phoneFull() = shoot("phone_full") { SampleHome(profile) }
    @Test @Config(qualifiers = PHONE_TALL) fun phoneFullDark() = shoot("phone_full_dark", dark = true) { SampleHome(profile) }
    @Test @Config(qualifiers = PHONE) fun phoneFirstScreen() = shoot("phone_first_screen") { SampleHome(profile) }
    @Test @Config(qualifiers = PHONE_TALL) fun phoneEmpty() = shoot("phone_empty") { SampleHome(StudyProfile(), full = false, goal = null) }
    @Test @Config(qualifiers = PHONE_TALL) fun phoneEmptyDark() = shoot("phone_empty_dark", dark = true) { SampleHome(StudyProfile(), full = false, goal = null) }
    @Test @Config(qualifiers = PHONE) fun phoneLgsToday() = shoot("phone_lgs_today") {
        SampleHome(profile, goal = ExamGoal(ExamGoalType.LGS, mapOf(ExamSession.LGS to ExamDate(2026, 10, 22, 23, 30))))
    }
    @Test @Config(qualifiers = PHONE) fun phoneAytNext() = shoot("phone_ayt_next", dark = true) {
        SampleHome(profile, now = ExamSession.TYT.defaultDate.toMillis() + 3 * 3_600_000L)
    }
    @Test @Config(qualifiers = PHONE) fun phoneDone() = shoot("phone_done") { SampleHome(profile, now = ExamSession.AYT_YDT.defaultDate.toMillis() + 86_400_000L) }
    @Test @Config(qualifiers = PHONE) fun phoneOff() = shoot("phone_off") { SampleHome(profile, goal = ExamGoal(ExamGoalType.YKS, enabled = false)) }
    @Test @Config(qualifiers = PHONE) fun phoneGoalDialog() = shoot("phone_goal_dialog", act = {
        rule.onNode(hasText("Sınav hedefini düzenle", substring = false).or(hasText("Sınav sayacı"))).performScrollTo().performClick()
    }) { SampleHome(profile) }
    @Test @Config(qualifiers = PHONE) fun phoneGoalDialogDark() = shoot("phone_goal_dialog_dark", dark = true, act = {
        rule.onNode(hasText("Sınav sayacı")).performScrollTo().performClick()
    }) { SampleHome(profile) }
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscape() = shoot("tablet_land") { SampleHome(profile) }
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeDark() = shoot("tablet_land_dark", dark = true) { SampleHome(profile) }
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun tabletLandscapeEmpty() = shoot("tablet_land_empty") { SampleHome(StudyProfile(), full = false, goal = null) }
    @Test @Config(qualifiers = TABLET_PORTRAIT) fun tabletPortrait() = shoot("tablet_port") { SampleHome(profile) }
}
