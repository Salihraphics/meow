package com.salihraphics.meow.analysis

import com.salihraphics.meow.analysis.key.AnswerKeyParseResult
import com.salihraphics.meow.analysis.key.AnswerKeyParser
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.analysis.text.OcrRegion
import com.salihraphics.meow.analysis.text.PageOcr
import com.salihraphics.meow.analysis.text.PdfTextSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

data class AnalysisOutcome(
    val result: AnswerKeyParseResult,
    val pageCount: Int,
    /** Pages whose own text was used. */
    val textPages: Int,
    /** Pages (or page margins) read with OCR. */
    val ocrReads: Int,
)

/**
 * Looks for answer keys in one PDF, cheapest evidence first:
 * 1. The PDF's own text, last pages first (keys are usually at the end), then the rest.
 * 2. OCR only for pages without text: first the last pages in full, and only if that finds no
 *    key, the top and bottom margins of every page (for per-page keys).
 *
 * Cancellable between pages; nothing is returned (or saved) from a cancelled run.
 */
class AnswerKeyAnalyzer(
    private val textSource: PdfTextSource,
    private val parser: AnswerKeyParser = AnswerKeyParser(),
) {
    suspend fun analyze(file: File, ocr: PageOcr?, onProgress: (Float) -> Unit = {}): AnalysisOutcome {
        val pages = HashMap<Int, PageText>()
        val pageCount: Int
        textSource.open(file).use { document ->
            pageCount = document.pageCount
            val order = priorityOrder(pageCount)
            order.forEachIndexed { i, index ->
                currentCoroutineContext().ensureActive()
                pages[index] = document.page(index)
                onProgress(0.5f * (i + 1) / order.size)
            }
        }
        val textPages = pages.values.count { it.characterCount >= MIN_TEXT_CHARS }
        // Every page with any text is parsed: a short key page may hold only a few characters.
        // The threshold below only decides which pages are scans that also need OCR.
        val withText = pages.values.filter { it.characterCount > 0 }
        var result = parser.parse(withText)
        var ocrReads = 0
        val textless = (0 until pageCount).filter { (pages[it]?.characterCount ?: 0) < MIN_TEXT_CHARS }

        if (ocr != null && textless.isNotEmpty() && result.tests.none { it.kind == KeySourceKind.KEY_SECTION }) {
            // Scanned pages: read the last ones in full first.
            val tail = tailPages(pageCount).filter { it in textless }.take(MAX_FULL_OCR_PAGES)
            val ocrPages = HashMap<Int, PageText>()
            tail.forEachIndexed { i, index ->
                currentCoroutineContext().ensureActive()
                ocr.read(index, OcrRegion.FULL_PAGE)?.let { ocrPages[index] = it }
                ocrReads++
                onProgress(0.5f + 0.2f * (i + 1) / tail.size)
            }
            result = parser.parse(withText + ocrPages.values)

            // Still nothing: the answers may be printed in page margins, so read those strips.
            if (!result.found) {
                val margins = textless.filter { it !in ocrPages }.take(MAX_MARGIN_OCR_PAGES)
                margins.forEachIndexed { i, index ->
                    currentCoroutineContext().ensureActive()
                    val lines = listOfNotNull(ocr.read(index, OcrRegion.TOP_MARGIN), ocr.read(index, OcrRegion.BOTTOM_MARGIN))
                        .flatMap { it.lines }
                    ocrReads += 2
                    if (lines.isNotEmpty()) ocrPages[index] = PageText(index, lines.sortedBy { it.box.top }, TextSource.OCR)
                    onProgress(0.7f + 0.3f * (i + 1) / margins.size)
                }
                result = parser.parse(withText + ocrPages.values)
            }
        }
        onProgress(1f)
        return AnalysisOutcome(result, pageCount, textPages, ocrReads)
    }

    companion object {
        const val MIN_TEXT_CHARS = 25
        const val MAX_FULL_OCR_PAGES = 12
        const val MAX_MARGIN_OCR_PAGES = 400

        /** The last quarter (at least 8 pages), last page first; it's where keys usually are. */
        fun tailPages(pageCount: Int): List<Int> {
            val size = maxOf(8, pageCount / 4).coerceAtMost(pageCount)
            return (pageCount - 1 downTo pageCount - size).toList()
        }

        fun priorityOrder(pageCount: Int): List<Int> {
            val tail = tailPages(pageCount)
            return tail + (0 until pageCount).filter { it !in tail.toSet() }
        }
    }
}
