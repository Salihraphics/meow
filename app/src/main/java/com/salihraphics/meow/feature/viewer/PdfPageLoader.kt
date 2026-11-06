package com.salihraphics.meow.feature.viewer

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.salihraphics.meow.data.pdf.PdfRenderSession
import kotlin.math.sqrt

/** What the page list needs: page shapes, and a way to get a page's pixels at a given width. */
interface PdfPageLoader {
    val pageCount: Int

    /** Width / height of page [index]. */
    fun aspectRatio(index: Int): Float

    suspend fun load(index: Int, widthPx: Int): ImageBitmap
}

/**
 * Renders through a [PdfRenderSession] and keeps recent pages in a cache bounded by bytes, so a
 * 500-page PDF never holds more than a handful of bitmaps.
 */
class SessionPageLoader(
    private val session: PdfRenderSession,
    maxCacheBytes: Int = defaultCacheBytes(),
) : PdfPageLoader {

    private data class Key(val index: Int, val widthPx: Int)

    private val cache = object : LruCache<Key, ImageBitmap>(maxCacheBytes) {
        override fun sizeOf(key: Key, value: ImageBitmap) = value.width * value.height * 4
    }

    override val pageCount: Int get() = session.pageSizes.size

    override fun aspectRatio(index: Int): Float = session.pageSizes[index].aspectRatio

    override suspend fun load(index: Int, widthPx: Int): ImageBitmap {
        val key = Key(index, widthPx)
        cache.get(key)?.let { return it }
        val bitmap = session.render(index, widthPx).asImageBitmap()
        cache.put(key, bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()

    companion object {
        /** An eighth of the app's heap, capped at 96 MB. */
        fun defaultCacheBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(96L * 1024 * 1024).toInt()
    }
}

/**
 * Pixel width to render at: the on-screen width, rounded up to a 128px step so small layout
 * changes reuse cached pages, capped at [MAX_RENDER_WIDTH_PX], and narrower still for tall
 * pages so one page bitmap never exceeds [MAX_RENDER_PIXELS] (about 24 MB). [pageAspect] is
 * the rendered (unturned) page's width / height.
 */
fun renderWidthFor(displayWidthPx: Float, pageAspect: Float = DEFAULT_ASPECT): Int {
    val step = 128
    val wanted = if (displayWidthPx.isFinite()) displayWidthPx else step.toFloat()
    val rounded = ((wanted / step).toInt() + 1) * step
    val aspect = safeAspect(pageAspect)
    // width × (width / aspect) ≤ budget
    val budgetWidth = sqrt(MAX_RENDER_PIXELS * aspect).toInt()
    return rounded.coerceAtMost(MAX_RENDER_WIDTH_PX).coerceAtMost(budgetWidth).coerceAtLeast(16)
}

const val MAX_RENDER_WIDTH_PX = 2048
const val MAX_RENDER_PIXELS = 2048 * 2900
