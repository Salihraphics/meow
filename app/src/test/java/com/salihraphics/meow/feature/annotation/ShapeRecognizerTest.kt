package com.salihraphics.meow.feature.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Strokes as a hand draws them on an A4 page (aspect = height / width = 1.414): small tremor,
 * uneven speed, ends that overshoot. Coordinates are page fractions, like stored strokes.
 */
class ShapeRecognizerTest {
    private val aspect = 1.414f
    private val rnd = Random(7)

    /** A path in square units (page widths) sampled unevenly, with [tremor] noise, as page fractions. */
    private fun stroke(n: Int = 60, tremor: Float = 0.0015f, f: (Float) -> Pair<Float, Float>): FloatArray {
        val out = FloatArray(n * 2)
        var t = 0f
        for (i in 0 until n) {
            // Uneven speed: slower at the ends.
            t = i / (n - 1f)
            val eased = 0.5f - 0.5f * cos(PI.toFloat() * t)
            val (x, y) = f(eased)
            out[2 * i] = x + (rnd.nextFloat() - 0.5f) * 2 * tremor
            out[2 * i + 1] = (y + (rnd.nextFloat() - 0.5f) * 2 * tremor) / aspect
        }
        return out
    }

    private fun recognize(points: FloatArray, scale: Float = 1f) = ShapeRecognizer.recognize(points, aspect, scale)

    private fun xs(p: FloatArray) = p.filterIndexed { i, _ -> i % 2 == 0 }
    private fun ys(p: FloatArray) = p.filterIndexed { i, _ -> i % 2 == 1 }.map { it * aspect }

    // ---- Lines ------------------------------------------------------------------------------

    @Test
    fun aShakyHorizontalLineBecomesLevelAndKeepsItsEnds() {
        val drawn = stroke { t -> (0.2f + 0.5f * t) to (0.4f + 0.004f * sin(t * 9)) }
        val shape = recognize(drawn)!!
        assertEquals(ShapeKind.LINE, shape.kind)
        assertEquals(4, shape.points.size)
        assertEquals(shape.points[1], shape.points[3], 0f) // exactly level
        assertEquals(drawn[0], shape.points[0], 1e-6f) // starts where the pen started
        assertEquals(drawn[drawn.size - 2], shape.points[2], 1e-6f)
    }

    @Test
    fun aVerticalLineBecomesUpright() {
        val shape = recognize(stroke { t -> (0.5f + 0.003f * sin(t * 7)) to (0.2f + 0.4f * t) })!!
        assertEquals(ShapeKind.LINE, shape.kind)
        assertEquals(shape.points[0], shape.points[2], 0f)
    }

    @Test
    fun aDiagonalLineStaysDiagonalAndStraight() {
        val shape = recognize(stroke { t -> (0.2f + 0.4f * t + 0.003f * sin(t * 11)) to (0.3f + 0.3f * t) })!!
        assertEquals(ShapeKind.LINE, shape.kind)
        assertEquals(2, shape.points.size / 2)
        val angle = Math.toDegrees(kotlin.math.atan2(((shape.points[3] - shape.points[1]) * aspect).toDouble(), (shape.points[2] - shape.points[0]).toDouble()))
        assertEquals(36.87, angle, 1.5)
    }

    // ---- Circles and ellipses ---------------------------------------------------------------

    @Test
    fun anImperfectCircleBecomesARoundCircleOfAboutTheSameSizeAndPlace() {
        val drawn = stroke(n = 90, tremor = 0.002f) { t ->
            val a = 2 * PI.toFloat() * (t * 1.06f) // overshoots the start a little
            val r = 0.1f * (1f + 0.05f * sin(3 * a)) // wobbly radius
            (0.5f + r * cos(a)) to (0.6f + 0.95f * r * sin(a))
        }
        val shape = recognize(drawn)!!
        assertEquals(ShapeKind.CIRCLE, shape.kind)
        // Centre from the extremes: the outline repeats its first point to close.
        val cx = (xs(shape.points).max() + xs(shape.points).min()) / 2.0
        val cy = (ys(shape.points).max() + ys(shape.points).min()) / 2.0
        assertEquals(0.5, cx, 0.01)
        assertEquals(0.6, cy, 0.01)
        val radii = xs(shape.points).indices.map { hypot(xs(shape.points)[it] - cx, ys(shape.points)[it] - cy) }
        assertTrue("round", radii.max() - radii.min() < 0.002)
        assertEquals(0.1, radii.average(), 0.012)
    }

