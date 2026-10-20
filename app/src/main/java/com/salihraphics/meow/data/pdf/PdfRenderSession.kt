package com.salihraphics.meow.data.pdf

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File

/** Page size in PDF points (1/72 inch). */
data class PdfPageSize(val width: Int, val height: Int) {
    val aspectRatio: Float get() = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1)
}

/** An open PDF that can render pages on demand. Must be closed when the viewer goes away. */
interface PdfRenderSession : Closeable {
    val pageSizes: List<PdfPageSize>

    /** Renders one page at [widthPx] wide on a white background. Cancellable while queued. */
    suspend fun render(pageIndex: Int, widthPx: Int): Bitmap
}

fun interface PdfRenderSessionFactory {
    /** @throws SecurityException if password protected, IOException if damaged. */
    suspend fun open(file: File): PdfRenderSession
}
