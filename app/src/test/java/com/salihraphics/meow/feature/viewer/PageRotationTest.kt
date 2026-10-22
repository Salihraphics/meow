package com.salihraphics.meow.feature.viewer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class PageRotationTest {
    private fun assertNear(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 1e-4f)
        assertEquals(expected.y, actual.y, 1e-4f)
    }

    @Test
    fun anglesAreQuarterTurnsClockwise() {
        assertEquals(0, PageRotation.normalize(360))
        assertEquals(270, PageRotation.normalize(-90))
        assertEquals(90, PageRotation.normalize(450))
        assertEquals(180, PageRotation.normalize(-180))
    }

    @Test
    fun aQuarterTurnSwapsTheShownShape() {
        val a4 = 595f / 842f
        assertEquals(a4, PageRotation.shownAspect(a4, 0), 1e-6f)
        assertEquals(842f / 595f, PageRotation.shownAspect(a4, 90), 1e-4f)
        assertEquals(a4, PageRotation.shownAspect(a4, 180), 1e-6f)
        assertEquals(1000 to 700, PageRotation.pageSize(700, 1000, 270))
        assertEquals(700 to 1000, PageRotation.pageSize(700, 1000, 180))
    }

    @Test
    fun theTopLeftCornerOfThePageGoesWhereTheTurnTakesIt() {
        // Page (0,0) is its top-left corner. Turned clockwise it is shown at the top right.
        assertNear(Offset(0f, 0f), PageRotation.toShown(Offset(0f, 0f), 1f, 1f, 0))
        assertNear(Offset(1f, 0f), PageRotation.toShown(Offset(0f, 0f), 1f, 1f, 90))
        assertNear(Offset(1f, 1f), PageRotation.toShown(Offset(0f, 0f), 1f, 1f, 180))
        assertNear(Offset(0f, 1f), PageRotation.toShown(Offset(0f, 0f), 1f, 1f, 270))
    }

    @Test
    fun screenToPageAndBackIsExactAtEveryAngleAndSize() {
        val points = listOf(Offset(0.1f, 0.2f), Offset(0.5f, 0.5f), Offset(0.93f, 0.07f), Offset(0.3f, 0.88f))
        for (angle in listOf(0, 90, 180, 270)) {
            for ((w, h) in listOf(400f to 566f, 1131f to 800f, 200f to 283f)) {
                for (p in points) {
                    val shown = PageRotation.toShown(p, w, h, angle)
                    assertNear(p, PageRotation.toPageFraction(shown, w, h, angle))
                }
            }
        }
    }

    @Test
    fun zoomDoesNotChangeWhereAPointIsOnThePage() {
        // The same spot of a turned page, shown at 50 %, 100 % and 400 %.
        val page = Offset(0.25f, 0.6f)
        for (zoom in listOf(0.5f, 1f, 4f)) {
            val w = 842f * zoom
            val h = 595f * zoom
            val shown = PageRotation.toShown(page, w, h, 90)
            assertNear(page, PageRotation.toPageFraction(shown, w, h, 90))
            assertNear(Offset(shown.x / zoom, shown.y / zoom), PageRotation.toShown(page, 842f, 595f, 90))
        }
    }
}
