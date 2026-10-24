package com.salihraphics.meow.feature.annotation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ShapeKind { LINE, CIRCLE, ELLIPSE, RECTANGLE, TRIANGLE, ARROW }

/** A clean shape replacing a hand-drawn stroke; [points] in the same fractions as the stroke. */
class RecognizedShape(val kind: ShapeKind, val points: FloatArray)

/**
 * Turns a hand-drawn stroke into a clean line, circle, ellipse, rectangle, triangle or arrow
 * when it clearly is one, and otherwise returns null so the stroke stays exactly as drawn.
 * Runs on the finished stroke only (never while drawing), on the device, in microseconds.
 *
 * Deliberately cautious: shapes must be larger than handwriting ([MIN_SHAPE] of the page
 * width), and each kind has to fit well. Letters, loops and scribbles stay freehand.
 *
 * Points are fractions of a box (a page, or a note) [aspect] = height / width; [boxScale] is the
 * box's width in page widths (1 for a page, the note's width for a note), so the size limits
 * are the same on a note as on the page.
 */
object ShapeRecognizer {
    /** Shapes smaller than this (in page widths) are taken for handwriting. */
    const val MIN_SHAPE = 0.045f
    private const val MIN_LINE = 0.05f

    private class P(val x: Float, val y: Float)

    fun recognize(points: FloatArray, aspect: Float, boxScale: Float = 1f): RecognizedShape? {
        if (points.size < 8 || aspect <= 0f) return null
        // Square units: x in box widths, y in box widths too.
        val pts = List(points.size / 2) { P(points[2 * it], points[2 * it + 1] * aspect) }
        val length = pathLength(pts)
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val extent = max(maxX - minX, maxY - minY) * boxScale
        if (extent < MIN_SHAPE.coerceAtMost(MIN_LINE)) return null

        val shape = line(pts, length, boxScale)
            ?: closed(pts, length, boxScale, minX, maxX, minY, maxY)
            ?: arrow(pts, length, boxScale)
            ?: return null
        // Back to the box's own fractions.
        val out = FloatArray(shape.second.size * 2)
        shape.second.forEachIndexed { i, p ->
            out[2 * i] = p.x.coerceIn(0f, 1f)
            out[2 * i + 1] = (p.y / aspect).coerceIn(0f, 1f)
        }
        return RecognizedShape(shape.first, out)
    }

    // ---- Lines ---------------------------------------------------------------------------------

