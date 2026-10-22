package com.salihraphics.meow.feature.viewer

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Turning pages in the viewer. Only the view turns: strokes and notes are stored in the
 * unrotated page's own fractions, so they stay on the same printed spot whatever the rotation,
 * zoom or screen, and the PDF file is never changed.
 */
object PageRotation {
    /** 0, 90, 180 or 270, clockwise. */
    fun normalize(degrees: Int): Int = Math.floorMod(degrees, 360) / 90 * 90

    fun isQuarterTurn(degrees: Int): Boolean = normalize(degrees) % 180 != 0

    /** Width / height of a page of [aspect] (width / height) as shown turned by [degrees]. */
    fun shownAspect(aspect: Float, degrees: Int): Float = if (isQuarterTurn(degrees)) 1f / aspect else aspect

    /** Size of the unturned page inside a box of [shownWidth] × [shownHeight] turned by [degrees]. */
    fun pageSize(shownWidth: Int, shownHeight: Int, degrees: Int): Pair<Int, Int> =
        if (isQuarterTurn(degrees)) shownHeight to shownWidth else shownWidth to shownHeight

    /**
     * A point on the screen, relative to the shown (turned) page of [shownWidth] × [shownHeight],
     * as fractions of the unturned page: where a stroke or note there is stored.
     */
    fun toPageFraction(point: Offset, shownWidth: Float, shownHeight: Float, degrees: Int): Offset {
        val u = point.x / shownWidth
        val v = point.y / shownHeight
        return when (normalize(degrees)) {
            90 -> Offset(v, 1f - u)
            180 -> Offset(1f - u, 1f - v)
            270 -> Offset(1f - v, u)
            else -> Offset(u, v)
        }
    }

    /** The inverse of [toPageFraction]: where a page fraction is shown on the turned page. */
    fun toShown(fraction: Offset, shownWidth: Float, shownHeight: Float, degrees: Int): Offset {
        val (u, v) = when (normalize(degrees)) {
            90 -> 1f - fraction.y to fraction.x
            180 -> 1f - fraction.x to 1f - fraction.y
            270 -> fraction.y to 1f - fraction.x
            else -> fraction.x to fraction.y
        }
        return Offset(u * shownWidth, v * shownHeight)
    }
}

/**
 * Lays the content out as the unturned page and shows it turned by [degrees] to fill this box.
 * Touches inside reach the content in the unturned page's coordinates (Compose maps pointer
 * positions through the layer), which is what keeps drawing in page space.
 */
fun Modifier.turned(degrees: Int): Modifier {
    val turn = PageRotation.normalize(degrees)
    if (turn == 0) return this
    return layout { measurable, constraints ->
        val shownWidth = constraints.maxWidth
        val shownHeight = constraints.maxHeight
        val (w, h) = PageRotation.pageSize(shownWidth, shownHeight, turn)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(shownWidth, shownHeight) {
            placeable.placeWithLayer(((shownWidth - w) / 2f).roundToInt(), ((shownHeight - h) / 2f).roundToInt()) {
                rotationZ = turn.toFloat()
            }
        }
    }
}
