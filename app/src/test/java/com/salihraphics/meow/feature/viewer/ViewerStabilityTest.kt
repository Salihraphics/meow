package com.salihraphics.meow.feature.viewer

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.feature.annotation.AnnotationSession
import com.salihraphics.meow.feature.annotation.AnnotationTool
import com.salihraphics.meow.feature.annotation.PageAnnotationLayer
import com.salihraphics.meow.testing.FakeAnnotationRepository
import com.salihraphics.meow.testing.RawTouch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Regression tests for the reported viewer problems, on a tablet-sized viewport with pages of
 * mixed shapes. Before the fix, a zoom laid pages out at the new size only after positioning
 * them with the old one, so a pinch landed two pages further down; the sideways correction came
 * a frame late (a visible jump); a thumb holding the tablet turned scrolling into a zoom; and a
 * tap that stopped a fling could complete a double-tap zoom.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1200dp-mdpi")
class ViewerStabilityTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Loader(override val pageCount: Int = 40) : PdfPageLoader {
        override fun aspectRatio(index: Int) = if (index % 3 == 1) 1.4f else 0.707f
        override suspend fun load(index: Int, widthPx: Int) = ImageBitmap(8, 8)
    }

    private lateinit var state: PdfViewportState
    private lateinit var scope: CoroutineScope
    private val repo = FakeAnnotationRepository()
    private var ids = 0
    private val session = AnnotationSession("doc", repo, CoroutineScope(Dispatchers.Unconfined)) { "s${ids++}" }

    private fun show(start: ViewportSnapshot = ViewportSnapshot(3), rotation: Int = 0) {
        rule.setContent {
            scope = rememberCoroutineScope()
            state = rememberPdfViewportState("doc", { start }, {})
            val tools by session.tools.collectAsState()
            Box(Modifier.size(800.dp, 1200.dp)) {
                PdfPagesView(
                    Loader(), state, Modifier.size(800.dp, 1200.dp),
                    drawingActive = tools.tool != null, fingerDraws = tools.fingerDraws, rotation = rotation,
                ) { page -> PageAnnotationLayer(page, session, tools) }
            }
        }
        rule.waitForIdle()
    }

    /** Unclipped: a page partly off screen still reports its whole size. */
    private fun page(i: Int): Rect = rule.onNodeWithContentDescription("Sayfa ${i + 1} / 40").fetchSemanticsNode().let {
        Rect(it.positionInRoot, Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }

    private data class Spot(val page: Int, val x: Float, val y: Float)

    private fun spotAt(p: Offset): Spot {
        for (i in 0 until 40) {
            val r = runCatching { page(i) }.getOrNull() ?: continue
            if (p.y >= r.top && p.y < r.bottom) return Spot(i, (p.x - r.left) / r.width, (p.y - r.top) / r.height)
        }
        error("no page at $p")
    }

    private fun screenOf(s: Spot): Offset {
        val r = page(s.page)
        return Offset(r.left + s.x * r.width, r.top + s.y * r.height)
    }

    private fun assertStill(spot: Spot, at: Offset, tolerance: Float = 2f, what: String = "") {
        val now = screenOf(spot)
        assertEquals("$what x", at.x, now.x, tolerance)
        assertEquals("$what y", at.y, now.y, tolerance)
    }

    private val raw get() = RawTouch(rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))

    // ---- Zoom ---------------------------------------------------------------------------------

    @Test
    fun zoomingInAndOutKeepsTheFocusedSpotInPlace() {
        show()
        for ((zoom, focus) in listOf(2f to Offset(400f, 1000f), 1f to Offset(400f, 1000f), 3.5f to Offset(150f, 300f), 2.2f to Offset(650f, 900f))) {
            val spot = spotAt(focus)
            scope.launch { state.zoomTo(zoom, focus) }
            rule.waitForIdle()
            assertEquals(zoom, state.zoom, 0.001f)
            val now = screenOf(spot)
            assertEquals("y at $zoom", focus.y, now.y, 2f)
            // Sideways the spot stays too, unless the page's own edge stops the pan.
            val atEdge = state.horizontal.value == 0 || state.horizontal.value == state.horizontal.maxValue
            if (!atEdge) assertEquals("x at $zoom", focus.x, now.x, 2f)
            else assertTrue("x moved only because of the edge at $zoom", abs(focus.x - now.x) < 400f)
        }
        // At 50% a page is narrower than the screen and sits centred: only the height is kept.
        val focus = Offset(650f, 900f)
        val spot = spotAt(focus)
        scope.launch { state.zoomTo(0.5f, focus) }
        rule.waitForIdle()
        assertEquals(focus.y, screenOf(spot).y, 2f)
        val r = page(spot.page)
        assertEquals("centred", 400f, r.center.x, 1f)
    }

    @Test
    fun pinchingKeepsTheSpotBetweenTheFingersAndNeverJumpsPages() {
        show(ViewportSnapshot(3, zoom = 0.5f))
        val centre = Offset(400f, 700f)
        val spot = spotAt(centre)
        rule.onRoot().performTouchInput {
            pinch(centre - Offset(100f, 0f), centre - Offset(300f, 0f), centre + Offset(100f, 0f), centre + Offset(300f, 0f), 400)
        }
        rule.waitForIdle()
        assertTrue("zoomed in", state.zoom > 1f)
        assertStill(spot, centre, what = "after pinching in")

        val spot2 = spotAt(centre)
        rule.onRoot().performTouchInput {
            pinch(centre - Offset(300f, 0f), centre - Offset(80f, 0f), centre + Offset(300f, 0f), centre + Offset(80f, 0f), 400)
        }
        rule.waitForIdle()
        assertStill(spot2, centre, what = "after pinching out")
    }

    @Test
    fun twoFingersMovingTogetherPanTheDocumentWithThem() {
        show(ViewportSnapshot(5, zoom = 1.5f))
        val start = Offset(400f, 800f)
        val spot = spotAt(start)
        rule.onRoot().performTouchInput {
            pinch(start - Offset(150f, 0f), start - Offset(150f, 300f), start + Offset(150f, 0f), start + Offset(150f, -300f), 500)
        }
        rule.waitForIdle()
        assertEquals("a pan doesn't zoom", 1.5f, state.zoom, 0.01f)
        assertStill(spot, start - Offset(0f, 300f), tolerance = 3f, what = "followed the fingers")
    }

    @Test
    fun theFirstFrameAfterAZoomIsAlreadyInPlace() {
        show()
        val focus = Offset(600f, 600f)
        val spot = spotAt(focus)
        rule.mainClock.autoAdvance = false
        scope.launch { state.zoomTo(2f, focus) }
        rule.mainClock.advanceTimeByFrame()
        assertStill(spot, focus, what = "first frame")
        rule.mainClock.advanceTimeBy(100)
        assertStill(spot, focus, what = "settled")
    }

    @Test
    fun zoomStaysWithinItsLimits() {
        show()
        scope.launch { state.zoomTo(10f) }
        rule.waitForIdle()
        assertEquals(PdfViewportState.MAX_ZOOM, state.zoom)
        scope.launch { state.zoomTo(0.1f) }
        rule.waitForIdle()
        assertEquals(PdfViewportState.MIN_ZOOM, state.zoom)
    }

    @Test
    fun aTurnedPageKeepsTheFocusedSpotToo() {
        show(rotation = 90)
        val focus = Offset(400f, 500f)
        val spot = spotAt(focus)
        scope.launch { state.zoomTo(2.5f, focus) }
        rule.waitForIdle()
        assertStill(spot, focus)
    }

    // ---- Scrolling ----------------------------------------------------------------------------

    @Test
    fun aSlowDragMovesTheDocumentExactlyWithTheFinger() {
        show()
        val start = Offset(400f, 1000f)
        val spot = spotAt(start)
        rule.onRoot().performTouchInput {
            down(start)
            // Past the slop first, then 600px more in slow steps across page boundaries.
            moveTo(start - Offset(0f, 20f))
            repeat(30) { moveTo(start - Offset(0f, 20f + 20f * (it + 1))); advanceEventTime(40) }
            advanceEventTime(300)
            up()
        }
        rule.waitForIdle()
        assertStill(spot, start - Offset(0f, 620f), tolerance = 21f, what = "after dragging") // the slop isn't scrolled
    }

    @Test
    fun aRestingThumbNeitherZoomsNorBlocksScrolling() {
        show()
        val before = state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset
        val spot = spotAt(Offset(500f, 900f))
        rule.onRoot().performTouchInput {
            down(0, Offset(10f, 600f)) // thumb holding the edge
            advanceEventTime(500)
            down(1, Offset(500f, 900f))
            repeat(10) { moveTo(1, Offset(500f, 900f - 40f * (it + 1))); advanceEventTime(16) }
            advanceEventTime(300)
            up(1); up(0)
        }
        rule.waitForIdle()
        assertEquals("zoom", 1f, state.zoom, 0.001f)
        assertTrue("the list moved", (state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset) != before)
        assertStill(spot, Offset(500f, 500f), tolerance = 2f, what = "followed the finger")
    }

    @Test
    fun aTapThatStopsAFlingIsNotHalfOfADoubleTap() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { swipeUp(startY = 1100f, endY = 200f, durationMillis = 80) }
        rule.mainClock.advanceTimeBy(64)
        assertTrue("still flinging", state.list.isScrollInProgress)
        rule.onRoot().performTouchInput { click(Offset(400f, 600f)) }
        rule.mainClock.advanceTimeBy(100)
        rule.onRoot().performTouchInput { click(Offset(400f, 600f)) }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1f, state.zoom, 0.001f)
    }

    @Test
    fun aRealDoubleTapStillZoomsAroundTheTap() {
        show()
        val at = Offset(300f, 600f)
        val spot = spotAt(at)
        rule.onRoot().performTouchInput { doubleClick(at) }
        rule.waitForIdle()
        assertEquals(2f, state.zoom, 0.001f)
        assertStill(spot, at)
    }

    // ---- Stylus, palm, fingers ------------------------------------------------------------

    @Test
    fun theStylusWritesWhileAPalmRestsAndThePalmDoesNothing() {
        show()
        session.selectTool(AnnotationTool.PEN)
        rule.waitForIdle()
        val position = state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset
        val touch = raw
        touch.down(1, 600f, 1000f, 1_000) // palm
        touch.move(1, 610f, 980f, 1_010)
        touch.down(7, 200f, 300f, 1_020, stylus = true)
        var t = 1_020L
        for (i in 1..12) {
            t += 8
            touch.move(7, 200f + i * 15f, 300f + i * 4f, t)
            touch.move(1, 610f + i * 3f, 980f - i * 8f, t + 1) // the palm drifts too
        }
        touch.up(7, t + 10)
        touch.up(1, t + 20)
        rule.waitForIdle()
        assertEquals("one stroke, from the stylus only", 1, repo.all.size)
        assertEquals("the palm didn't scroll", position, state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset)
        assertEquals(1f, state.zoom, 0.001f)
    }

    @Test
    fun aHandComingToRestRightAfterAStrokeIsStillThePalm() {
        show()
        session.selectTool(AnnotationTool.PEN)
        rule.waitForIdle()
        val end = raw.stylusLine(200f, 300f, 380f, 340f, start = 1_000)
        val position = state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset
        val touch = raw
        // Lands 100 ms after the stylus lifted, beside where it wrote, and slides.
        touch.down(1, 450f, 420f, end + 100)
        repeat(8) { touch.move(1, 450f + it * 4f, 420f - it * 20f, end + 116 + it * 16L) }
        touch.up(1, end + 300)
        rule.waitForIdle()
        assertEquals(position, state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset)
        assertEquals(1, repo.all.size)
    }

    @Test
    fun afterTheStylusIsUsedOneFingerScrollsInsteadOfDrawing() {
        show()
        session.selectTool(AnnotationTool.PEN)
        rule.waitForIdle()
        raw.stylusLine(200f, 300f, 380f, 340f, start = 1_000)
        rule.waitForIdle()
        assertEquals(1, repo.all.size)
        val before = state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset
        rule.onRoot().performTouchInput { swipeUp(startY = 1000f, endY = 600f, durationMillis = 400) }
        rule.waitForIdle()
        assertEquals("the finger left no ink", 1, repo.all.size)
        assertTrue("the finger scrolled", (state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset) != before)
    }

    @Test
    fun withoutAStylusOneFingerDrawsAndAPinchLeavesNoInk() {
        show()
        session.selectTool(AnnotationTool.PEN)
        rule.waitForIdle()
        rule.onRoot().performTouchInput { swipe(Offset(200f, 300f), Offset(400f, 360f), 200) }
        rule.waitForIdle()
        assertEquals("one finger draws", 1, repo.all.size)
        val c = Offset(400f, 700f)
        rule.onRoot().performTouchInput { pinch(c - Offset(80f, 0f), c - Offset(250f, 0f), c + Offset(80f, 0f), c + Offset(250f, 0f), 400) }
        rule.waitForIdle()
        assertEquals("the pinch drew nothing", 1, repo.all.size)
        assertTrue("and zoomed", state.zoom > 1.1f)
    }

    @Test
    fun theStylusEraserEndErasesWhateverToolIsSelected() {
        show()
        session.selectTool(AnnotationTool.PEN)
        rule.waitForIdle()
        raw.stylusLine(200f, 300f, 380f, 340f, start = 1_000)
        rule.waitForIdle()
        val stroke = repo.all.keys.single()
        // MotionEvent's eraser tool type is the stylus's back end.
        val view = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        fun send(action: Int, x: Float, t: Long) {
            val p = android.view.MotionEvent.PointerProperties().apply { id = 3; toolType = android.view.MotionEvent.TOOL_TYPE_ERASER }
            val c = android.view.MotionEvent.PointerCoords().apply { this.x = x; y = 320f; pressure = 1f }
            val e = android.view.MotionEvent.obtain(2_000, t, action, 1, arrayOf(p), arrayOf(c), 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_STYLUS, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }
        send(android.view.MotionEvent.ACTION_DOWN, 250f, 2_000)
        send(android.view.MotionEvent.ACTION_MOVE, 300f, 2_016)
        send(android.view.MotionEvent.ACTION_UP, 300f, 2_032)
        rule.waitForIdle()
        assertTrue(stroke in repo.deleted)
    }

    // ---- Ink stays on the document --------------------------------------------------------

    @Test
    fun inkIsStoredAtTheDocumentSpotUnderThePenAtAnyZoomAndPan() {
        show(ViewportSnapshot(4, pageOffsetFraction = 0.3f, zoom = 1.6f, horizontalFraction = 0.4f))
        session.selectTool(AnnotationTool.PEN)
        rule.waitForIdle()
        val a = Offset(300f, 500f)
        val b = Offset(420f, 540f)
        val spotA = spotAt(a)
        val spotB = spotAt(b)
        raw.stylusLine(a.x, a.y, b.x, b.y, start = 1_000)
        rule.waitForIdle()
        val stroke = repo.all.values.single()
        assertEquals(spotA.page, stroke.pageIndex)
        assertEquals(spotA.x, stroke.points[0], 0.002f)
        assertEquals(spotA.y, stroke.points[1], 0.002f)
        assertEquals(spotB.x, stroke.points[stroke.points.size - 2], 0.002f)
        assertEquals(spotB.y, stroke.points[stroke.points.size - 1], 0.002f)

        // Zoomed far in elsewhere and back: the same page fractions, so the same printed spot.
        scope.launch { state.zoomTo(3.5f, Offset(100f, 100f)) }
        rule.waitForIdle()
        scope.launch { state.zoomTo(1f) }
        rule.waitForIdle()
        assertTrue(repo.all.values.single().points.contentEquals(stroke.points))
    }

    @Test
    fun pressureIsKeptOnlyWhenTheStylusReportsIt() {
        show()
        session.selectTool(AnnotationTool.PEN)
        rule.waitForIdle()
        raw.stylusLine(200f, 300f, 380f, 340f, start = 1_000) // constant pressure: a plain stylus
        rule.waitForIdle()
        assertEquals(null, repo.all.values.last().pressures)
        raw.stylusLine(200f, 500f, 380f, 540f, start = 2_000, pressures = { 0.2f + it * 0.05f })
        rule.waitForIdle()
        val pressures = repo.all.values.last().pressures!!
        assertTrue("rising pressure recorded", pressures.first() < pressures.last())
        assertTrue(abs(pressures.size * 2 - repo.all.values.last().points.size) == 0)
    }

    // ---- Shape correction -----------------------------------------------------------------

    @Test
    fun aShakyStylusLineAtTwoHundredPercentBecomesStraightOnTheSameSpotAndUndoesToTheOriginal() {
        show(ViewportSnapshot(6, pageOffsetFraction = 0.2f, zoom = 2f, horizontalFraction = 0.3f))
        session.selectTool(AnnotationTool.PEN)
        session.setShapeCorrection(true)
        rule.waitForIdle()
        val a = Offset(150f, 500f)
        val b = Offset(650f, 506f)
        val spotA = spotAt(a)
        val spotB = spotAt(b)
        val touch = raw
        touch.down(7, a.x, a.y, 1_000, stylus = true)
        for (i in 1..30) touch.move(7, a.x + (b.x - a.x) * i / 30, a.y + (b.y - a.y) * i / 30 + if (i % 2 == 0) 2f else -2f, 1_000L + i * 8)
        touch.up(7, 1_260)
        rule.waitForIdle()
        val clean = session.strokes(spotA.page).value!!.single()
        assertEquals("a straight line: two points", 4, clean.points.size)
        assertEquals("level", clean.points[1], clean.points[3], 0f)
        assertEquals(spotA.x, clean.points[0], 0.003f)
        assertEquals(spotA.y, clean.points[1], 0.003f)
        assertEquals(spotB.x, clean.points[2], 0.003f)

        session.undo()
        val original = session.strokes(spotA.page).value!!.single()
        assertTrue("undo brings back the hand-drawn line", original.points.size > 4)
        assertEquals(spotA.x, original.points[0], 0.003f)
    }
}