    private fun line(pts: List<P>, length: Float, scale: Float): Pair<ShapeKind, List<P>>? {
        val a = pts.first()
        var b = pts.last()
        val chord = dist(a, b)
        if (chord * scale < MIN_LINE || chord / length < 0.92f) return null
        val worst = pts.maxOf { distToSegment(it, a, b) }
        if (worst > max(0.006f / scale, 0.06f * chord)) return null
        // Nearly level or upright lines are made exactly so, keeping the start.
        val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()))
        val toAxis = ((angle % 90.0) + 90.0) % 90.0
        if (toAxis <= SNAP_DEGREES || toAxis >= 90.0 - SNAP_DEGREES) {
            val horizontal = abs(b.x - a.x) >= abs(b.y - a.y)
            b = if (horizontal) P(b.x, a.y) else P(a.x, b.y)
        }
        return ShapeKind.LINE to listOf(a, b)
    }

    // ---- Closed shapes -------------------------------------------------------------------------

    private fun closed(pts: List<P>, length: Float, scale: Float, minX: Float, maxX: Float, minY: Float, maxY: Float): Pair<ShapeKind, List<P>>? {
        val w = maxX - minX
        val h = maxY - minY
        if (min(w, h) * scale < MIN_SHAPE * 0.6f || max(w, h) * scale < MIN_SHAPE) return null
        val gap = dist(pts.first(), pts.last())
        // It has to come back to where it started and go round (not a hook or a "c").
        if (gap > 0.22f * max(w, h) || length < 1.8f * max(w, h)) return null
        return ellipse(pts, w, h) ?: polygon(pts, length, w, h)
    }

    private fun ellipse(pts: List<P>, w: Float, h: Float): Pair<ShapeKind, List<P>>? {
        // Centre and axes from the points weighted by the path around them, so slow, dense
        // parts of the drawing don't pull the fit.
        val weights = FloatArray(pts.size) { i ->
            val prev = pts[(i - 1).coerceAtLeast(0)]
            val next = pts[(i + 1).coerceAtMost(pts.lastIndex)]
            dist(prev, next) / 2f
        }
        val total = weights.sum().takeIf { it > 0f } ?: return null
        val cx = pts.indices.sumOf { (pts[it].x * weights[it]).toDouble() }.toFloat() / total
        val cy = pts.indices.sumOf { (pts[it].y * weights[it]).toDouble() }.toFloat() / total
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        pts.indices.forEach {
            val dx = pts[it].x - cx; val dy = pts[it].y - cy
            sxx += weights[it] * dx * dx; syy += weights[it] * dy * dy; sxy += weights[it] * dx * dy
        }
        sxx /= total; syy /= total; sxy /= total
        val phi = 0.5 * atan2(2 * sxy, sxx - syy)
        val c = cos(phi).toFloat(); val s = sin(phi).toFloat()
        val us = pts.map { (it.x - cx) * c + (it.y - cy) * s }
        val vs = pts.map { -(it.x - cx) * s + (it.y - cy) * c }
        // Radii from the extremes along each axis (averaged), which the drawn outline reaches.
        val a = (us.max() - us.min()) / 2f
        val b = (vs.max() - vs.min()) / 2f
        if (a <= 0f || b <= 0f) return null
        val ucx = (us.max() + us.min()) / 2f
        val vcx = (vs.max() + vs.min()) / 2f
        val errors = pts.indices.map { abs(hypot((us[it] - ucx) / a, (vs[it] - vcx) / b) - 1f) }
        val mean = errors.average().toFloat()
        val worst = errors.max()
        if (mean > 0.075f || worst > 0.28f) return null
        // Once round: the angle around the centre sweeps one full turn, mostly one way.
        var sweep = 0.0
        var backwards = 0.0
        var prev = atan2(((vs[0] - vcx) / b).toDouble(), ((us[0] - ucx) / a).toDouble())
        for (i in 1 until pts.size) {
            val ang = atan2(((vs[i] - vcx) / b).toDouble(), ((us[i] - ucx) / a).toDouble())
            var d = ang - prev
            if (d > PI) d -= 2 * PI
            if (d < -PI) d += 2 * PI
            sweep += d
            prev = ang
            backwards += abs(d)
        }
        if (abs(sweep) < 1.7 * PI || abs(sweep) > 2.6 * PI || backwards > abs(sweep) * 1.25) return null

        val centreX = cx + (ucx * c - vcx * s)
        val centreY = cy + (ucx * s + vcx * c)
        val circle = min(a, b) / max(a, b) >= 0.85f
        val ra = if (circle) (a + b) / 2f else a
        val rb = if (circle) (a + b) / 2f else b
        val out = List(ELLIPSE_POINTS + 1) { i ->
            val t = 2 * PI * i / ELLIPSE_POINTS
            val u = ra * cos(t).toFloat(); val v = rb * sin(t).toFloat()
            P(centreX + (u * c - v * s), centreY + (u * s + v * c))
        }
        return (if (circle) ShapeKind.CIRCLE else ShapeKind.ELLIPSE) to out
    }

    private fun polygon(pts: List<P>, length: Float, w: Float, h: Float): Pair<ShapeKind, List<P>>? {
        val closedPts = pts + pts.first()
        val corners = simplifyClosed(closedPts, 0.045f * length)
        val k = corners.size
        if (k != 3 && k != 4) return null
        // Every drawn point close to the clean outline, and the corners really corners.
        val outline = corners + corners.first()
        val size = sqrt(w * h)
        val deviation = pts.map { p -> (0 until k).minOf { distToSegment(p, outline[it], outline[it + 1]) } }
        if (deviation.average() > 0.035f * size || deviation.max() > 0.14f * size) return null
        val angles = (0 until k).map { cornerAngle(outline[(it + k - 1) % k], corners[it], outline[it + 1]) }
        if (k == 3) {
            if (angles.any { it < 20.0 || it > 150.0 }) return null
            return ShapeKind.TRIANGLE to densify(outline)
        }
        if (angles.any { abs(it - 90.0) > 20.0 }) return null
        // Level and upright sides become exactly so; a tilted rectangle keeps its tilt.
        val tilt = Math.toDegrees(atan2((corners[1].y - corners[0].y).toDouble(), (corners[1].x - corners[0].x).toDouble()))
        val toAxis = ((tilt % 90.0) + 90.0) % 90.0
        val rect = if (toAxis <= 10.0 || toAxis >= 80.0) {
            val l = corners.minOf { it.x }; val r = corners.maxOf { it.x }
            val t = corners.minOf { it.y }; val b = corners.maxOf { it.y }
            listOf(P(l, t), P(r, t), P(r, b), P(l, b))
        } else {
            squareUp(corners)
        }
        return ShapeKind.RECTANGLE to densify(rect + rect.first())
    }

    // ---- Arrows --------------------------------------------------------------------------------

    /** One stroke: the shaft to the tip, one barb, back to the tip, the other barb. */
    private fun arrow(pts: List<P>, length: Float, scale: Float): Pair<ShapeKind, List<P>>? {
        val v = simplifyOpen(pts, 0.04f * length)
        if (v.size != 5) return null
        val (start, tip, barb1, tip2, barb2) = v
        val shaft = dist(start, tip)
        if (shaft * scale < MIN_LINE || dist(tip, tip2) > 0.25f * shaft) return null
        val b1 = dist(tip, barb1); val b2 = dist(tip2, barb2)
        if (b1 !in 0.1f * shaft..0.6f * shaft || b2 !in 0.1f * shaft..0.6f * shaft) return null
        // The shaft itself must be straight.
        val shaftPts = pts.subList(0, pts.indexOf(tip).takeIf { it > 0 } ?: return null)
        if (shaftPts.any { distToSegment(it, start, tip) > 0.08f * shaft }) return null
        val a1 = cornerAngle(start, tip, barb1)
        val a2 = cornerAngle(start, tip2, barb2)
        if (a1 !in 15.0..70.0 || a2 !in 15.0..70.0) return null
        // Barbs on both sides of the shaft.
        if (side(start, tip, barb1) * side(start, tip, barb2) >= 0f) return null
        val barb = (b1 + b2) / 2f
        val angle = Math.toRadians((a1 + a2) / 2.0)
        val back = atan2((start.y - tip.y).toDouble(), (start.x - tip.x).toDouble())
        val sign = if (side(start, tip, barb1) > 0f) 1 else -1
        fun barbAt(dir: Int) = P(tip.x + (barb * cos(back + dir * angle)).toFloat(), tip.y + (barb * sin(back + dir * angle)).toFloat())
        return ShapeKind.ARROW to densify(listOf(start, tip, barbAt(sign), tip, barbAt(-sign)))
    }

    // ---- Geometry helpers ----------------------------------------------------------------------

    /**
     * Points along each side, so the drawing code's gentle smoothing (through segment midpoints)
     * leaves the corners sharp instead of rounding a 4-point square into a diamond.
     */
    private fun densify(vertices: List<P>): List<P> {
        val out = ArrayList<P>()
        for (i in 0 until vertices.size - 1) {
            val a = vertices[i]; val b = vertices[i + 1]
            val steps = (dist(a, b) / EDGE_STEP).roundToInt().coerceIn(1, 200)
            for (j in 0 until steps) out += P(a.x + (b.x - a.x) * j / steps, a.y + (b.y - a.y) * j / steps)
        }
        out += vertices.last()
        return out
    }

    /** A tilted quadrilateral made a true rectangle: centre, tilt and side lengths averaged. */
    private fun squareUp(c: List<P>): List<P> {
        val cx = c.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = c.sumOf { it.y.toDouble() }.toFloat() / 4f
        val ux = ((c[1].x - c[0].x) + (c[2].x - c[3].x)) / 2f
        val uy = ((c[1].y - c[0].y) + (c[2].y - c[3].y)) / 2f
        val lu = hypot(ux, uy)
        val vx = ((c[3].x - c[0].x) + (c[2].x - c[1].x)) / 2f
        val vy = ((c[3].y - c[0].y) + (c[2].y - c[1].y)) / 2f
        val lv = hypot(vx, vy)
        val dx = ux / lu; val dy = uy / lu
        // Perpendicular, on the side the drawn second side went.
        val sgn = if (dx * vy - dy * vx >= 0f) 1f else -1f
        val px = -dy * sgn; val py = dx * sgn
        val hu = lu / 2f; val hv = lv / 2f
        return listOf(
            P(cx - dx * hu - px * hv, cy - dy * hu - py * hv),
            P(cx + dx * hu - px * hv, cy + dy * hu - py * hv),
            P(cx + dx * hu + px * hv, cy + dy * hu + py * hv),
            P(cx - dx * hu + px * hv, cy - dy * hu + py * hv),
        )
    }

    /** Ramer–Douglas–Peucker on an open path. */
    private fun simplifyOpen(pts: List<P>, epsilon: Float): List<P> {
        if (pts.size < 3) return pts
        var worst = 0f
        var index = 0
        for (i in 1 until pts.lastIndex) {
            val d = distToSegment(pts[i], pts.first(), pts.last())
            if (d > worst) { worst = d; index = i }
        }
        if (worst <= epsilon) return listOf(pts.first(), pts.last())
        val left = simplifyOpen(pts.subList(0, index + 1), epsilon)
        val right = simplifyOpen(pts.subList(index, pts.size), epsilon)
        return left.dropLast(1) + right
    }

    /** RDP on a closed outline: split at the point farthest from the start, then simplify both halves. */
    private fun simplifyClosed(pts: List<P>, epsilon: Float): List<P> {
        val start = pts.first()
        val far = pts.indices.maxBy { dist(pts[it], start) }
        val a = simplifyOpen(pts.subList(0, far + 1), epsilon)
        val b = simplifyOpen(pts.subList(far, pts.size), epsilon)
        val ring = (a.dropLast(1) + b.dropLast(1)).toMutableList()
        // Drop corners that are not corners (almost straight through).
        var changed = true
        while (changed && ring.size > 3) {
            changed = false
            for (i in ring.indices) {
                val ang = cornerAngle(ring[(i + ring.size - 1) % ring.size], ring[i], ring[(i + 1) % ring.size])
                if (ang > 155.0) { ring.removeAt(i); changed = true; break }
            }
        }
        return ring
    }

    /** Angle at [b] between [a] and [c], in degrees (180 = straight on). */
    private fun cornerAngle(a: P, b: P, c: P): Double {
        val v1x = a.x - b.x; val v1y = a.y - b.y
        val v2x = c.x - b.x; val v2y = c.y - b.y
        val d = hypot(v1x, v1y) * hypot(v2x, v2y)
        if (d == 0f) return 180.0
        return Math.toDegrees(kotlin.math.acos(((v1x * v2x + v1y * v2y) / d).toDouble().coerceIn(-1.0, 1.0)))
    }

    private fun side(a: P, b: P, p: P): Float = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)

    private fun pathLength(pts: List<P>): Float = (1 until pts.size).sumOf { dist(pts[it - 1], pts[it]).toDouble() }.toFloat()

    private fun dist(a: P, b: P) = hypot(a.x - b.x, a.y - b.y)

    private fun distToSegment(p: P, a: P, b: P): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    private const val SNAP_DEGREES = 4.0
    private const val ELLIPSE_POINTS = 72
    private const val EDGE_STEP = 0.004f
}
