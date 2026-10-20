package com.salihraphics.meow.analysis.text

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextBox
import com.salihraphics.meow.analysis.TextSource
import com.salihraphics.meow.analysis.TextWord
import com.salihraphics.meow.data.pdf.PdfRenderSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device OCR with ML Kit's bundled Latin model (covers Turkish letters). No network, no
 * account. Pages are rendered through the same renderer as the viewer.
 */
class MlKitPageOcr(private val session: PdfRenderSession) : PageOcr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun read(pageIndex: Int, region: OcrRegion): PageText? {
        val page = session.render(pageIndex, if (region == OcrRegion.FULL_PAGE) FULL_WIDTH_PX else MARGIN_WIDTH_PX)
        val (top, bottom) = when (region) {
            OcrRegion.FULL_PAGE -> 0f to 1f
            OcrRegion.TOP_MARGIN -> 0f to MARGIN_FRACTION
            OcrRegion.BOTTOM_MARGIN -> 1f - MARGIN_FRACTION to 1f
        }
        val y = (page.height * top).toInt()
        val crop = if (region == OcrRegion.FULL_PAGE) page else Bitmap.createBitmap(page, 0, y, page.width, (page.height * (bottom - top)).toInt().coerceAtLeast(1))
        return try {
            val words = recognize(crop).map { (text, rect) ->
                // Back to whole-page fractions.
                TextWord(
                    text,
                    TextBox(
                        rect.left.toFloat() / page.width,
                        top + rect.top.toFloat() / page.height,
                        rect.right.toFloat() / page.width,
                        top + rect.bottom.toFloat() / page.height,
                    ),
                )
            }
            PageText(pageIndex, groupIntoLines(words), TextSource.OCR)
        } catch (e: Exception) {
            null
        } finally {
            if (crop !== page) crop.recycle()
            page.recycle()
        }
    }

    private suspend fun recognize(bitmap: Bitmap): List<kotlin.Pair<String, android.graphics.Rect>> =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val words = result.textBlocks.flatMap { block -> block.lines.flatMap { it.elements } }
                        .mapNotNull { element -> element.boundingBox?.let { element.text to it } }
                    continuation.resume(words)
                }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }

    private companion object {
        const val FULL_WIDTH_PX = 1400
        const val MARGIN_WIDTH_PX = 1200
        const val MARGIN_FRACTION = 0.14f
    }
}