    @Test
    fun aTiltedEllipseStaysAnEllipseWithItsTilt() {
        val tilt = 0.5f
        val shape = recognize(stroke(n = 90) { t ->
            val a = 2 * PI.toFloat() * t * 1.03f
            val u = 0.18f * cos(a); val v = 0.08f * sin(a)
            (0.5f + u * cos(tilt) - v * sin(tilt)) to (0.6f + u * sin(tilt) + v * cos(tilt))
        })!!
        assertEquals(ShapeKind.ELLIPSE, shape.kind)
        val x = xs(shape.points); val y = ys(shape.points)
        val far = x.indices.maxBy { hypot(x[it] - 0.5f, y[it] - 0.6f) }
        val angle = kotlin.math.atan2((y[far] - 0.6f).toDouble(), (x[far] - 0.5f).toDouble())
        val folded = ((angle - tilt) % PI + PI) % PI
        assertTrue("long axis keeps its tilt", folded < 0.12 || folded > PI - 0.12)
        assertEquals(0.18, hypot(x[far] - 0.5f, y[far] - 0.6f).toDouble(), 0.02)
    }

    // ---- Rectangles, triangles, arrows ------------------------------------------------------

    private fun polygonStroke(vararg corners: Pair<Float, Float>, n: Int = 100, close: Boolean = true): FloatArray {
        val path = if (close) corners.toList() + corners.first() else corners.toList()
        return stroke(n = n, tremor = 0.0015f) { t ->
            val seg = (t * (path.size - 1)).coerceAtMost(path.size - 1.0001f)
            val i = seg.toInt(); val f = seg - i
            (path[i].first + (path[i + 1].first - path[i].first) * f) to (path[i].second + (path[i + 1].second - path[i].second) * f)
        }
    }

    @Test
    fun aHandDrawnRectangleBecomesARectangleWithLevelSides() {
        val shape = recognize(polygonStroke(0.2f to 0.3f, 0.6f to 0.302f, 0.598f to 0.55f, 0.201f to 0.548f))!!
        assertEquals(ShapeKind.RECTANGLE, shape.kind)
        val x = xs(shape.points); val y = ys(shape.points)
        assertEquals(0.2, x.min().toDouble(), 0.01); assertEquals(0.6, x.max().toDouble(), 0.01)
        assertEquals(0.3, y.min().toDouble(), 0.01); assertEquals(0.55, y.max().toDouble(), 0.01)
        // Every point on one of the four sides.
        assertTrue(x.indices.all { i -> listOf(abs(x[i] - x.min()), abs(x[i] - x.max()), abs(y[i] - y.min()), abs(y[i] - y.max())).min() < 1e-4f })
    }

    @Test
    fun aTriangleBecomesATriangle() {
        val shape = recognize(polygonStroke(0.3f to 0.6f, 0.5f to 0.3f, 0.7f to 0.6f))!!
        assertEquals(ShapeKind.TRIANGLE, shape.kind)
    }

    @Test
    fun aOneStrokeArrowBecomesAnArrow() {
        val shape = recognize(polygonStroke(0.2f to 0.5f, 0.6f to 0.5f, 0.52f to 0.44f, 0.6f to 0.5f, 0.52f to 0.56f, n = 120, close = false))!!
        assertEquals(ShapeKind.ARROW, shape.kind)
    }

    // ---- What must stay freehand -----------------------------------------------------------

