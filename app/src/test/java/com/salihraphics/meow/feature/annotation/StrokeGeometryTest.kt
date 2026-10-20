package com.salihraphics.meow.feature.annotation

import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeGeometryTest {
    private fun stroke(vararg p: Float) = Stroke("s", "d", 0, StrokeTool.PEN, 0, 0.005f, p)

    @Test
    fun theSamePageSpotMapsToTheSameFractionAtAnyZoomOrOrientation() {
        // Middle of the page, top third: portrait phone, 2x zoom, landscape tablet.
        for ((w, h) in listOf(369f to 522f, 738f to 1044f, 736f to 1041f)) {
            val (x, y) = StrokeGeometry.toFraction(w * 0.5f, h * 0.33f, w, h)
            assertEquals(0.5f, x, 1e-6f)
            assertEquals(0.33f, y, 1e-6f)
        }
    }

    @Test
    fun pointsOutsideThePageAreClampedOntoIt() {
        assertEquals(0f to 1f, StrokeGeometry.toFraction(-40f, 900f, 300f, 400f))
    }

    @Test
    fun nearlyIdenticalPointsAreDropped() {
        val points = listOf(0.5f, 0.5f)
        assertFalse(StrokeGeometry.shouldAppend(points, 0.5005f, 0.5f, aspect = 0.7f))
        assertTrue(StrokeGeometry.shouldAppend(points, 0.51f, 0.5f, aspect = 0.7f))
    }

    @Test
    fun eraserHitsAlongTheLineNotJustAtPoints() {
        val diagonal = stroke(0.1f, 0.1f, 0.9f, 0.9f)
        // Midpoint of the line on a 400x400 page, with no point stored there.
        assertTrue(StrokeGeometry.hits(diagonal, 200f, 200f, radiusPx = 10f, width = 400f, height = 400f))
        assertFalse(StrokeGeometry.hits(diagonal, 300f, 100f, radiusPx = 10f, width = 400f, height = 400f))
    }

    @Test
    fun eraserRadiusIsInScreenPixelsSoZoomDoesNotChangeItsFeel() {
        val dot = stroke(0.5f, 0.5f)
        // 15px from the dot: inside a 20px eraser at 1x and at 2x zoom alike.
        assertTrue(StrokeGeometry.hits(dot, 215f, 200f, 20f, 400f, 400f))
        assertTrue(StrokeGeometry.hits(dot, 415f, 400f, 20f, 800f, 800f))
        assertFalse(StrokeGeometry.hits(dot, 250f, 200f, 20f, 400f, 400f))
    }
}
