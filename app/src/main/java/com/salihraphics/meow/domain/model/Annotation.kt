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
    /** x0, y0, x1, y1, … as page fractions, or as fractions of the note for [noteId]'s ink. */
    val points: FloatArray,
    /** Set when the stroke was written on a sticky note: it moves and resizes with the note. */
    val noteId: String? = null,
) {
    val pointCount: Int get() = points.size / 2

    override fun equals(other: Any?): Boolean =
        other is Stroke && other.id == id && other.documentId == documentId && other.pageIndex == pageIndex &&
            other.tool == tool && other.color == color && other.width == width && other.points.contentEquals(points) &&
            other.noteId == noteId

    override fun hashCode(): Int = id.hashCode()
}

/** A pen draws opaque ink; a highlighter draws a wide translucent band that keeps text readable. */
enum class StrokeTool { PEN, HIGHLIGHTER }

/**
 * A Post-it note on one PDF page. Position and size are fractions of the unrotated page
 * ([x], [width] of its width; [y], [height] of its height), so the note stays on the same spot
 * of the page at any zoom, rotation or screen size.
 */
data class StickyNote(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String,
    /** ARGB. */
    val color: Int,
) {
    /** Kept inside the page and no smaller than [MIN_SIZE]. */
    fun clamped(): StickyNote {
        val w = width.coerceIn(MIN_SIZE, 1f)
        val h = height.coerceIn(MIN_SIZE, 1f)
        return copy(x = x.coerceIn(0f, 1f - w), y = y.coerceIn(0f, 1f - h), width = w, height = h)
    }

    companion object {
        const val MIN_SIZE = 0.08f
        const val DEFAULT_WIDTH = 0.3f
        const val DEFAULT_HEIGHT = 0.16f
        const val YELLOW = 0xFFFFE680.toInt()
    }
}
