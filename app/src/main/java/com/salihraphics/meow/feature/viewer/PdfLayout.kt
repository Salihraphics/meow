package com.salihraphics.meow.feature.viewer

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/**
 * Exact page geometry for one viewport width, zoom and rotation, in pixels. The page list is laid
 * out with these sizes (not with sizes Compose derives on its own), so zoom maths done here match
 * the screen to the pixel. Pure: no Compose state, so it is tested directly.
 *
 * Content coordinates: y = 0 is the top of the list's top padding; page i starts at [pageTop].
 * x = 0 is the left edge of the (possibly wider than the screen) list.
 */
class PdfLayout(
    val viewportWidth: Int,
    maxPageWidth: Int,
    /** Padding around the pages and the gap between them. */
    val margin: Int,
    val zoom: Float,
    val pageCount: Int,
    /** Width / height of page i as shown (already turned). */
    shownAspect: (Int) -> Float,
) {
    private val baseWidth = minOf(viewportWidth, maxPageWidth + 2 * margin).coerceAtLeast(2 * margin + 1)

    /** Every page is this wide; heights follow each page's own shape. */
    val pageWidth: Int = (baseWidth * zoom - 2 * margin).roundToInt().coerceAtLeast(1)

    /** Never narrower than the screen, so small pages sit centred. */
    val listWidth: Int = maxOf(pageWidth + 2 * margin, viewportWidth)

    /** Left edge of every page in the list (the list centres them). */
    val pageLeft: Int = margin + ((listWidth - 2 * margin - pageWidth) / 2f).roundToInt()

    /** How far the list can be panned sideways. */
    val maxScrollX: Int = listWidth - viewportWidth

    private val heights = IntArray(pageCount) { (pageWidth / shownAspect(it)).roundToInt().coerceAtLeast(1) }
    private val tops = IntArray(pageCount + 1).also { t ->
        t[0] = margin
        for (i in 0 until pageCount) t[i + 1] = t[i] + heights[i] + margin
    }

    fun pageHeight(index: Int): Int = heights[index]

    fun pageTop(index: Int): Int = tops[index]

    /** Top padding, pages, gaps and bottom padding. */
    val contentHeight: Int get() = tops[pageCount]

    /** Content y at the top of the screen for the list position (index, offset) Compose reports. */
    fun scrollY(firstIndex: Int, firstOffset: Int): Int =
        if (pageCount == 0) 0 else tops[firstIndex.coerceIn(0, pageCount - 1)] - margin + firstOffset

    /**
     * The list position (index, offset ≥ 0) whose top is at content y [scrollY]: the inverse of
     * [scrollY], so the page under the top of the screen is found with these exact heights.
     */
    fun listPosition(scrollY: Int): Pair<Int, Int> {
        if (pageCount == 0) return 0 to 0
        val y = scrollY.coerceAtLeast(0)
        // The last page whose slot (gap above it included) starts at or above y.
        var lo = 0
        var hi = pageCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (tops[mid] - margin <= y) lo = mid else hi = mid - 1
        }
        return lo to (y - (tops[lo] - margin))
    }

    /** The page whose slot (the page and the gap below it) holds content y [y]. */
    fun pageAt(y: Float): Int {
        if (pageCount == 0) return 0
        var lo = 0
        var hi = pageCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (tops[mid] <= y) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/** A spot in the document: page and fractions of it (outside 0..1 in the margins and gaps). */
data class DocumentPoint(val page: Int, val x: Float, val y: Float)

/** Where the list is scrolled, in content pixels of one [PdfLayout]. */
data class ScrollPosition(val y: Float, val x: Float)

object ZoomMath {
    /** The document spot shown at viewport point [at]. */
    fun pointAt(layout: PdfLayout, scroll: ScrollPosition, at: Offset): DocumentPoint {
        val cy = scroll.y + at.y
        val page = layout.pageAt(cy)
        return DocumentPoint(
            page,
            (scroll.x + at.x - layout.pageLeft) / layout.pageWidth,
            (cy - layout.pageTop(page)) / layout.pageHeight(page),
        )
    }

    /** Where [point] is on screen, at [scroll]. */
    fun screenOf(layout: PdfLayout, scroll: ScrollPosition, point: DocumentPoint): Offset = Offset(
        layout.pageLeft + point.x * layout.pageWidth - scroll.x,
        layout.pageTop(point.page) + point.y * layout.pageHeight(point.page) - scroll.y,
    )

    /**
     * The scroll for layout [to] that shows the document spot that was under [from] (viewport
     * point, layout [old], scroll [scroll]) under viewport point [toPoint]. Kept inside what can
     * be scrolled; the spot moves only when the document's edge is reached.
     */
    fun anchor(
        old: PdfLayout,
        scroll: ScrollPosition,
        from: Offset,
        to: PdfLayout,
        toPoint: Offset,
        viewportHeight: Int,
    ): ScrollPosition {
        val spot = pointAt(old, scroll, from)
        val y = to.pageTop(spot.page) + spot.y * to.pageHeight(spot.page) - toPoint.y
        val x = to.pageLeft + spot.x * to.pageWidth - toPoint.x
        val maxY = (to.contentHeight - viewportHeight).coerceAtLeast(0)
        return ScrollPosition(y.coerceIn(0f, maxY.toFloat()), x.coerceIn(0f, to.maxScrollX.toFloat()))
    }
}
