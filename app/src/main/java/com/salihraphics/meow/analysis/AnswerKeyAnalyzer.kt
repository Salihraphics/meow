package com.salihraphics.meow.analysis

import com.salihraphics.meow.analysis.key.AnswerKeyParseResult
import com.salihraphics.meow.analysis.key.AnswerKeyParser
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.analysis.key.TurkishText
import com.salihraphics.meow.analysis.text.OcrRegion
import com.salihraphics.meow.analysis.text.PageOcr
import com.salihraphics.meow.analysis.text.PdfTextSource
import com.salihraphics.meow.domain.model.AnalysisClues
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
    /** Pages whose embedded text couldn't be read (fonts without a character map). */
    val unreadablePages: List<Int> = emptyList(),
    /** Pages read in full with OCR. */
    val ocrPages: List<Int> = emptyList(),
) {
    val clues: AnalysisClues
        get() = AnalysisClues(
            headingPages = result.clues.headingPages,
            keyPages = result.clues.keyPages,
            answerLines = result.clues.answerLines,
            unreadablePages = unreadablePages,
            scannedPages = pageCount - textPages,
            ocrPages = ocrPages,
        )
}

/**
 * Looks for answer keys in one PDF, cheapest evidence first:
 * 1. The PDF's own text, last pages first (keys are usually at the end), then the rest. Text
 *    that turns out unreadable (fonts without a character map) counts as no text.
 * 2. OCR only when the text didn't give a key: first pages titled "Cevap Anahtarı" whose answers
 *    weren't text (printed as drawings), then the last pages, scans first; and if that still finds
 *    nothing, the top and bottom margins of scanned pages (for per-page keys).
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
                onProgress(0.4f * (i + 1) / order.size)
            }
        }
        val unreadable = pages.values.filter { it.characterCount >= MIN_TEXT_CHARS && !isReadable(it) }.map { it.pageIndex }.sorted()
        val textless = (0 until pageCount).filter { (pages[it]?.characterCount ?: 0) < MIN_TEXT_CHARS || it in unreadable }
        // Every readable page with any text is parsed: a short key page may hold only a few characters.
        val readable = pages.values.filter { it.characterCount > 0 && it.pageIndex !in unreadable }
        var result = parser.parse(readable)
        var ocrReads = 0
        val ocrPages = HashMap<Int, PageText>()

        if (ocr != null && result.tests.none { it.kind == KeySourceKind.KEY_SECTION }) {
            // Key pages whose answers weren't text, then the last pages (scans before the rest).
            val answered = result.tests.flatMap { it.sourcePages }.toSet()
            val headings = result.clues.headingPages.filter { it !in answered }
            val tail = tailPages(pageCount)
            // Text pages are worth OCR only when the text found no key at all (answers drawn as shapes).
            val drawn = if (result.found) emptyList() else tail.filter { it !in textless }
            val candidates = (headings + tail.filter { it in textless } + drawn).distinct().take(MAX_FULL_OCR_PAGES)
            candidates.forEachIndexed { i, index ->
                currentCoroutineContext().ensureActive()
                ocr.read(index, OcrRegion.FULL_PAGE)?.let { ocrPages[index] = it }
                ocrReads++
                onProgress(0.4f + 0.3f * (i + 1) / candidates.size)
            }
            if (ocrPages.isNotEmpty()) result = parser.parse(merged(readable, ocrPages))

            // Still nothing: the answers may be printed in the margins of scanned pages.
            if (!result.found) {
                val margins = textless.filter { it !in ocrPages }.take(MAX_MARGIN_OCR_PAGES)
                val marginPages = HashMap<Int, PageText>()
                margins.forEachIndexed { i, index ->
                    currentCoroutineContext().ensureActive()
                    val lines = listOfNotNull(ocr.read(index, OcrRegion.TOP_MARGIN), ocr.read(index, OcrRegion.BOTTOM_MARGIN))
                        .flatMap { it.lines }
                    ocrReads += 2
                    if (lines.isNotEmpty()) marginPages[index] = PageText(index, lines.sortedBy { it.box.top }, TextSource.OCR)
                    onProgress(0.7f + 0.3f * (i + 1) / margins.size)
                }
                if (marginPages.isNotEmpty()) result = parser.parse(merged(readable, ocrPages + marginPages))
            }
        }
        onProgress(1f)
        return AnalysisOutcome(result, pageCount, pageCount - textless.size, ocrReads, unreadable, ocrPages.keys.sorted())
    }

    /**
     * Reads only [pageIndexes] (0-based) as answer-key pages: the pages a student pointed at, or
     * every page of a separate answer-key PDF (null). OCR is used for pages whose text gives no
     * answers.
     */
    suspend fun analyzeKeyPages(file: File, pageIndexes: List<Int>?, ocr: PageOcr?, onProgress: (Float) -> Unit = {}): AnalysisOutcome {
        val pages = HashMap<Int, PageText>()
        val pageCount: Int
        textSource.open(file).use { document ->
            pageCount = document.pageCount
            val wanted = (pageIndexes ?: (0 until pageCount).toList()).filter { it in 0 until pageCount }.take(MAX_KEY_PAGES)
            wanted.forEachIndexed { i, index ->
                currentCoroutineContext().ensureActive()
                pages[index] = document.page(index)
                onProgress(0.4f * (i + 1) / wanted.size)
            }
        }
        val unreadable = pages.values.filter { it.characterCount >= MIN_TEXT_CHARS && !isReadable(it) }.map { it.pageIndex }.sorted()
        val readable = pages.values.filter { it.pageIndex !in unreadable }
        var result = parser.parse(readable, forcedKeyPages = pages.keys)
        val answered = result.tests.flatMap { it.sourcePages }.toSet()
        val needOcr = pages.keys.filter { it !in answered }.sorted().take(MAX_FULL_OCR_PAGES * 3)
        val ocrPages = HashMap<Int, PageText>()
        if (ocr != null) {
            needOcr.forEachIndexed { i, index ->
                currentCoroutineContext().ensureActive()
                ocr.read(index, OcrRegion.FULL_PAGE)?.let { ocrPages[index] = it }
                onProgress(0.4f + 0.6f * (i + 1) / needOcr.size)
            }
            if (ocrPages.isNotEmpty()) result = parser.parse(merged(readable, ocrPages), forcedKeyPages = pages.keys)
        }
        onProgress(1f)
        val textPages = pages.values.count { it.characterCount >= MIN_TEXT_CHARS && it.pageIndex !in unreadable }
        return AnalysisOutcome(result, pageCount, textPages, ocrPages.size, unreadable, ocrPages.keys.sorted())
    }

    /** OCR text replaces a page's own text where both exist (it was read because the text gave nothing). */
    private fun merged(text: List<PageText>, ocr: Map<Int, PageText>): List<PageText> =
        text.filter { it.pageIndex !in ocr } + ocr.values

    companion object {
        const val MIN_TEXT_CHARS = 25
        const val MAX_FULL_OCR_PAGES = 12
        const val MAX_MARGIN_OCR_PAGES = 400
        const val MAX_KEY_PAGES = 120

        /** The last quarter (at least 8 pages), last page first; it's where keys usually are. */
        fun tailPages(pageCount: Int): List<Int> {
            val size = maxOf(8, pageCount / 4).coerceAtMost(pageCount)
            return (pageCount - 1 downTo pageCount - size).toList()
        }

        fun priorityOrder(pageCount: Int): List<Int> {
            val tail = tailPages(pageCount)
            return tail + (0 until pageCount).filter { it !in tail.toSet() }
        }

        /**
         * Whether a page's text is real text. PDFs whose fonts lack a character map give text
         * made of symbols, private-use or foreign characters, or consonant soup; such pages are
         * treated as scans. A word counts as readable when it uses Turkish/Latin letters, digits
         * and ordinary punctuation ("1.A", "x+y=3", "Aşağıdakilerden"), and a long all-letter word
         * has a vowel.
         */
        fun isReadable(page: PageText): Boolean {
            val words = page.lines.flatMap { it.words }.map { it.text }
            if (words.isEmpty()) return true
            var good = 0
            var bad = 0
            for (word in words) {
                val core = word.trim { !it.isLetterOrDigit() }
                when {
                    core.isEmpty() -> if (word.length >= 2) bad++
                    !readableWord.matches(core) -> bad++
                    core.length >= 4 && core.all { it.isLetter() } && TurkishText.fold(core).none { it in "aeiou" } -> bad++
                    else -> good++
                }
            }
            return good >= bad
        }

        private val readableWord = Regex("[A-Za-zÇĞİÖŞÜçğıöşüÂâÎîÛûÊê0-9.,:;/()\\-+=%?!'’\"*×÷<>°^²³√π]+")
    }
}
