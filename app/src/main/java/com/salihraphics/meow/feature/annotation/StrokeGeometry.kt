package com.salihraphics.meow.feature.annotation

import com.salihraphics.meow.domain.model.Stroke
import kotlin.math.hypot

/** Conversions between on-screen page pixels and page fractions (0..1), the stored form. */
object StrokeGeometry {
    /**
     * Points closer than this (as a fraction of page width) are dropped while drawing; keeps long
     * strokes small without visible loss.
     */
    const val MIN_POINT_SPACING = 0.0015f

    /** A touch at [x],[y] px on a page drawn [width]×[height] px, clamped onto the page. */
    fun toFraction(x: Float, y: Float, width: Float, height: Float): Pair<Float, Float> =
        (x / width).coerceIn(0f, 1f) to (y / height).coerceIn(0f, 1f)

    /** Whether a new point is at least [spacing] (a fraction of the width) from the previous one. */
    fun shouldAppend(points: List<Float>, x: Float, y: Float, aspect: Float, spacing: Float = MIN_POINT_SPACING): Boolean {
        if (points.size < 2) return true
        val dx = x - points[points.size - 2]
        val dy = (y - points[points.size - 1]) * aspect
        return hypot(dx, dy) >= spacing
    }

    /**
     * Does a circle at ([x], [y]) px with [radiusPx] touch [stroke], on a page drawn at
     * [width]×[height] px? Measured in pixels so the eraser feels the same at every zoom.
     */
    fun hits(stroke: Stroke, x: Float, y: Float, radiusPx: Float, width: Float, height: Float): Boolean {
        val p = stroke.points
        val reach = radiusPx + stroke.width * width / 2f
        if (p.size == 2) return hypot(p[0] * width - x, p[1] * height - y) <= reach
        var i = 0
        while (i + 3 < p.size) {
            if (distanceToSegment(x, y, p[i] * width, p[i + 1] * height, p[i + 2] * width, p[i + 3] * height) <= reach) return true
            i += 2
        }
        return false
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
