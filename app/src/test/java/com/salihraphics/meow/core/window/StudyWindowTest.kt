package com.salihraphics.meow.core.window

import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The study window: immersive and awake only while the study workspace is shown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyWindowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var studying by mutableStateOf(true)
    private val window get() = rule.activity.window

    private fun barsVisible(w: Window = window): Boolean {
        rule.waitForIdle()
        val insets = ViewCompat.getRootWindowInsets(w.decorView)!!
        return insets.isVisible(WindowInsetsCompat.Type.statusBars()) || insets.isVisible(WindowInsetsCompat.Type.navigationBars())
    }

    private fun keepsScreenOn(): Boolean {
        rule.waitForIdle()
        return window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    }

    /** A test window starts with no visible bars; a phone's window starts with them. */
    private fun systemDefaultBars(w: Window = window) {
        rule.runOnUiThread { WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars()) }
    }

    private fun start() {
        systemDefaultBars()
        rule.setContent { if (studying) StudyWindowEffect { Text("PDF") } else Text("Ayarlar") }
        rule.waitForIdle()
    }

    @Test
    fun studyingHidesTheBarsAndKeepsTheScreenOnLeavingRestoresBoth() {
        start()
        assertFalse(barsVisible())
        assertTrue(keepsScreenOn())
        // The bars come back with the standard swipe, for a moment.
        assertEquals(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
            WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior,
        )

        studying = false // leaving the workspace (e.g. back to the library or to settings)
        assertTrue(barsVisible())
        assertFalse(keepsScreenOn())
    }

    @Test
    fun otherScreensKeepTheirBarsAndNormalTimeout() {
        studying = false
        start()
        assertTrue(barsVisible())
        assertFalse(keepsScreenOn())
    }

    @Test
    fun comingBackFromTheBackgroundHidesTheBarsAgain() {
        start()
        // Something (the system, another app) showed the bars while MEOW was away.
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        assertTrue(barsVisible())
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertFalse(barsVisible())
        assertTrue(keepsScreenOn())
    }

    @Test
    fun reopeningThePdfIsImmersiveAndAwakeAgain() {
        start()
        studying = false
        assertTrue(barsVisible())
        studying = true // back into the workspace (or a new composition after the device turned)
        assertFalse(barsVisible())
        assertTrue(keepsScreenOn())
    }

    @Test
    fun aDialogOpenedWhileStudyingKeepsTheBarsHidden() {
        var dialogWindow: Window? = null
        rule.setContent {
            StudyWindowEffect {
                Dialog(onDismissRequest = {}) {
                    ImmersiveDialogWindow()
                    dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
                    Text("Sayfaya git")
                }
            }
        }
        rule.waitForIdle()
        assertNotNull(dialogWindow)
        assertFalse(barsVisible(dialogWindow!!))
        assertFalse(barsVisible())
    }

    @Test
    fun theSameDialogElsewhereLeavesTheBarsAlone() {
        systemDefaultBars()
        var dialogWindow: Window? = null
        rule.setContent {
            Dialog(onDismissRequest = {}) {
                ImmersiveDialogWindow()
                dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
                Text("Ayar")
            }
        }
        rule.waitForIdle()
        systemDefaultBars(dialogWindow!!)
        // Recompose: the dialog hook must not hide them outside the workspace.
        rule.waitForIdle()
        assertTrue(barsVisible(dialogWindow!!))
        assertTrue(barsVisible())
    }
}
