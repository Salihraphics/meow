package com.salihraphics.meow.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.cancellation.CancellationException

/**
 * [PdfRenderer] is not thread-safe and allows one open page at a time, so every call runs on a
 * single thread owned by this session. Closing hands the renderer to that same thread, so it is
 * never closed in the middle of a render.
 */
class AndroidPdfRenderSession private constructor(
    private val renderer: PdfRenderer,
    private val dispatcher: ExecutorCoroutineDispatcher,
    override val pageSizes: List<PdfPageSize>,
) : PdfRenderSession {

    @Volatile private var closed = false

    override suspend fun render(pageIndex: Int, widthPx: Int): Bitmap {
        require(pageIndex in pageSizes.indices) { "No page $pageIndex" }
        val size = pageSizes[pageIndex]
        val width = widthPx.coerceAtLeast(1)
        val height = (width.toLong() * size.height / size.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        return try {
            withContext(dispatcher) {
                if (closed) throw CancellationException("Session closed")
                val page = renderer.openPage(pageIndex)
                try {
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally {
                    page.close()
                }
            }
        } catch (e: RejectedExecutionException) {
            throw CancellationException("Session closed").apply { initCause(e) }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            dispatcher.executor.execute { renderer.close() }
        } catch (_: RejectedExecutionException) {
            // Already shut down.
        }
        dispatcher.close()
    }

    object Factory : PdfRenderSessionFactory {
        override suspend fun open(file: File): PdfRenderSession {
            val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "meow-pdf-render") }.asCoroutineDispatcher()
            try {
                // Not cancellable: a renderer half-opened and then abandoned would leak its file.
                return withContext(dispatcher + NonCancellable) {
                    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = try {
                        PdfRenderer(descriptor)
                    } catch (e: Throwable) {
                        descriptor.close()
                        throw e
                    }
                    try {
                        // Page sizes up front give the scrolling list stable item heights.
                        val sizes = List(renderer.pageCount) { i ->
                            val page = renderer.openPage(i)
                            try { PdfPageSize(page.width, page.height) } finally { page.close() }
                        }
                        AndroidPdfRenderSession(renderer, dispatcher, sizes)
                    } catch (e: Throwable) {
                        renderer.close()
                        throw e
                    }
                }
            } catch (e: Throwable) {
                dispatcher.close()
                throw e
            }
        }
    }
}