    @Test
    fun handwritingIsNeverTurnedIntoShapes() {
        val letterSize = 0.02f // a letter on an A4 page
        val o = stroke(n = 40) { t -> val a = 2 * PI.toFloat() * t; (0.3f + letterSize * cos(a)) to (0.3f + letterSize * sin(a)) }
        val l = stroke(n = 20) { t -> 0.3f to (0.3f + 0.03f * t) }
        val e = stroke(n = 50) { t ->
            val a = 2 * PI.toFloat() * t * 0.85f
            (0.3f + letterSize * (if (t < 0.15f) t * 6 - 0.5f else cos(a))) to (0.3f + letterSize * (if (t < 0.15f) 0f else -sin(a)))
        }
        // A cursive word: loops going across the line.
        val cursive = stroke(n = 120) { t -> (0.2f + 0.15f * t + 0.012f * cos(t * 30)) to (0.4f + 0.012f * sin(t * 30)) }
        for ((name, s) in listOf("o" to o, "l" to l, "e" to e, "cursive" to cursive)) assertNull(name, recognize(s))
    }

    @Test
    fun handwritingOnANoteIsMeasuredAgainstThePageToo() {
        // A 0.25-page-wide note: a letter spans 0.12 of the note but only 0.03 of the page.
        val o = stroke(n = 40) { t -> val a = 2 * PI.toFloat() * t; (0.5f + 0.06f * cos(a)) to (0.3f + 0.06f * sin(a)) }
        assertNull(recognize(o, scale = 0.25f))
        assertNotNull("the same stroke on the page is a circle", recognize(o, scale = 1f))
    }

    @Test
    fun ambiguousMarksStayAsDrawn() {
        val arc = stroke(n = 60) { t -> val a = PI.toFloat() * 1.2f * t; (0.5f + 0.1f * cos(a)) to (0.5f + 0.1f * sin(a)) } // a "C"
        val zigzag = polygonStroke(0.2f to 0.3f, 0.3f to 0.4f, 0.4f to 0.3f, 0.5f to 0.4f, 0.6f to 0.3f, close = false)
        val spiral = stroke(n = 150) { t -> val a = 6 * PI.toFloat() * t; val r = 0.03f + 0.08f * t; (0.5f + r * cos(a)) to (0.5f + r * sin(a)) }
        val tick = polygonStroke(0.3f to 0.5f, 0.34f to 0.56f, 0.45f to 0.38f, close = false)
        val bigScribble = stroke(n = 100, tremor = 0.03f) { t -> (0.2f + 0.4f * t) to (0.4f + 0.2f * sin(t * 13)) }
        for ((name, s) in listOf("arc" to arc, "zigzag" to zigzag, "spiral" to spiral, "tick" to tick, "scribble" to bigScribble)) {
            assertNull(name, recognize(s))
        }
    }

    @Test
    fun shortStrokesAndDotsStayAsDrawn() {
        assertNull(recognize(floatArrayOf(0.5f, 0.5f)))
        assertNull(recognize(stroke(n = 10) { t -> (0.5f + 0.02f * t) to 0.5f }))
    }

    @Test
    fun shapesKeepTheirPlaceInPageFractionsWhateverTheZoomTheyWereDrawnAt() {
        // The same circle drawn at 100% and at 300% has the same page fractions (only the
        // number of samples differs), so the result is the same spot on the page.
        val at1 = stroke(n = 60) { t -> val a = 2 * PI.toFloat() * t * 1.02f; (0.5f + 0.1f * cos(a)) to (0.5f + 0.1f * sin(a)) }
        val at3 = stroke(n = 180) { t -> val a = 2 * PI.toFloat() * t * 1.02f; (0.5f + 0.1f * cos(a)) to (0.5f + 0.1f * sin(a)) }
        val a = recognize(at1)!!; val b = recognize(at3)!!
        assertEquals(xs(a.points).average(), xs(b.points).average(), 0.003)
        assertEquals(ys(a.points).average(), ys(b.points).average(), 0.003)
    }
}
