package com.salihraphics.meow.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class AndroidPdfInspector : PdfInspector {

    override fun pageCount(file: File): Int = withRenderer(file) { it.pageCount }

    override fun writeThumbnail(pdf: File, target: File, widthPx: Int): Boolean = runCatching {
        withRenderer(pdf) { renderer ->
            if (renderer.pageCount == 0) return@withRenderer false
            val page = renderer.openPage(0)
            val bitmap = try {
                val height = (widthPx.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, widthPx * 4)
                Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } finally {
                page.close()
            }
            target.parentFile?.mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            true
        }
    }.getOrDefault(false)

    private inline fun <T> withRenderer(file: File, block: (PdfRenderer) -> T): T {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: Throwable) {
            descriptor.close()
            throw e
        }
        // Closing the renderer also closes the descriptor.
        return try { block(renderer) } finally { renderer.close() }
    }
}
