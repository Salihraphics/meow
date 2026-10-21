package com.salihraphics.meow.analysis

import com.salihraphics.meow.analysis.key.AnswerKeyParseResult
import com.salihraphics.meow.analysis.key.AnswerKeyParser
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.analysis.key.BubbleSheetReader
import com.salihraphics.meow.analysis.key.PageInspection
import com.salihraphics.meow.analysis.text.PageImages
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
    /** A short, shareable account of what was seen; see [AnswerKeyAnalyzer.report]. */
    val report: String = "",
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
    suspend fun analyze(file: File, ocr: PageOcr?, images: PageImages? = null, onProgress: (Float) -> Unit = {}): AnalysisOutcome {
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
        val marginPages = HashMap<Int, PageText>()

        // A text layer with only a running header or page number over a scanned page: OCR it too,
        // keeping the text it has.
        val sparse = readable.filter { isMarginsOnly(it) && parser.inspect(it).answers == 0 }.map { it.pageIndex }.toSet()
        // Short text is kept beside the OCR, and pages whose text already gave answers aren't read.
        val kept = sparse + readable.map { it.pageIndex }.filter { it in textless }
        val scanned = textless.toSet() + sparse - result.tests.flatMap { it.sourcePages }.toSet()

        if (ocr != null) {
            // Key pages whose answers weren't text, scanned pages next to a key found in text and
            // the last scanned pages first; while a scanned page gives answers, the scanned pages
            // next to it too (a long scanned key). Headings are only read when no key turned up,
            // and the last text pages (answers drawn as shapes) only if all that found nothing.
            val answered = result.tests.flatMap { it.sourcePages }.toSet()
            val keyFound = result.tests.any { it.kind == KeySourceKind.KEY_SECTION }
            val headings = if (keyFound) emptyList() else result.clues.headingPages.filter { it !in answered }
            val keyNeighbours = result.tests.filter { it.kind == KeySourceKind.KEY_SECTION }.flatMap { it.sourcePages }
                .flatMap { listOf(it - 1, it + 1) }.filter { it in scanned }
            val tail = tailPages(pageCount)
            val firstStage = (headings + keyNeighbours + tail.filter { it in scanned }).distinct().take(MAX_FULL_OCR_PAGES)
            val queue = ArrayDeque(firstStage)
            val queued = firstStage.toHashSet()
            while (queue.isNotEmpty() && ocrReads < MAX_FOLLOWED_OCR_PAGES) {
                currentCoroutineContext().ensureActive()
                val index = queue.removeFirst()
                val page = ocr.read(index, OcrRegion.FULL_PAGE)
                ocrReads++
                if (page != null) {
                    ocrPages[index] = page
                    if (parser.inspect(page).answers >= MIN_FOLLOWED_ANSWERS) {
                        for (next in listOf(index + 1, index - 1)) if (next in scanned && queued.add(next)) queue.addFirst(next)
                    }
                }
                onProgress(0.4f + 0.15f * ocrReads / (ocrReads + queue.size))
            }
            if (ocrPages.isNotEmpty()) result = parser.parse(merged(readable, ocrPages, kept))

            val budget = (MAX_FULL_OCR_PAGES - firstStage.size).coerceAtLeast(0)
            val secondStage = if (result.found) emptyList() else tail.filter { it !in scanned && it !in ocrPages }.take(budget)
            secondStage.forEachIndexed { i, index ->
                currentCoroutineContext().ensureActive()
                ocr.read(index, OcrRegion.FULL_PAGE)?.let { ocrPages[index] = it }
                ocrReads++
                onProgress(0.55f + 0.15f * (i + 1) / secondStage.size)
            }
            if (secondStage.isNotEmpty()) result = parser.parse(merged(readable, ocrPages, kept))

            // Still nothing: the answers may be printed in the margins of scanned pages.
            if (!result.found) {
                val margins = textless.filter { it !in ocrPages }.take(MAX_MARGIN_OCR_PAGES)
                margins.forEachIndexed { i, index ->
                    currentCoroutineContext().ensureActive()
                    val lines = listOfNotNull(ocr.read(index, OcrRegion.TOP_MARGIN), ocr.read(index, OcrRegion.BOTTOM_MARGIN))
                        .flatMap { it.lines }
                    ocrReads += 2
                    if (lines.isNotEmpty()) marginPages[index] = PageText(index, lines.sortedBy { it.box.top }, TextSource.OCR)
                    onProgress(0.7f + 0.3f * (i + 1) / margins.size)
                }
                if (marginPages.isNotEmpty()) result = parser.parse(merged(readable, ocrPages + marginPages, kept))
            }
        }
        // Optical answer sheets: text gives the rows, the page image tells which bubble is filled.
        val bubbles = readBubbles(merged(readable, ocrPages + marginPages, kept), images)
        if (bubbles.readings.isNotEmpty()) result = parser.parse(bubbles.pages, imageReadings = bubbles.readings)
        onProgress(1f)
        val used = merged(readable, ocrPages, kept)
        val textPages = pageCount - textless.size
        return AnalysisOutcome(
            result, pageCount, textPages, ocrReads, unreadable, ocrPages.keys.sorted(),
            report(pageCount, textPages, unreadable, ocrPages.keys, used, result, ocr != null, bubbles = bubbles.summary),
        )
    }

    /** What reading optical forms gave; empty when there were none. */
    data class BubbleSummary(val pages: List<Int> = emptyList(), val marked: Int = 0, val blank: Int = 0, val ambiguous: Int = 0, val noImages: Boolean = false)

    private class Bubbles(val pages: List<PageText>, val readings: Map<Int, Map<Int, String>>, val summary: BubbleSummary)

    /**
     * Finds "n A B C D E" rows and, if page images are available, which bubble of each is filled.
     * Marked rows join their page as answer lines; blank forms and doubly marked rows add nothing.
     */
    private suspend fun readBubbles(pages: List<PageText>, images: PageImages?): Bubbles {
        val forms = pages.associateWith { BubbleSheetReader.rows(it) }.filterValues { it.isNotEmpty() }.entries.take(MAX_BUBBLE_PAGES)
        if (forms.isEmpty()) return Bubbles(pages, emptyMap(), BubbleSummary())
        if (images == null) return Bubbles(pages, emptyMap(), BubbleSummary(forms.map { it.key.pageIndex }, noImages = true))
        val readings = HashMap<Int, Map<Int, String>>()
        val rewritten = HashMap<Int, PageText>()
        var marked = 0
        var blank = 0
        var ambiguous = 0
        for ((page, rows) in forms) {
            currentCoroutineContext().ensureActive()
            val darkness = runCatching { images.darkness(page.pageIndex) }.getOrNull() ?: continue
            val read = BubbleSheetReader.read(rows, darkness)
            marked += read.values.count { it is BubbleSheetReader.Reading.Marked }
            blank += read.values.count { it == BubbleSheetReader.Reading.Blank }
            ambiguous += read.values.count { it == BubbleSheetReader.Reading.Ambiguous }
            val letters = read.mapNotNull { (q, r) -> (r as? BubbleSheetReader.Reading.Marked)?.let { q to it.letter } }.toMap()
            if (letters.isNotEmpty()) {
                readings[page.pageIndex] = letters
                rewritten[page.pageIndex] = BubbleSheetReader.withAnswers(page, rows, read)
            }
        }
        val withAnswers = pages.map { p -> rewritten[p.pageIndex] ?: p }
        return Bubbles(withAnswers, readings, BubbleSummary(forms.map { it.key.pageIndex }, marked, blank, ambiguous))
    }

    /**
     * Reads only [pageIndexes] (0-based) as answer-key pages: the pages a student pointed at, or
     * every page of a separate answer-key PDF (null). OCR is used for pages whose text gives no
     * answers.
     */
    suspend fun analyzeKeyPages(
        file: File,
        pageIndexes: List<Int>?,
        ocr: PageOcr?,
        images: PageImages? = null,
        onProgress: (Float) -> Unit = {},
    ): AnalysisOutcome {
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
        val bubbles = readBubbles(merged(readable, ocrPages), images)
        if (bubbles.readings.isNotEmpty()) result = parser.parse(bubbles.pages, forcedKeyPages = pages.keys, imageReadings = bubbles.readings)
        onProgress(1f)
        val textPages = pages.values.count { it.characterCount >= MIN_TEXT_CHARS && it.pageIndex !in unreadable }
        return AnalysisOutcome(
            result, pageCount, textPages, ocrPages.size, unreadable, ocrPages.keys.sorted(),
            report(pageCount, textPages, unreadable, ocrPages.keys, merged(readable, ocrPages), result, ocr != null, pages.keys.sorted(), bubbles.summary),
        )
    }

    /**
     * What the search saw, in a few lines a student can copy and send: per stage counts, then
     * the pages that looked most like a key (and every page with a key heading), each with its
     * headings, answer-like lines, table rows and why near-miss lines were turned down. Excerpts
     * are short and only of key-like lines; whole pages are never included.
     */
    private fun report(
        pageCount: Int,
        textPages: Int,
        unreadable: List<Int>,
        ocrPages: Set<Int>,
        pages: List<PageText>,
        result: AnswerKeyParseResult,
        ocrAvailable: Boolean,
        askedFor: List<Int>? = null,
        bubbles: BubbleSummary = BubbleSummary(),
    ): String {
        val inspected = pages.map { parser.inspect(it) }
        val score = { i: PageInspection ->
            i.answers + 2 * (i.numberRows + i.letterRows + i.bubbleRows) + i.rejected.size + if (i.keyHeadings.isNotEmpty()) 50 else 0
        }
        val shown = inspected.filter { score(it) > 0 }.sortedByDescending(score).take(MAX_REPORTED_PAGES).sortedBy { it.pageIndex }
        fun pages(list: Collection<Int>) = if (list.isEmpty()) "-" else list.sorted().take(12).joinToString(", ") { "${it + 1}" } + if (list.size > 12) "…" else ""
        return buildString {
            appendLine("MEOW cevap anahtarı tanılama · sürüm $REPORT_VERSION")
            askedFor?.let { appendLine("İstenen sayfalar: ${pages(it)}") }
            appendLine("Sayfa: $pageCount · metni okunan: $textPages · okunamayan metin: ${unreadable.size} · OCR: ${ocrPages.size}${if (ocrAvailable) "" else " (OCR yok)"}")
            appendLine("Anahtar başlıklı sayfalar: ${pages(result.clues.headingPages)}")
            appendLine("Anahtar sayılan sayfalar: ${pages(result.clues.keyPages)}")
            appendLine("Cevap satırı (tüm kitap): ${result.clues.answerLines}")
            with(result.clues) {
                appendLine("Soru yanında \"Cevap: X\": $labelledAnswers (soru numarasız: $unplacedLabels) · çözüm sayfaları: ${pages(solutionPages)}")
                if (marginLinesOutOfContext > 0) appendLine("Sayfanın sorularıyla eşleşmeyen kenar cevap satırı: $marginLinesOutOfContext")
            }
            if (bubbles.pages.isNotEmpty()) {
                appendLine(
                    "Optik form sayfaları: ${pages(bubbles.pages)} · " +
                        if (bubbles.noImages) "görüntü okunamadı" else "işaretli ${bubbles.marked}, boş ${bubbles.blank}, belirsiz ${bubbles.ambiguous}",
                )
            }
            appendLine("Bulunan testler: ${result.tests.size}" + result.tests.take(6).joinToString("") { t ->
                "\n  · ${t.ref?.label?.ifBlank { null } ?: "adsız"} (${t.kind}): ${t.answers.size} cevap, s. ${pages(t.sourcePages)}" +
                    if (t.issues.isNotEmpty()) ", sorunlar: ${t.issues.joinToString { it.javaClass.simpleName }}" else ""
            } + if (result.tests.size > 6) "\n  · …" else "")
            if (shown.isEmpty()) appendLine("Anahtara benzeyen sayfa yok.")
            for (i in shown) {
                appendLine("— s. ${i.pageIndex + 1}${if (i.pageIndex in ocrPages) " (OCR)" else ""} · ${i.lines} satır${if (i.byRows) " · satır satır okundu" else ""}")
                if (i.keyHeadings.isNotEmpty()) appendLine("  başlık: ${i.keyHeadings.joinToString(" | ")}")
                if (i.sections.isNotEmpty()) appendLine("  gün/ünite: ${i.sections.joinToString(", ")}")
                if (i.testHeaders.isNotEmpty()) appendLine("  testler: ${i.testHeaders.joinToString(", ")}")
                appendLine("  cevap satırı ${i.answerLines} (${i.answers} cevap) · tablodan ${i.tableLines} · sayı satırı ${i.numberRows} · harf satırı ${i.letterRows} · balon ${i.bubbleRows}")
                i.rejected.forEach { (line, why) -> appendLine("  reddedildi: \"$line\" → $why") }
                i.samples.forEach { appendLine("  örnek: \"$it\"") }
            }
        }.trimEnd()
    }

    /** OCR text replaces a page's own text where both exist (it was read because the text gave nothing). */
    /**
     * OCR'd pages replace their text, except [kept] pages, whose text lines join the OCR's where
     * OCR didn't read the same place again.
     */
    private fun merged(text: List<PageText>, ocr: Map<Int, PageText>, kept: Set<Int> = emptySet()): List<PageText> {
        val byIndex = text.associateBy { it.pageIndex }
        return text.filter { it.pageIndex !in ocr } + ocr.values.map { page ->
            val own = byIndex[page.pageIndex]?.takeIf { page.pageIndex in kept } ?: return@map page
            val extra = own.lines.filter { line -> page.lines.none { overlaps(it.box, line.box) } }
            page.copy(lines = (extra + page.lines).sortedBy { it.box.top })
        }
    }

    private fun overlaps(a: TextBox, b: TextBox) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** Text only in the top and bottom bands, and little of it: a running header over a scan. */
    private fun isMarginsOnly(page: PageText) = page.characterCount < SPARSE_TEXT_CHARS &&
        page.lines.all { it.box.bottom <= MARGIN_BAND || it.box.top >= 1f - MARGIN_BAND }

    companion object {
        const val MIN_TEXT_CHARS = 25
        const val MAX_FULL_OCR_PAGES = 12

        /** Scanned pages read in all when following a scanned key from page to page. */
        const val MAX_FOLLOWED_OCR_PAGES = 60
        const val MIN_FOLLOWED_ANSWERS = 4

        /** Text this short, only in these bands and with no answers, is a header over a scan. */
        const val SPARSE_TEXT_CHARS = 200
        const val MARGIN_BAND = 0.12f
        const val MAX_MARGIN_OCR_PAGES = 400
        const val MAX_KEY_PAGES = 120
        const val MAX_REPORTED_PAGES = 15
        const val MAX_BUBBLE_PAGES = 20
        const val REPORT_VERSION = 2

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
