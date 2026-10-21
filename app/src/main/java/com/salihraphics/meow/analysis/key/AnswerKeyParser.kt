package com.salihraphics.meow.analysis.key

import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextLine

/**
 * Finds answer keys in positioned page text (from the PDF or from OCR).
 *
 * It looks for:
 * - **Key sections**: a heading such as "Cevap Anahtarı", a dense block of numbered answers, or
 *   pages the student pointed at, anywhere in the book (often at the end). They are split into
 *   tests by "Test 5"-style headers, into days or units by "1. GÜN" / "2. Ünite" headers, or by
 *   numbering that restarts.
 * - **Answer tables among the questions**: a row of numbers with a row of letters under it,
 *   typically at the end of each test.
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
            "(test|deneme sinavi|deneme|konu testi|konu tarama|tarama testi|tarama|alistirma|pekistirme testi|pekistirme|" +
            "kazanim testi|karma test|genel tekrar testi|genel tekrar|quiz)" +
            "(?![\\p{L}])\\s*[-:#.]?\\s*(\\d{1,3})?",
    )

    /** Days and units that group tests: "1. GÜN", "GÜN 12", "2. Ünite", "Bölüm 3". */
    private val sectionHeader = Regex(
        "^(?:(\\d{1,3})\\s*\\.?\\s*)?(gun|unite|bolum|modul|hafta|kisim)(?![\\p{L}])\\s*[-:#.]?\\s*(\\d{1,3})?",
    )

    private val keyHeadings = listOf(
        "cevap anahtari", "cevap anahtarlari", "cevaplar", "yanitlar", "test cevaplari",
        "dogru cevaplar", "answer key", "answer keys", "answers",
    )

    /** Words that label a row of a printed answer table: "Soru 1 2 3", "Cevap C A D". */
    private val rowLabels = setOf("soru", "no", "sorular", "cevap", "cevaplar", "yanit", "yanitlar", "answer", "answers", "key")

    /**
     * @param forcedKeyPages pages known to hold the key (the student pointed at them, or they come
     *   from a separate answer-key PDF): read as key sections whether or not they look like one.
     */
    fun parse(pages: List<PageText>, forcedKeyPages: Set<Int> = emptySet()): AnswerKeyParseResult {
        val sorted = pages.sortedBy { it.pageIndex }
        val keyPages = findKeySectionPages(sorted) + forcedKeyPages
        val pureKeyPages = sorted.filter { it.pageIndex in forcedKeyPages || (it.pageIndex in keyPages && isMostlyAnswers(it)) }
            .map { it.pageIndex }.toSet()
        val paths = mapQuestionPages(sorted, pureKeyPages)

        val sectionTests = parseKeySection(sorted.filter { it.pageIndex in keyPages }).map { it.build(KeySourceKind.KEY_SECTION) }
        val sectionRefs = sectionTests.mapNotNull { it.ref }
        val linkedSection = sectionTests.map { test ->
            val ref = test.ref ?: return@map test
            test.copy(questionPages = paths.filter { (_, path) -> sectionRefs.count { matches(it, path) } == 1 && matches(ref, path) }.keys.sorted())
        }
        val others = sorted.filter { it.pageIndex !in keyPages }
        val tableTests = parseAnswerTables(others, paths)
        val marginTests = parseMargins(others, paths).map { it.build(KeySourceKind.PAGE_MARGIN, it.marginQuestionPages.sorted()) }

        val clues = KeyClues(
            headingPages = sorted.filter { p -> p.lines.any { isKeyHeading(it.text) } }.map { it.pageIndex },
            keyPages = keyPages.sorted(),
            answerLines = sorted.sumOf { p -> p.lines.count { answerLinePairs(it, lenient = false) != null } },
        )
        return AnswerKeyParseResult((linkedSection + tableTests + marginTests).filter { it.answers.isNotEmpty() }, clues)
    }

    // ---- Key sections ------------------------------------------------------------------------

    /** Pages with a key heading, plus following pages that continue the key, plus dense pages. */
    private fun findKeySectionPages(pages: List<PageText>): Set<Int> {
        val result = HashSet<Int>()
        var continuing = false
        for (page in pages) {
            val answerLines = page.lines.count { answerLinePairs(it, lenient = true) != null }
            val rich = page.lines.count { (answerLinePairs(it, lenient = false)?.size ?: 0) >= 3 } >= 3
            // A table of one answer per line ("1. C" / "2. A" …) is dense too.
            val column = answerLines >= 8 && answerLines * 2 >= page.lines.size
            val heading = page.lines.any { isKeyHeading(it.text) }
            continuing = when {
                heading -> true
                continuing && (answerLines > 0 || page.lines.any { isTableLine(it.text) }) -> true
                else -> false
            }
            if (continuing || rich || column) result += page.pageIndex
        }
        return result
    }

    /** A page that is mostly answers (not a question page with a small key on it). */
    private fun isMostlyAnswers(page: PageText): Boolean {
        val keyish = page.lines.count { line -> answerLinePairs(line, lenient = true) != null || isTableLine(line.text) || isKeyHeading(line.text) }
        return keyish * 5 >= page.lines.size * 2
    }

    private fun parseKeySection(pages: List<PageText>): List<TestBuilder> {
        val tests = mutableListOf<TestBuilder>()
        var current: TestBuilder? = null
        var section: TestRef? = null

        fun startTest(ref: TestRef?): TestBuilder = TestBuilder(ref).also { tests += it; current = it }

        for (page in pages) {
            val lines = columnOrder(page.lines)
            var i = 0
            while (i < lines.size) {
                val text = TurkishText.normalizeSeparators(lines[i].text)

                // Answer grid: a row of question numbers, then rows of letters (optionally labelled).
                val numbers = consecutiveNumbers(text)
                if (numbers != null) {
                    var j = i + 1
                    var consumed = false
                    while (j < lines.size) {
                        val row = gridRow(lines[j].text, numbers.size) ?: break
                        val target = when {
                            row.first != null -> startTest(within(section, row.first!!))
                            current == null -> startTest(section)
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

                var rest = text
                parseSectionHeader(rest)?.let { (ref, after) ->
                    section = ref
                    current = null
                    rest = after
                }
                val header = parseTestHeader(rest)
                if (header != null) {
                    rest = header.second
                    startTest(within(section, header.first))
                }
                val pairs = answerPairs(rest, lenient = true)
                if (pairs != null && pairs.isNotEmpty()) {
                    val target = current ?: startTest(section)
                    // Numbering restarting without a header means a new, unnamed test.
                    val restarted = header == null && target.lastQuestion != null && pairs.first().question < target.lastQuestion!! &&
                        pairs.first().question <= 2
                    val into = if (restarted) startTest(section?.let { TestRef(null, "", it.section) }) else target
                    pairs.forEach { into.add(it, page.pageIndex) }
                }
                i++
            }
        }
        return tests
    }

    /** "TEST 2" under "1. GÜN" is "1. GÜN · TEST 2". */
    private fun within(section: TestRef?, test: TestRef): TestRef =
        if (section == null) test else TestRef(test.number, "${section.label} · ${test.label}", section.section)

    // ---- Answer tables among the questions ---------------------------------------------------

    /**
     * Tables printed on question pages, usually closing a test: "1 2 3 … 16" over "C A D … B".
     * Each belongs to the test named on its page, or else to the pages since the previous table.
     */
    private fun parseAnswerTables(pages: List<PageText>, paths: Map<Int, TestRef>): List<DetectedTest> {
        val found = mutableListOf<DetectedTest>()
        var previousTablePage = -1
        for (page in pages) {
            val lines = columnOrder(page.lines)
            val builders = mutableListOf<TestBuilder>()
            var i = 0
            while (i < lines.size) {
                val numbers = consecutiveNumbers(TurkishText.normalizeSeparators(lines[i].text))?.takeIf { it.size >= 4 }
                val row = if (numbers != null && i + 1 < lines.size) gridRow(lines[i + 1].text, numbers.size) else null
                if (numbers != null && row != null) {
                    val ref = row.first ?: paths[page.pageIndex]
                    // A table continuing the previous one (questions 9–16 after 1–8) is the same test.
                    val b = builders.lastOrNull()?.takeIf { it.ref == ref && it.lastQuestion == numbers.first() - 1 }
                        ?: TestBuilder(ref).also { builders += it }
                    row.second.forEachIndexed { k, letter -> b.add(Pair(numbers[k], letter, Confidence.HIGH, null), page.pageIndex) }
                    i += 2
                    continue
                }
                i++
            }
            if (builders.isEmpty()) continue
            for (b in builders) {
                val path = b.ref
                val questionPages = if (path != null) {
                    paths.filter { (_, p) -> p == path }.keys.sorted().ifEmpty { listOf(page.pageIndex) }
                } else {
                    ((previousTablePage + 1)..page.pageIndex).toList()
                }
                found += b.build(KeySourceKind.IN_TEST, questionPages)
            }
            previousTablePage = page.pageIndex
        }
        return found
    }

    // ---- Page margins ------------------------------------------------------------------------

    private fun parseMargins(pages: List<PageText>, pageTests: Map<Int, TestRef>): List<TestBuilder> {
        val byRef = LinkedHashMap<TestRef, TestBuilder>()
        val unnamed = mutableListOf<TestBuilder>()
        for (page in pages) {
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

    /**
     * Which test each question page belongs to, from headers near the top: "1. GÜN" (a section)
     * and "TEST 2". A test stays current on the pages after its header until the next one.
     */
    private fun mapQuestionPages(pages: List<PageText>, keyPages: Set<Int>): Map<Int, TestRef> {
        val result = HashMap<Int, TestRef>()
        var section: TestRef? = null
        var test: TestRef? = null
        for (page in pages) {
            if (page.pageIndex in keyPages) {
                section = null
                test = null
                continue
            }
            val top = page.lines.filter { it.box.top < 0.25f }.map { TurkishText.normalizeSeparators(it.text) }
            val newSection = top.firstNotNullOfOrNull { line -> parseSectionHeader(line)?.takeIf { it.second.length < 40 }?.first }
            if (newSection != null && newSection.section != section?.section) {
                section = newSection
                test = null
            }
            val header = top.firstNotNullOfOrNull { line ->
                val afterSection = parseSectionHeader(line)?.second ?: line
                parseTestHeader(afterSection)?.takeIf { it.second.length < 30 }?.first
            }
            if (header != null && header.number != null) test = header
            val path = test?.let { within(section, it) } ?: section
            path?.let { result[page.pageIndex] = it }
        }
        return result
    }

    /** Does a key test (from a key section) cover a question page with this [path]? */
    private fun matches(key: TestRef, path: TestRef): Boolean {
        if (key.section != null && key.section != path.section) return false
        return when {
            key.number != null -> key.number == path.number
            // A day or unit with no tests under it: all its pages.
            else -> key.section != null && key.label.isNotEmpty()
        }
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
        val (number, label, rest) = header(testHeader, text) ?: return null
        return if (number == null && label.length < 3) null else TestRef(number, label) to rest
    }

    /** A day or unit header ("1. GÜN") at the start of [text], and whatever follows it. */
    fun parseSectionHeader(text: String): kotlin.Pair<TestRef, String>? {
        val (number, label, rest) = header(sectionHeader, text) ?: return null
        if (number == null) return null
        val word = sectionHeader.find(TurkishText.fold(text))!!.groupValues[2]
        return TestRef(null, label, "$word#$number") to rest
    }

    private fun header(regex: Regex, text: String): Triple<Int?, String, String>? {
        val match = regex.find(TurkishText.fold(text)) ?: return null
        if (match.range.first != 0) return null
        val number = (match.groupValues[1].ifEmpty { match.groupValues[3] }).toIntOrNull()
        val label = text.substring(0, match.range.last + 1).trim().trimEnd(':', '-', '.', '#').trim()
        return Triple(number, label, text.substring(match.range.last + 1).trim().trimStart(':', '-').trim())
    }

    private fun answerLinePairs(line: TextLine, lenient: Boolean): List<Pair>? {
        var text = TurkishText.normalizeSeparators(line.text)
        parseSectionHeader(text)?.let { text = it.second }
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

    private fun tokens(text: String) = text.split(' ', ',', ';').filter { it.isNotBlank() }

    /** Drops a leading row label ("Soru", "Cevap") or test name from a table row. */
    private fun withoutRowLabel(text: String): kotlin.Pair<TestRef?, List<String>> {
        val normalized = TurkishText.normalizeSeparators(text)
        parseTestHeader(normalized)?.let { (ref, rest) -> return ref to tokens(rest) }
        val all = tokens(normalized)
        val labels = all.takeWhile { TurkishText.fold(it).trimEnd(':', '.') in rowLabels }
        return null to all.drop(labels.size)
    }

    /** "1 2 3 4 5" (optionally "Soru 1 2 3 …") → [1, 2, 3, 4, 5] when the numbers count up by one. */
    private fun consecutiveNumbers(text: String): List<Int>? {
        val (ref, tokens) = withoutRowLabel(text)
        if (ref != null || tokens.size < 3 || tokens.any { t -> t.any { !it.isDigit() } || t.length > 3 }) return null
        val numbers = tokens.map { it.toInt() }
        return numbers.takeIf { it.zipWithNext().all { (a, b) -> b == a + 1 } }
    }

    /** A grid row: an optional test label or row label, then exactly [count] answer letters. */
    private fun gridRow(raw: String, count: Int): kotlin.Pair<TestRef?, List<String>>? {
        val (ref, tokens) = withoutRowLabel(raw)
        if (tokens.size != count || tokens.any { it.length != 1 || it[0].uppercaseChar() !in choices }) return null
        return ref to tokens.map { it.uppercase() }
    }

    private fun isTableLine(text: String): Boolean {
        val (_, tokens) = withoutRowLabel(text)
        return tokens.size >= 4 && (consecutiveNumbers(text) != null || tokens.all { it.length == 1 && it[0].uppercaseChar() in choices })
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

        fun build(kind: KeySourceKind, questionPages: List<Int> = emptyList()): DetectedTest {
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
                val start = if (kind == KeySourceKind.PAGE_MARGIN) answers.first().question else 1
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
