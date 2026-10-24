package com.salihraphics.meow.feature.viewer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who owns a touch on the pages: the list, a pinch, a manual pan, or nobody (palm). */
class TouchArbiterTest {
    private val slop = 8f
    private fun arbiter() = TouchArbiter(slop)
    private fun touch(id: Long, x: Float, y: Float, t: Long, pressed: Boolean = true) = TouchSample(id, Offset(x, y), pressed, stylus = false, uptime = t)
    private fun pen(id: Long, x: Float, y: Float, t: Long, pressed: Boolean = true) = TouchSample(id, Offset(x, y), pressed, stylus = true, uptime = t)

    @Test
    fun oneFingerIsAlwaysTheLists() {
        val a = arbiter()
        assertEquals(TouchAction.Pass, a.onEvent(listOf(touch(1, 100f, 500f, 0)), drawing = false))
        for (i in 1..20) assertEquals(TouchAction.Pass, a.onEvent(listOf(touch(1, 100f, 500f - i * 20, i * 16L)), drawing = false))
        assertEquals(TouchAction.Pass, a.onEvent(listOf(touch(1, 100f, 100f, 400, pressed = false)), drawing = false))
    }

    @Test
    fun twoFingersTogetherPinchOnceTheyReallyMove() {
        val a = arbiter()
        a.onEvent(listOf(touch(1, 300f, 500f, 0)), false)
        assertEquals(TouchAction.Pass, a.onEvent(listOf(touch(1, 300f, 500f, 30), touch(2, 500f, 500f, 30)), false))
        // Tiny wobble: still nothing.
        assertEquals(TouchAction.Pass, a.onEvent(listOf(touch(1, 299f, 500f, 46), touch(2, 501f, 500f, 46)), false))
        val start = a.onEvent(listOf(touch(1, 280f, 500f, 62), touch(2, 520f, 500f, 62)), false)
        assertTrue(start is TouchAction.Transform)
        val step = a.onEvent(listOf(touch(1, 260f, 500f, 78), touch(2, 540f, 500f, 78)), false) as TouchAction.Transform
        assertEquals(280f / 240f, step.zoom, 0.001f)
        assertEquals(Offset(400f, 500f), step.to)
        a.onEvent(listOf(touch(1, 260f, 500f, 90, pressed = false), touch(2, 540f, 500f, 90)), false)
        assertEquals(TouchAction.EndTransform, a.onEvent(listOf(touch(2, 540f, 500f, 100, pressed = false)), false))
    }

    @Test
    fun aRestingThumbIsIgnoredAndTheOtherFingerScrolls() {
        val a = arbiter()
        a.onEvent(listOf(touch(1, 10f, 600f, 0)), false) // thumb on the edge
        a.onEvent(listOf(touch(1, 10f, 600f, 400), touch(2, 500f, 900f, 400)), false)
        var moved = 0f
        for (i in 1..10) {
            val action = a.onEvent(listOf(touch(1, 10f, 601f, 400 + i * 16L), touch(2, 500f, 900f - i * 30, 400 + i * 16L)), false)
            assertTrue("never a pinch: $action", action !is TouchAction.Transform)
            if (action is TouchAction.Pan) moved += action.delta.y
        }
        assertEquals(-300f, moved, 1f)
        val end = a.onEvent(listOf(touch(1, 10f, 601f, 600), touch(2, 500f, 600f, 600, pressed = false)), false)
        assertTrue(end is TouchAction.EndPan)
    }

    @Test
    fun aThumbLandingWhileScrollingDoesNotStopTheScroll() {
        val a = arbiter()
        a.onEvent(listOf(touch(1, 500f, 900f, 0)), false)
        for (i in 1..5) a.onEvent(listOf(touch(1, 500f, 900f - i * 30, i * 16L)), false)
        // The thumb lands and stays still; the scrolling finger goes on.
        for (i in 6..15) {
            val action = a.onEvent(listOf(touch(1, 500f, 900f - i * 30, i * 16L), touch(2, 10f, 600f, i * 16L)), false)
            assertEquals(TouchAction.Pass, action)
        }
    }

    @Test
    fun whileTheStylusWritesThePalmIsBlocked() {
        val a = arbiter()
        a.onEvent(listOf(pen(9, 300f, 300f, 0)), drawing = true)
        assertEquals(TouchAction.Block, a.onEvent(listOf(pen(9, 310f, 305f, 16), touch(1, 600f, 900f, 16)), drawing = true))
        assertEquals(TouchAction.Block, a.onEvent(listOf(pen(9, 320f, 310f, 32), touch(1, 640f, 860f, 32), touch(2, 700f, 950f, 32)), drawing = true))
    }

    @Test
    fun readingWithAStylusIsLikeAFinger() {
        val a = arbiter()
        assertEquals(TouchAction.Pass, a.onEvent(listOf(pen(9, 300f, 300f, 0)), drawing = false))
        assertEquals(TouchAction.Pass, a.onEvent(listOf(pen(9, 300f, 200f, 16)), drawing = false))
    }

    @Test
    fun aSecondFingerJoiningAPinchDoesNotJump() {
        val a = arbiter()
        a.onEvent(listOf(touch(1, 300f, 500f, 0), touch(2, 500f, 500f, 0)), false)
        a.onEvent(listOf(touch(1, 280f, 500f, 16), touch(2, 520f, 500f, 16)), false)
        val joined = a.onEvent(listOf(touch(1, 280f, 500f, 32), touch(2, 520f, 500f, 32), touch(3, 400f, 700f, 32)), false) as TouchAction.Transform
        assertEquals(1f, joined.zoom)
        assertEquals(joined.from, joined.to)
    }

    @Test
    fun aTouchRightAfterTheStylusNextToItIsThePalmButFarAwayItScrolls() {
        val a = TouchArbiter(slop, palmReach = 200f)
        a.onEvent(listOf(pen(9, 300f, 300f, 0)), drawing = true)
        a.onEvent(listOf(pen(9, 320f, 310f, 16, pressed = false)), drawing = true)
        assertEquals(TouchAction.Block, a.onEvent(listOf(touch(1, 380f, 420f, 100)), drawing = true))
        assertEquals(TouchAction.Block, a.onEvent(listOf(touch(1, 380f, 300f, 120)), drawing = true))
        a.onEvent(listOf(touch(1, 380f, 300f, 140, pressed = false)), drawing = true)
        // The other hand, far away, scrolls even right after writing.
        assertEquals(TouchAction.Pass, a.onEvent(listOf(touch(2, 700f, 1100f, 160)), drawing = true))
        assertEquals(TouchAction.Pass, a.onEvent(listOf(touch(2, 700f, 900f, 180)), drawing = true))
    }
}
