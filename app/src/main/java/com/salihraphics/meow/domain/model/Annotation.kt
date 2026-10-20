package com.salihraphics.meow.domain.model

/**
 * A freehand mark on one PDF page. Coordinates are fractions of the page (0..1 on each axis), so
 * a stroke stays on the same spot of the page at any zoom, rotation or screen size.
 */
data class Stroke(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val tool: StrokeTool,
    /** ARGB. */
    val color: Int,
    /** Line width as a fraction of the page width. */
    val width: Float,
    /** x0, y0, x1, y1, … as page fractions. */
    val points: FloatArray,
) {
    val pointCount: Int get() = points.size / 2

    override fun equals(other: Any?): Boolean =
        other is Stroke && other.id == id && other.documentId == documentId && other.pageIndex == pageIndex &&
            other.tool == tool && other.color == color && other.width == width && other.points.contentEquals(points)

    override fun hashCode(): Int = id.hashCode()
}

enum class StrokeTool { PEN }
