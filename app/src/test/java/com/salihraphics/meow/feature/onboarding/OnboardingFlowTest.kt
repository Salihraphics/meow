package com.salihraphics.meow.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import com.salihraphics.meow.MainActivity
import com.salihraphics.meow.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Drives the real app: onboarding → home, then checks the next launch skips onboarding. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun waitForText(value: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun completeOnboardingThenRelaunchGoesStraightHome() {
        // Welcome advances on its own.
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.meet_cta)).performClick()

        // 1/5 Exam: answer it.
        rule.onNodeWithText(text(R.string.exam_lgs)).performClick().assertIsSelected()
        rule.onNodeWithText(text(R.string.action_continue)).performClick()
        // 2/5 Subjects: LGS list is shown.
        rule.onNodeWithText(text(R.string.subject_science)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.action_skip)).performClick()
        // 3/5 and 4/5: skip.
        rule.onNodeWithText(text(R.string.level_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.action_skip)).performClick()
        rule.onNodeWithText(text(R.string.action_skip)).performClick()
        // 5/5 Name.
        rule.onNodeWithText(text(R.string.name_label)).performTextInput("Deniz")
        rule.onNodeWithText(text(R.string.action_continue)).performClick()

        rule.onNodeWithText(text(R.string.ready_title_named, "Deniz")).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.ready_cta)).performClick()
        // Saving happens on DataStore's own thread, which Compose idling does not track.
        waitForText(text(R.string.home_greeting_named, "Deniz"))
        rule.onNodeWithText(text(R.string.home_greeting_named, "Deniz")).assertIsDisplayed()

        // Relaunch: onboarding must not appear again.
        rule.activityRule.scenario.recreate()
        waitForText(text(R.string.home_greeting_named, "Deniz"))
        rule.onNodeWithText(text(R.string.home_greeting_named, "Deniz")).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.exam_lgs)).assertIsDisplayed()
    }
}
