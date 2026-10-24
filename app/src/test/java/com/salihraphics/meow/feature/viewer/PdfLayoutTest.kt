package com.salihraphics.meow.feature.viewer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The page geometry and the zoom maths the viewer lays pages out with. */
class PdfLayoutTest {
    /** Portrait A4, landscape and a tall receipt-like page, mixed. */
    private val aspects = floatArrayOf(0.707f, 1.414f, 0.707f, 0.4f, 0.707f)
    private fun layout(zoom: Float, width: Int = 1600, pages: Int = 60, turn: Int = 0) =
        PdfLayout(width, 1520, 24, zoom, pages) { PageRotation.shownAspect(aspects[it % aspects.size], turn) }

    @Test
    fun pagesAreStackedWithOneGapBetweenThem() {
        val l = layout(1f)
        assertEquals(1520 - 0, l.pageWidth + 0) // 760dp at 2x fits; base = min(1600, 1520 + 48) = 1568
        assertEquals(24, l.pageTop(0))
        for (i in 1 until 60) assertEquals(l.pageTop(i - 1) + l.pageHeight(i - 1) + 24, l.pageTop(i))
        assertEquals(l.pageTop(59) + l.pageHeight(59) + 24, l.contentHeight)
        assertEquals(2150, l.pageHeight(0)) // 1520 / 0.707
    }

    @Test
    fun listPositionIsTheInverseOfScrollY() {
        val l = layout(1.7f)
        for (y in listOf(0, 1, 23, 24, 25, 5000, 12345, l.contentHeight - 1)) {
            val (index, offset) = l.listPosition(y)
            assertTrue("offset ≥ 0", offset >= 0)
            assertTrue("offset inside the slot", offset < l.pageHeight(index) + 24 || index == 59)
            assertEquals(y, l.scrollY(index, offset))
        }
    }

    @Test
    fun zoomingKeepsTheDocumentSpotUnderTheFocusExactly() {
        val rnd = Random(3)
        repeat(200) {
            val fromZoom = 0.5f + rnd.nextFloat() * 3.5f
            val toZoom = 0.5f + rnd.nextFloat() * 3.5f
            val old = layout(fromZoom)
            val new = layout(toZoom)
            val viewportH = 2000
            val scroll = ScrollPosition((rnd.nextFloat() * (old.contentHeight - viewportH)).coerceAtLeast(0f), rnd.nextFloat() * old.maxScrollX)
            val focus = Offset(rnd.nextFloat() * 1600, rnd.nextFloat() * viewportH)
            val spot = ZoomMath.pointAt(old, scroll, focus)
            val after = ZoomMath.anchor(old, scroll, focus, new, focus, viewportH)
            val shown = ZoomMath.screenOf(new, after, spot)
            // Unless an edge of the document stops it, the spot stays exactly under the focus.
            val clampedY = after.y == 0f || after.y == (new.contentHeight - viewportH).toFloat()
            val clampedX = after.x == 0f || after.x == new.maxScrollX.toFloat()
            if (!clampedX) assertEquals("x at $fromZoom→$toZoom", focus.x, shown.x, 0.01f)
            if (!clampedY) assertEquals("y at $fromZoom→$toZoom", focus.y, shown.y, 0.05f)
        }
    }

    @Test
    fun aPinchCanMoveTheSpotFromUnderOneFingerPairToAnother() {
        val old = layout(1f); val new = layout(2f)
        val scroll = ScrollPosition(30000f, 0f)
        val spot = ZoomMath.pointAt(old, scroll, Offset(800f, 900f))
        val after = ZoomMath.anchor(old, scroll, Offset(800f, 900f), new, Offset(600f, 1100f), 2000)
        val shown = ZoomMath.screenOf(new, after, spot)
        assertEquals(600f, shown.x, 0.01f)
        assertEquals(1100f, shown.y, 0.05f)
    }

    @Test
    fun theDocumentsEdgesStopTheScrollInsteadOfGoingNegative() {
        val old = layout(2f); val new = layout(0.5f)
        val top = ZoomMath.anchor(old, ScrollPosition(10f, 50f), Offset(800f, 1900f), new, Offset(800f, 1900f), 2000)
        assertEquals(0f, top.y)
        assertEquals(0f, top.x) // at 50% the page is narrower than the screen: nothing to pan
        val end = ZoomMath.anchor(old, ScrollPosition((old.contentHeight - 2000).toFloat(), 0f), Offset(800f, 100f), new, Offset(800f, 100f), 2000)
        assertEquals((new.contentHeight - 2000).toFloat(), end.y)
    }

    @Test
    fun quarterTurnedPagesUseTheirTurnedShape() {
        val upright = layout(1f)
        val turned = layout(1f, turn = 90)
        assertEquals(upright.pageWidth, turned.pageWidth)
        assertEquals(1075, turned.pageHeight(0)) // a portrait page on its side: 1520 × 0.707
        assertEquals(upright.pageHeight(1), turned.pageHeight(0), 1) // like a landscape page
        assertEquals(upright.pageHeight(0), turned.pageHeight(1), 2) // and the landscape page stands up
    }

    @Test
    fun smallPagesAreCentredAndCannotBePannedSideways() {
        val l = layout(0.5f)
        assertEquals(1600, l.listWidth)
        assertEquals(0, l.maxScrollX)
        assertEquals((1600 - l.pageWidth) / 2, l.pageLeft, 1)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        assertTrue("expected $expected ± $tolerance, was $actual", kotlin.math.abs(expected - actual) <= tolerance)
}
