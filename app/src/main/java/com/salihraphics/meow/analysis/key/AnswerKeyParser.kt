package com.salihraphics.meow.analysis.key

import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextLine

/**
 * Finds answer keys in positioned page text (from the PDF or from OCR).
 *
 * It looks for two things:
 * - **Key sections**: a heading such as "Cevap Anahtarı", or a dense block of numbered answers,
 *   anywhere in the book (often at the end), split into tests by "Test 5"-style headers or by
 *   numbering that restarts.
 * - **Page margins**: compact answer lines ("5.B 6.D 7.A") in a page's header or footer.
 *
 * It only reports what is printed. Missing numbers are reported, never filled in, and anything
 * that looks like an OCR misread is kept but marked [Confidence.LOW].
 */
class AnswerKeyParser(
    /** Answer letters accepted. Books use A–E (YKS) or A–D (LGS); unused letters cost nothing. */
    private val choices: CharRange = 'A'..'E',
) {
    private data class Pair(val question: Int, val answer: String, val confidence: Confidence, val note: AnswerNote?)

    private enum class Zone { HEADER, BODY, FOOTER }

    private val choiceClass = "${choices.first}-${choices.last}${choices.first.lowercaseChar()}-${choices.last.lowercaseChar()}"

    /** "1. D", "1-D", "1) D", "1:D", "1/D", "1 D", "1D". The letter must stand alone, and not be an option label like "B)". */
    private val strictPair = Regex("(?<![\\p{L}\\p{N}])(\\d{1,3})\\s*([.)\\-:/]?)\\s*([$choiceClass])(?![\\p{L}\\p{N})])")

    /** OCR misreads, only with an explicit separator: "5.8" (B), "6-0" (D), "l.C" (1). */
    private val ocrPair = Regex("(?<![\\p{L}\\p{N}])([0-9lI]{1,3})\\s*([.)\\-:/])\\s*([${choiceClass}08OQ])(?![\\p{L}\\p{N})])")

    private val optionLabelStart = Regex("^[$choiceClass]\\s*[.)]")

    private val testHeader = Regex(
        "^(?:(\\d{1,3})\\s*\\.?\\s*)?" +
            "(test|deneme sinavi|deneme|konu testi|konu tarama|tarama testi|tarama|alistirma|bolum|unite|quiz)" +
            "(?![\\p{L}])\\s*[-:#.]?\\s*(\\d{1,3})?",
    )

    private val keyHeadings = listOf(
        "cevap anahtari", "cevap anahtarlari", "cevaplar", "yanitlar", "test cevaplari",
        "dogru cevaplar", "answer key", "answer keys", "answers",
    )

    fun parse(pages: List<PageText>): AnswerKeyParseResult {
        val keyPages = findKeySectionPages(pages)
        val questionPageTests = mapQuestionPagesToTests(pages, keyPages)

        val sectionTests = pages.filter { it.pageIndex in keyPages }.let { parseKeySection(it) }
        val marginTests = parseMargins(pages.filter { it.pageIndex !in keyPages }, questionPageTests)

        val pagesByTestNumber = questionPageTests.entries.groupBy({ it.value.number }, { it.key })
        val tests = sectionTests.map { builder ->
            val number = builder.ref?.number
            builder.build(KeySourceKind.KEY_SECTION, if (number != null) pagesByTestNumber[number].orEmpty().sorted() else emptyList())
        } + marginTests.map { it.build(KeySourceKind.PAGE_MARGIN, it.marginQuestionPages.sorted()) }
        return AnswerKeyParseResult(tests.filter { it.answers.isNotEmpty() })
    }

    // ---- Key sections ------------------------------------------------------------------------

    /** Pages with a key heading, plus following pages that continue the key, plus dense pages. */
    private fun findKeySectionPages(pages: List<PageText>): Set<Int> {
        val result = HashSet<Int>()
        var continuing = false
        for (page in pages.sortedBy { it.pageIndex }) {
            val answerLines = page.lines.count { answerLinePairs(it, lenient = true) != null }
            val dense = page.lines.count { (answerLinePairs(it, lenient = false)?.size ?: 0) >= 3 } >= 3
            val heading = page.lines.any { isKeyHeading(it.text) }
            continuing = when {
                heading -> true
                continuing && answerLines > 0 -> true
                else -> false
            }
            if (continuing || dense) result += page.pageIndex
        }
        return result
    }

    private fun parseKeySection(pages: List<PageText>): List<TestBuilder> {
        val tests = mutableListOf<TestBuilder>()
        var current: TestBuilder? = null

        fun startTest(ref: TestRef?): TestBuilder = TestBuilder(ref).also { tests += it; current = it }

        for (page in pages.sortedBy { it.pageIndex }) {
            val lines = columnOrder(page.lines)
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val text = TurkishText.normalizeSeparators(line.text)

                // Answer grid: a row of question numbers, then rows of letters (optionally labelled).
                val numbers = consecutiveNumbers(text)
                if (numbers != null) {
                    var j = i + 1
                    var consumed = false
                    while (j < lines.size) {
                        val row = gridRow(lines[j].text, numbers.size) ?: break
                        val target = when {
                            row.first != null -> startTest(row.first)
                            current == null -> startTest(null)
                            else -> current!!
                        }
                        row.second.forEachIndexed { k, letter -> target.add(Pair(numbers[k], letter, Confidence.HIGH, null), page.pageIndex) }
                        consumed = true
                        j++
                    }
                    if (consumed) {
                        i = j
                        continue
                    }
                }

                val header = parseTestHeader(text)
                val rest = header?.second ?: text
                if (header != null) startTest(header.first)
                val pairs = answerPairs(rest, lenient = true)
                if (pairs != null && pairs.isNotEmpty()) {
                    val target = current ?: startTest(null)
                    // Numbering restarting without a header means a new, unnamed test.
                    val restarted = header == null && target.lastQuestion != null && pairs.first().question < target.lastQuestion!! &&
                        pairs.first().question <= 2
                    (if (restarted) startTest(null) else target).let { t -> pairs.forEach { t.add(it, page.pageIndex) } }
                }
                i++
            }
        }
        return tests
    }

    // ---- Page margins ------------------------------------------------------------------------

    private fun parseMargins(pages: List<PageText>, pageTests: Map<Int, TestRef>): List<TestBuilder> {
        val byRef = LinkedHashMap<TestRef, TestBuilder>()
        val unnamed = mutableListOf<TestBuilder>()
        for (page in pages.sortedBy { it.pageIndex }) {
            for (line in marginRows(page)) {
                val text = TurkishText.normalizeSeparators(line.text)
                val header = parseTestHeader(text)
                val pairs = answerPairs(header?.second ?: text, lenient = false) ?: continue
                if (pairs.size < 2) continue
                val ref = header?.first ?: pageTests[page.pageIndex]
                val builder = if (ref != null) {
                    byRef.getOrPut(ref) { TestBuilder(ref) }
                } else {
                    // No test name: keep extending one test while numbering continues.
                    val last = unnamed.lastOrNull()
                    if (last != null && last.lastQuestion != null && pairs.first().question > last.lastQuestion!!) last
                    else TestBuilder(null).also { unnamed += it }
                }
                pairs.forEach { builder.add(it, page.pageIndex) }
                builder.marginQuestionPages += page.pageIndex
            }
        }
        return byRef.values.toList() + unnamed
    }

    /**
     * Header and footer lines, with pieces of one printed row joined again: answers spread across
     * the page width can arrive as several fragments.
     */
    private fun marginRows(page: PageText): List<TextLine> {
        val rows = mutableListOf<MutableList<TextLine>>()
        for (line in page.lines.filter { zoneOf(it) != Zone.BODY }.sortedBy { it.box.top }) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(row.first().box.top - line.box.top) < 0.012f) row += line else rows += mutableListOf(line)
        }
        return rows.map { row -> TextLine(row.sortedBy { it.box.left }.flatMap { it.words }) }
    }

    // ---- Question pages ----------------------------------------------------------------------

    /** "TEST 5" at the top of a question page names that page and the pages after it. */
    private fun mapQuestionPagesToTests(pages: List<PageText>, keyPages: Set<Int>): Map<Int, TestRef> {
        val result = HashMap<Int, TestRef>()
        var current: TestRef? = null
        for (page in pages.sortedBy { it.pageIndex }) {
            if (page.pageIndex in keyPages) {
                current = null
                continue
            }
            val header = page.lines
                .filter { it.box.top < 0.25f }
                .firstNotNullOfOrNull { line -> parseTestHeader(TurkishText.normalizeSeparators(line.text))?.takeIf { it.second.length < 30 } }
            if (header != null && header.first.number != null) current = header.first
            current?.let { result[page.pageIndex] = it }
        }
        return result
    }

    // ---- Line-level recognition --------------------------------------------------------------

    private fun zoneOf(line: TextLine) = when {
        line.box.bottom <= 0.10f -> Zone.HEADER
        line.box.top >= 0.88f -> Zone.FOOTER
        else -> Zone.BODY
    }

    fun isKeyHeading(text: String): Boolean {
        val folded = TurkishText.fold(text).trim()
        if (folded.count { !it.isWhitespace() } > 40) return false
        return keyHeadings.any { heading -> Regex("(^|[^\\p{L}])${Regex.escape(heading)}([^\\p{L}]|$)").containsMatchIn(folded) }
    }

    /** A test header at the start of [text], and whatever follows it. */
    fun parseTestHeader(text: String): kotlin.Pair<TestRef, String>? {
        val folded = TurkishText.fold(text)
        val match = testHeader.find(folded) ?: return null
        if (match.range.first != 0) return null
        val number = (match.groupValues[1].ifEmpty { match.groupValues[3] }).toIntOrNull()
        val label = text.substring(0, match.range.last + 1).trim().trimEnd(':', '-', '.', '#').trim()
        if (number == null && label.length < 3) return null
        return TestRef(number, label) to text.substring(match.range.last + 1).trim()
    }

    private fun answerLinePairs(line: TextLine, lenient: Boolean): List<Pair>? {
        val text = TurkishText.normalizeSeparators(line.text)
        return answerPairs(parseTestHeader(text)?.second ?: text, lenient)
    }

    /**
     * The answers on one line, or null if the line isn't an answer line. A line qualifies when
     * answers make up most of its text and their numbers mostly step up by one; this keeps
     * question text and option lines ("A) 1 B) 2") out.
     */
    private fun answerPairs(text: String, lenient: Boolean): List<Pair>? {
        if (text.isBlank() || optionLabelStart.containsMatchIn(text.trimStart())) return null
        val strict = strictPair.findAll(text).toList()
        val minimum = if (lenient) 1 else 2
        if (strict.size < minimum) return null

        val found = strict.map { m ->
            Pair(m.groupValues[1].toInt(), m.groupValues[3].uppercase(), Confidence.HIGH, null) to m.range
        }.toMutableList()
        // Second pass for OCR misreads, but only on a line already shown to be an answer line.
        if (strict.size >= 2) {
            ocrPair.findAll(text).filter { m -> found.none { it.second.first <= m.range.last && m.range.first <= it.second.last } }.forEach { m ->
                val number = m.groupValues[1].replace('l', '1').replace('I', '1').toIntOrNull() ?: return@forEach
                val answer = when (val c = m.groupValues[3].uppercase()) {
                    "8" -> "B"
                    "0", "O", "Q" -> "D"
                    else -> c
                }
                found += Pair(number, answer, Confidence.LOW, AnswerNote.OCR_GUESS) to m.range
            }
        }
        found.sortBy { it.second.first }

        val covered = found.sumOf { (_, range) -> text.substring(range).count { !it.isWhitespace() } }
        val total = text.count { !it.isWhitespace() && it != ',' && it != ';' }
        if (total == 0 || covered.toFloat() / total < if (lenient) 0.55f else 0.7f) return null

        val pairs = found.map { it.first }
        if (pairs.size >= 2) {
            val steps = pairs.zipWithNext().count { (a, b) -> b.question == a.question + 1 }
            if (steps.toFloat() / (pairs.size - 1) < 0.6f) return null
        }
        return pairs
    }

    /** "1 2 3 4 5" → [1, 2, 3, 4, 5] when the numbers count up by one. */
    private fun consecutiveNumbers(text: String): List<Int>? {
        val tokens = text.split(' ', ',', ';').filter { it.isNotBlank() }
        if (tokens.size < 3 || tokens.any { t -> t.any { !it.isDigit() } || t.length > 3 }) return null
        val numbers = tokens.map { it.toInt() }
        return numbers.takeIf { it.zipWithNext().all { (a, b) -> b == a + 1 } }
    }

    /** A grid row: an optional test label, then exactly [count] answer letters. */
    private fun gridRow(raw: String, count: Int): kotlin.Pair<TestRef?, List<String>>? {
        val text = TurkishText.normalizeSeparators(raw)
        val header = parseTestHeader(text)
        val tokens = (header?.second ?: text).split(' ', ',', ';').filter { it.isNotBlank() }
        if (tokens.size != count || tokens.any { it.length != 1 || it[0].uppercaseChar() !in choices }) return null
        return header?.first to tokens.map { it.uppercase() }
    }

    /** Two-column key pages are read column by column, so each column's tests stay together. */
    private fun columnOrder(lines: List<TextLine>): List<TextLine> {
        val left = lines.filter { it.box.right <= 0.55f }
        val right = lines.filter { it.box.left >= 0.45f }
        val twoColumns = left.size >= 3 && right.size >= 3 && left.size + right.size == lines.size
        return if (twoColumns) left.sortedBy { it.box.top } + right.sortedBy { it.box.top } else lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }

    // ---- Test assembly -----------------------------------------------------------------------

    private class TestBuilder(val ref: TestRef?) {
        private val entries = mutableListOf<kotlin.Pair<Pair, Int>>()
        private val sourcePages = sortedSetOf<Int>()
        val marginQuestionPages = sortedSetOf<Int>()
        var lastQuestion: Int? = null
            private set
        private var wentBackwards = false

        fun add(pair: Pair, page: Int) {
            lastQuestion?.let { if (pair.question < it && entries.none { e -> e.first.question == pair.question }) wentBackwards = true }
            entries += pair to page
            sourcePages += page
            lastQuestion = pair.question
        }

        fun build(kind: KeySourceKind, questionPages: List<Int>): DetectedTest {
            val issues = mutableListOf<TestIssue>()
            val answers = entries.groupBy { it.first.question }.toSortedMap().map { (question, found) ->
                val distinct = found.map { it.first.answer }.distinct()
                val first = found.first()
                if (distinct.size > 1) {
                    issues += TestIssue.Conflict(question, distinct)
                    DetectedAnswer(question, first.first.answer, Confidence.LOW, first.second, AnswerNote.CONFLICT, distinct)
                } else {
                    // Repeats with the same answer are just repeats; keep the most confident reading.
                    val best = found.minByOrNull { it.first.confidence.ordinal }!!
                    DetectedAnswer(question, best.first.answer, best.first.confidence, best.second, best.first.note)
                }
            }
            if (answers.isNotEmpty()) {
                val start = if (kind == KeySourceKind.KEY_SECTION) 1 else answers.first().question
                val present = answers.map { it.question }.toSet()
                val missing = (start..answers.last().question).filter { it !in present }
                if (missing.isNotEmpty()) issues += TestIssue.Missing(missing)
            }
            if (answers.size < 3) issues += TestIssue.TooFew
            if (wentBackwards) issues += TestIssue.OutOfOrder
            return DetectedTest(ref, kind, sourcePages.toList(), questionPages, answers, issues)
        }
    }
}
