package com.salihraphics.meow.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tap once, hold to repeat (after a delay, a little faster), stop on release or at a limit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepeatingButtonTest {
    @get:Rule val rule = createComposeRule()

    private var value = 0
    private var max = 1_000
    private var enabled by mutableStateOf(true)
    private var shown by mutableStateOf(true)

    private fun start() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            if (shown) {
                RepeatingButton("Artır", enabled, onStep = { (value < max).also { if (it) value++ } }) { Text("+") }
            }
        }
        rule.waitForIdle()
    }

    private val button get() = rule.onNode(hasContentDescription("Artır"))

    private fun press() = button.performTouchInput { down(center) }
    private fun release() = button.performTouchInput { up() }
    private fun hold(ms: Long) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    @Test
    fun aTapStepsOnce() {
        start()
        button.performClick()
        hold(2_000)
        assertEquals(1, value)
    }

    @Test
    fun aShortPressIsATapNotAHold() {
        start()
        press()
        hold(HoldToRepeat.INITIAL_DELAY_MS - 100)
        assertEquals(0, value)
        release()
        hold(100)
        assertEquals(1, value)
    }

    @Test
    fun holdingRepeatsAfterADelayThenFasterAndReleasingStopsWithoutAnExtraStep() {
        start()
        press()
        hold(HoldToRepeat.INITIAL_DELAY_MS + 20)
        assertEquals(1, value) // the first repeat
        // Over the next 2 s: more steps than at the first pace, so it sped up, but not wildly.
        hold(2_000)
        val held = value
        val atFirstPace = 1 + 2_000 / HoldToRepeat.FIRST_INTERVAL_MS
        assertTrue("sped up: $held", held > atFirstPace)
        assertTrue("modest: $held", held <= 1 + 2_000 / HoldToRepeat.FASTEST_MS)
        release()
        hold(2_000)
        assertEquals("no extra step on release, nothing after it", held, value)
    }

    @Test
    fun theIntervalsShrinkModestlyToAFloor() {
        val intervals = (0 until 20).map { HoldToRepeat.interval(it) }
        assertEquals(HoldToRepeat.FIRST_INTERVAL_MS, intervals.first())
        assertTrue(intervals.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(HoldToRepeat.FASTEST_MS, intervals.last())
    }

    @Test
    fun repeatsStopAtTheLimit() {
        max = 5
        start()
        press()
        hold(10_000)
        assertEquals(5, value)
        release()
    }

    @Test
    fun slidingOffTheButtonCancelsTheHold() {
        start()
        press()
        hold(HoldToRepeat.INITIAL_DELAY_MS + 20)
        assertEquals(1, value)
        button.performTouchInput { moveBy(Offset(width * 3f, 0f)) }
        val atCancel = value
        hold(2_000)
        assertEquals(atCancel, value)
        release()
        hold(500)
        assertEquals(atCancel, value)
    }

    @Test
    fun leavingTheScreenStopsTheRepeats() {
        start()
        press()
        hold(HoldToRepeat.INITIAL_DELAY_MS + 20)
        shown = false
        hold(50)
        val atLeave = value
        hold(2_000)
        assertEquals(atLeave, value)
    }

    @Test
    fun aDisabledButtonDoesNothing() {
        enabled = false
        start()
        button.performClick()
        press()
        hold(2_000)
        release()
        assertEquals(0, value)
    }

    @Test
    fun accessibilityClickStepsOnce() {
        start()
        button.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, value)
    }
}
