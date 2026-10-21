package com.salihraphics.meow.analysis.key

import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextLine
import com.salihraphics.meow.analysis.TextWord

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
 * - **Page margins**: compact answer lines ("5.B 6.D 7.A") in a page's header or footer, kept
 *   with the page's test only when their numbers match the questions printed on it.
 * - **Labelled answers**: "CEVAP: A", "Doğru cevap: D" printed with a question, and explicit
 *   answers in worked solutions ("Çözümler" pages), each given to the question number printed
 *   above it in its column.
 * - **Image readings** handed in by the analyzer (filled bubbles of an optical answer sheet).
 *
 * Each finder works on its own; [combine] then joins what they found for the same test, keeps
 * where every answer came from, and marks disagreements as conflicts instead of choosing.
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

    /** A letter answer directly followed by the next numbered answer. */
    private val compactBoundary = Regex("(?<=\\d[.)\\-:/]?)([$choiceClass])(?=\\d{1,3}[.)\\-:/]?[$choiceClass](?![\\p{L}]))")

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
    /**
     * @param imageReadings answers read from page images (filled bubbles), per page and question;
     *   pages carry them as answer lines and they are marked [AnswerNote.FROM_IMAGE].
     */
    fun parse(
        pages: List<PageText>,
        forcedKeyPages: Set<Int> = emptySet(),
        imageReadings: Map<Int, Map<Int, String>> = emptyMap(),
    ): AnswerKeyParseResult {
        this.imageReadings = imageReadings
        val original = pages.sortedBy { it.pageIndex }
        val prepared = original.map { prepare(it) }
        tableLines = prepared.associate { it.page.pageIndex to it.tableLines }
        val sorted = prepared.map { it.page }
        val keyPages = findKeySectionPages(sorted) + forcedKeyPages
        val pureKeyPages = sorted.filter { it.pageIndex in forcedKeyPages || (it.pageIndex in keyPages && isMostlyAnswers(it)) }
            .map { it.pageIndex }.toSet()
        val solutionPages = findSolutionPages(original, keyPages)
        val paths = mapQuestionPages(sorted, pureKeyPages)
        // Solutions name their test ("TEST 3 ÇÖZÜMLERİ") but hold no questions to solve.
        val questionPaths = paths.filterKeys { it !in solutionPages }
        // Question numbers printed on each page, from the text as extracted (column by column).
        val labels = original.associate { it.pageIndex to questionLabels(it) }

        val sectionTests = parseKeySection(sorted.filter { it.pageIndex in keyPages }).map { it.build(KeySourceKind.KEY_SECTION) }
        val sectionRefs = sectionTests.mapNotNull { it.ref }
        val linkedSection = sectionTests.map { test ->
            val ref = test.ref ?: return@map test
            test.copy(questionPages = questionPaths.filter { (_, path) -> sectionRefs.count { matches(it, path) } == 1 && matches(ref, path) }.keys.sorted())
        }
        val others = sorted.filter { it.pageIndex !in keyPages }
        val tableTests = parseAnswerTables(others, questionPaths)
        val margins = parseMargins(others.filter { it.pageIndex !in solutionPages }, questionPaths, labels)
        val marginTests = margins.first.map { it.build(KeySourceKind.PAGE_MARGIN, it.marginQuestionPages.sorted()) }
        // Labels are read everywhere, pages the student pointed at included.
        val labelled = parseLabelled(original, paths, labels, solutionPages)

        val clues = KeyClues(
            headingPages = sorted.filter { p -> p.lines.any { isKeyHeading(it.text) } }.map { it.pageIndex },
            keyPages = keyPages.sorted(),
            answerLines = sorted.sumOf { p -> p.lines.count { answerLinePairs(it, lenient = false) != null } },
            labelledAnswers = labelled.placed,
            unplacedLabels = labelled.unplaced,
            solutionPages = solutionPages.sorted(),
            marginLinesOutOfContext = margins.second,
        )
        val found = (linkedSection + tableTests + marginTests + labelled.tests).filter { it.answers.isNotEmpty() }
        return AnswerKeyParseResult(combine(found), clues)
    }

    private var imageReadings: Map<Int, Map<Int, String>> = emptyMap()

    private companion object {
        /** The widest step between neighbouring answers on one line ("4.B 7.D": 5 and 6 missing). */
        const val MAX_GAP = 3
    }

    private val tables = KeyTableReader(choices) { parseTestHeader(it) }

    /** Per page, the lines that were made from answer tables (see [prepare]). */
    private var tableLines: Map<Int, Set<TextLine>> = emptyMap()

    /**
     * The page's lines in the reading that gives the most answers: column by column as
     * extracted, or whole printed rows (table cells spaced wider than a word gap come apart into
     * separate "lines" otherwise). Either way, answer tables become answer lines.
     */
    private fun prepare(page: PageText): Prepared {
        val byColumn = tables.rewrite(columnOrder(page.lines))
        val byRow = tables.rewrite(tables.visualRows(page))
        val rows = answersIn(byRow.first) > answersIn(byColumn.first)
        val best = if (rows) byRow else byColumn
        return Prepared(PageText(page.pageIndex, best.first, page.source), best.second, rows)
    }

    private class Prepared(val page: PageText, val tableLines: Set<TextLine>, val byRows: Boolean)

    private fun answersIn(lines: List<TextLine>) = lines.sumOf { answerLinePairs(it, lenient = true)?.size ?: 0 }

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
                    var into = if (restarted) startTest(section?.let { TestRef(null, "", it.section) }) else target
                    var previous: Int? = null
                    for (pair in pairs) {
                        // The same within a line: "1.A 2.B 3.C 4.D 1.E 2.A" is two tests.
                        if (previous != null && pair.question < previous && pair.question <= 2) into = startTest(section?.let { TestRef(null, "", it.section) })
                        into.add(pair, page.pageIndex)
                        previous = pair.question
                    }
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
            val made = tableLines[page.pageIndex].orEmpty()
            val builders = mutableListOf<TestBuilder>()
            for (line in columnOrder(page.lines).filter { it in made }) {
                val text = TurkishText.normalizeSeparators(line.text)
                val header = parseTestHeader(text)
                val pairs = answerPairs(header?.second ?: text, lenient = true) ?: continue
                val ref = header?.first ?: paths[page.pageIndex]
                // A table continuing the previous one (questions 9–16 after 1–8) is the same test.
                val b = builders.lastOrNull()?.takeIf { it.ref == ref && it.lastQuestion == pairs.first().question - 1 }
                    ?: TestBuilder(ref).also { builders += it }
                pairs.forEach { b.add(it, page.pageIndex) }
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

    /**
     * Answer lines in headers and footers. A line is the page's own key only when its numbers
     * match questions printed on the page; answers to another section printed in the margin
     * (numbers that aren't on this page) keep their own name if they print one, or stay unnamed.
     * Returns the tests and how many lines were out of the page's context.
     */
    private fun parseMargins(pages: List<PageText>, pageTests: Map<Int, TestRef>, labels: Map<Int, List<QuestionLabel>>): kotlin.Pair<List<TestBuilder>, Int> {
        val byRef = LinkedHashMap<TestRef, TestBuilder>()
        val unnamed = mutableListOf<TestBuilder>()
        var outOfContext = 0
        for (page in pages) {
            val printed = labels[page.pageIndex].orEmpty().map { it.number }.toSet()
            for (line in marginRows(page)) {
                val text = TurkishText.normalizeSeparators(line.text)
                val header = parseTestHeader(text)
                val pairs = answerPairs(header?.second ?: text, lenient = false) ?: continue
                if (pairs.size < 2) continue
                val ownPage = printed.isEmpty() || pairs.any { it.question in printed }
                if (!ownPage) outOfContext++
                val ref = header?.first ?: pageTests[page.pageIndex]?.takeIf { ownPage }
                val builder = if (ref != null) {
                    byRef.getOrPut(ref) { TestBuilder(ref) }
                } else {
                    // No test name: keep extending one test while numbering continues.
                    val last = unnamed.lastOrNull()
                    if (last != null && last.lastQuestion != null && pairs.first().question > last.lastQuestion!!) last
                    else TestBuilder(null).also { unnamed += it }
                }
                pairs.forEach { builder.add(it, page.pageIndex) }
                if (ownPage) builder.marginQuestionPages += page.pageIndex
            }
        }
        return (byRef.values.toList() + unnamed) to outOfContext
    }

    // ---- Labelled answers and worked solutions -----------------------------------------------

    /** A question number printed at the start of a line ("7." / "7)"), with its position. */
    private class QuestionLabel(val number: Int, val top: Float, val left: Float)

    /** A test (or day/unit) header printed on a page, where the answers under it belong. */
    private class PageHeader(val ref: TestRef, val top: Float, val left: Float)

    /** "7." or "7)", alone or fused to the text that follows ("7.Verilen"). */
    private val questionNumber = Regex("^(\\d{1,3})[.)](?:\\p{L}.*)?$")

    /**
     * "CEVAP: A", "Cevap C", "Doğru cevap: D", "Doğru seçenek E", "Yanıt: B" (matched on folded
     * text; the letter must be printed as a capital, so "cevap a ve b" in a sentence doesn't count).
     */
    private val answerLabel = Regex("(?<![\\p{L}])(dogru cevap|dogru secenek|dogru yanit|cevap|yanit)\\s*[:=\\-]?\\s*\\(?([a-e])\\)?(?![\\p{L}\\p{N}])")

    /**
     * Question numbers at the left edge of their column (numbered steps inside a solution are
     * indented, so they drop out). Numbers near the top of the page count too; footers don't.
     */
    private fun questionLabels(page: PageText): List<QuestionLabel> {
        val candidates = page.lines.filter { zoneOf(it) != Zone.FOOTER && answerLinePairs(it, lenient = true) == null }.mapNotNull { line ->
            val first = line.words.firstOrNull() ?: return@mapNotNull null
            val number = questionNumber.find(first.text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..300 } ?: return@mapNotNull null
            val text = TurkishText.normalizeSeparators(line.text)
            // Headers are not questions: "1. GÜN", "2. TEST", "TEST 1 …".
            if (parseSectionHeader(text) != null || parseTestHeader(text) != null) return@mapNotNull null
            if (parseTestHeader(TurkishText.normalizeSeparators(line.words.drop(1).joinToString(" ") { it.text }))?.second?.isEmpty() == true) return@mapNotNull null
            QuestionLabel(number, line.box.top, first.box.left)
        }
        val twoColumns = twoColumns(candidates.map { it.left })
        return candidates.groupBy { if (twoColumns) columnOf(it.left) else 0 }.flatMap { (_, column) ->
            val margin = column.minOf { it.left }
            column.filter { it.left - margin < 0.02f }
        }
    }

    private fun columnOf(left: Float) = if (left < 0.45f) 0 else 1

    /** Question numbers printed in both halves of the page. */
    private fun twoColumns(lefts: List<Float>) = lefts.any { it < 0.45f } && lefts.any { it >= 0.5f }

    /** Test and day/unit headers on a page, in reading order, each with its section. */
    private fun pageHeaders(page: PageText): List<PageHeader> {
        var section: TestRef? = null
        return page.lines.sortedBy { it.box.top }.mapNotNull { line ->
            val text = TurkishText.normalizeSeparators(line.text)
            val sectionHeader = parseSectionHeader(text)?.takeIf { it.second.length < 40 }
            if (sectionHeader != null) section = sectionHeader.first
            val rest = sectionHeader?.second ?: text
            val test = parseTestHeader(rest)?.takeIf { it.second.length < 30 && it.first.number != null }?.first
            when {
                test != null -> PageHeader(within(section, test), line.box.top, line.box.left)
                sectionHeader != null -> PageHeader(sectionHeader.first, line.box.top, line.box.left)
                else -> null
            }
        }
    }

    /** "Çözümler" pages: a solutions heading near the top, and the pages that go on with it. */
    private fun findSolutionPages(pages: List<PageText>, keyPages: Set<Int>): Set<Int> {
        val result = HashSet<Int>()
        var continuing = false
        for (page in pages) {
            val heading = page.lines.filter { it.box.top < 0.25f }.any { line ->
                val folded = TurkishText.fold(line.text)
                folded.length < 40 && Regex("(?<![\\p{L}])cozum(ler|leri|lu)?(?![\\p{L}])").containsMatchIn(folded)
            }
            val labelled = tables.visualRows(page).any { answerLabel.containsMatchIn(TurkishText.fold(it.text)) }
            continuing = heading || (continuing && labelled && page.pageIndex !in keyPages)
            if (continuing) result += page.pageIndex
        }
        return result
    }

    private class Labelled(val tests: List<DetectedTest>, val placed: Int, val unplaced: Int)

    /**
     * Answers printed with their question ("CEVAP: D"). Each label is read on its whole printed
     * row (the letter may stand apart) and given to the nearest question number above it, within
     * the same test: a test header on the page starts a new run of numbers, and numbers must
     * count up within a run (a solution's numbered steps don't). Columns matter only on pages
     * with question numbers in two columns.
     *
     * The test is the header above the label on the page; on solution pages, otherwise the one
     * the solutions section last named. Solutions are never given the test of the question pages
     * before them, and a label with no question number above it is not used.
     */
    private fun parseLabelled(pages: List<PageText>, paths: Map<Int, TestRef>, labels: Map<Int, List<QuestionLabel>>, solutionPages: Set<Int>): Labelled {
        val builders = LinkedHashMap<kotlin.Pair<TestRef?, KeySourceKind>, TestBuilder>()
        var placed = 0
        var unplaced = 0
        var solutionContext: TestRef? = null
        for (page in pages) {
            val solution = page.pageIndex in solutionPages
            if (!solution) solutionContext = null
            val kind = if (solution) KeySourceKind.SOLUTION else KeySourceKind.LABELLED
            val numbers = labels[page.pageIndex].orEmpty()
            val headers = pageHeaders(page)
            val split = twoColumns(numbers.map { it.left })
            fun col(left: Float): Int = if (split) columnOf(left) else 0
            // The header governing a position: the nearest above it in its column (or, in the
            // right column, the last one of the left column), else the page's context.
            val base = if (solution) solutionContext else paths[page.pageIndex]
            fun headerAt(top: Float, left: Float): PageHeader? {
                val above = headers.filter { it.top <= top + 0.002f }
                return above.filter { col(it.left) == col(left) }.maxByOrNull { it.top }
                    ?: above.filter { col(it.left) < col(left) }.maxByOrNull { it.top }
            }
            // Numbers must count up within one test's run in one column.
            val runs = numbers.groupBy { col(it.left) to headerAt(it.top, it.left) }.flatMap { (_, run) ->
                var last = 0
                run.sortedBy { it.top }.filter { label -> (label.number > last).also { if (it) last = label.number } }
            }
            for (row in tables.visualRows(page)) {
                val folded = TurkishText.fold(row.text)
                for (match in answerLabel.findAll(folded)) {
                    val letter = row.text[match.groups[2]!!.range.first]
                    if (!letter.isUpperCase()) continue
                    val word = wordAt(row, match.range.first)
                    val header = headerAt(word.box.top, word.box.left)
                    val question = runs.filter { col(it.left) == col(word.box.left) && it.top <= word.box.top + 0.005f }
                        .filter { headerAt(it.top, it.left) === header }
                        .maxByOrNull { it.top }
                    if (question == null) {
                        unplaced++
                        continue
                    }
                    placed++
                    val ref = header?.ref ?: base
                    val builder = builders.getOrPut(ref to kind) { TestBuilder(ref) }
                    builder.add(Pair(question.number, letter.toString(), Confidence.HIGH, null), page.pageIndex)
                    if (kind == KeySourceKind.LABELLED) builder.marginQuestionPages += page.pageIndex
                }
            }
            if (solution) headers.lastOrNull()?.let { solutionContext = it.ref }
        }
        val tests = builders.map { (key, b) -> b.build(key.second, b.marginQuestionPages.sorted()) }
        return Labelled(tests, placed, unplaced)
    }

    /** The word of [line] that the character at [index] of its text falls in. */
    private fun wordAt(line: TextLine, index: Int): TextWord {
        var start = 0
        for (word in line.words) {
            if (index < start + word.text.length) return word
            start += word.text.length + 1
        }
        return line.words.last()
    }

    // ---- Combining the finders ---------------------------------------------------------------

    /**
     * Joins what different finders found for the same test: the same name (a section and number,
     * or label), or an unnamed test whose question pages overlap exactly one named test's. Per
     * question, agreeing readings merge (keeping every provenance); disagreeing ones become a
     * conflict listing each value and where it was read.
     */
    private fun combine(tests: List<DetectedTest>): List<DetectedTest> {
        val groups = LinkedHashMap<String, MutableList<DetectedTest>>()
        val unnamed = mutableListOf<DetectedTest>()
        for (test in tests) {
            val key = identity(test.ref)
            if (key == null) unnamed += test else groups.getOrPut(key) { mutableListOf() } += test
        }
        val loose = mutableListOf<DetectedTest>()
        for (test in unnamed) {
            val overlapping = groups.values.filter { group -> group.any { g -> g.questionPages.any { it in test.questionPages } } }
            if (test.questionPages.isNotEmpty() && overlapping.size == 1) overlapping.single() += test else loose += test
        }
        // In the order they were found (by the first part of each), as the book lists them.
        val order = java.util.IdentityHashMap<DetectedTest, Int>().apply { tests.forEachIndexed { i, t -> put(t, i) } }
        return (groups.values.map { group -> order.getValue(group.first()) to merge(group) } + loose.map { order.getValue(it) to it })
            .sortedBy { it.first }.map { it.second }
    }

    private fun identity(ref: TestRef?): String? = when {
        ref == null -> null
        ref.number != null -> "${ref.section}#${kindOf(ref)}#${ref.number}"
        ref.label.isNotBlank() -> "${ref.section}#${TurkishText.fold(ref.label)}"
        else -> null
    }

    private fun merge(parts: List<DetectedTest>): DetectedTest {
        if (parts.size == 1) return parts.single()
        val kind = parts.minOf { it.kind.ordinal }.let { KeySourceKind.entries[it] }
        val answers = parts.flatMap { it.answers }.groupBy { it.question }.toSortedMap().map { (question, readings) ->
            val provenance = readings.flatMap { it.provenance }.distinct()
            val values = provenance.map { it.answer.uppercase() }.distinct()
            val best = readings.minBy { it.confidence.ordinal }
            if (values.size > 1) {
                DetectedAnswer(question, best.answer, Confidence.LOW, best.sourcePage, AnswerNote.CONFLICT, values, provenance)
            } else {
                // Readings that agree: any reading from text outweighs an image or OCR guess.
                val firm = readings.firstOrNull { it.confidence == Confidence.HIGH } ?: best
                DetectedAnswer(question, firm.answer, firm.confidence, firm.sourcePage, firm.note, emptyList(), provenance)
            }
        }
        val ref = parts.firstOrNull { it.kind == kind }?.ref ?: parts.first().ref
        return DetectedTest(
            ref, kind, parts.flatMap { it.sourcePages }.distinct().sorted(), parts.flatMap { it.questionPages }.distinct().sorted(),
            answers, issuesOf(answers, kind, parts.any { TestIssue.OutOfOrder in it.issues }),
        )
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
            key.number != null -> key.number == path.number && kindOf(key) == kindOf(path)
            // A day or unit with no tests under it: all its pages.
            else -> key.section != null && key.label.isNotEmpty()
        }
    }

    /**
     * The kind of test a name says ("test", "deneme", "konu tarama"…), so "Deneme 1" is never
     * "Test 1". "Deneme Sınavı" and "Deneme" are one kind, as are "Konu Testi" and "Konu".
     */
    private fun kindOf(ref: TestRef): String {
        val name = TurkishText.fold(ref.label.substringAfterLast(" · "))
        val kind = testHeader.find(name)?.takeIf { it.range.first == 0 }?.groupValues?.get(2) ?: return ""
        return kind.removeSuffix(" sinavi").removeSuffix(" testi").ifEmpty { kind }
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
    private fun answerPairs(raw: String, lenient: Boolean): List<Pair>? {
        if (raw.isBlank() || optionLabelStart.containsMatchIn(raw.trimStart())) return null
        // "1.C2.A3.D" / "1C2A3D": answers printed without spaces get their spaces back.
        val text = compactBoundary.replace(raw, "$1 ")
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
            // Numbers count up, allowing small gaps (an answer printed as an image, or missing) and
            // repeats (the same question listed twice): gaps are reported later as missing
            // questions and repeats as conflicts, never filled in or settled.
            val steps = pairs.zipWithNext().count { (a, b) -> b.question - a.question in 0..MAX_GAP }
            if (steps.toFloat() / (pairs.size - 1) < 0.75f) return null
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

    // ---- Diagnostics -------------------------------------------------------------------------

    /**
     * What this parser sees on one page, for explaining a miss: headings, answer-like lines and
     * why lines that almost looked like answers were turned down. Only short, key-like excerpts.
     */
    fun inspect(page: PageText): PageInspection {
        val prepared = prepare(page)
        val lines = prepared.page.lines
        val made = prepared.tableLines
        val texts = lines.map { TurkishText.normalizeSeparators(it.text) }
        val rejected = texts.mapNotNull { text -> rejectionOf(text)?.let { excerpt(text) to it } }.take(3)
        return PageInspection(
            pageIndex = page.pageIndex,
            lines = lines.size,
            byRows = prepared.byRows,
            keyHeadings = lines.filter { isKeyHeading(it.text) }.map { excerpt(it.text) }.take(2),
            testHeaders = texts.mapNotNull { t -> (parseSectionHeader(t)?.second ?: t).let { parseTestHeader(it)?.first?.label } }.distinct().take(6),
            sections = texts.mapNotNull { parseSectionHeader(it)?.first?.label }.distinct().take(4),
            answerLines = lines.count { answerLinePairs(it, lenient = true) != null },
            answers = lines.sumOf { answerLinePairs(it, lenient = true)?.size ?: 0 },
            tableLines = made.size,
            numberRows = texts.count { consecutiveNumbers(it) != null },
            letterRows = texts.count { t -> withoutRowLabel(t).second.let { tok -> tok.size >= 4 && tok.all { it.length == 1 && it[0].uppercaseChar() in choices } } },
            bubbleRows = texts.count { bubbleRow.matches(it) },
            rejected = rejected,
            samples = lines.filter { answerLinePairs(it, lenient = true) != null || it in made }.map { excerpt(it.text) }.take(3),
        )
    }

    /** "1 A B C D E": every option printed, the answer marked by a filled shape that isn't text. */
    private val bubbleRow = Regex("^\\d{1,3}[.)]?\\s+A\\s+B\\s+C\\s+D(\\s+E)?$")

    /** Why a line with several number–letter pairs still isn't an answer line, or null. */
    private fun rejectionOf(text: String): String? {
        val strict = strictPair.findAll(compactBoundary.replace(text, "$1 ")).count()
        if (strict < 2 || answerPairs(parseTestHeader(text)?.second ?: text, lenient = true) != null) return null
        if (optionLabelStart.containsMatchIn(text.trimStart())) return "option line"
        val covered = strictPair.findAll(text).sumOf { m -> m.value.count { !it.isWhitespace() } }
        val total = text.count { !it.isWhitespace() }
        if (total > 0 && covered * 100 / total < 55) return "answers are ${covered * 100 / total}% of the line"
        return "numbers don't count up"
    }

    private fun excerpt(text: String) = text.trim().let { if (it.length > 70) it.take(69) + "…" else it }

    // ---- Test assembly -----------------------------------------------------------------------

    private fun issuesOf(answers: List<DetectedAnswer>, kind: KeySourceKind, wentBackwards: Boolean): List<TestIssue> {
        val issues = mutableListOf<TestIssue>()
        answers.filter { it.note == AnswerNote.CONFLICT }.forEach { issues += TestIssue.Conflict(it.question, it.alternatives) }
        if (answers.isNotEmpty()) {
            // Whole-test keys count from question 1; partial ones (margins, labels) from their first.
            val start = if (kind == KeySourceKind.KEY_SECTION || kind == KeySourceKind.IN_TEST) 1 else answers.first().question
            val present = answers.map { it.question }.toSet()
            val missing = (start..answers.last().question).filter { it !in present }
            if (missing.isNotEmpty()) issues += TestIssue.Missing(missing)
        }
        if (answers.size < 3) issues += TestIssue.TooFew
        if (wentBackwards) issues += TestIssue.OutOfOrder
        return issues
    }

    private inner class TestBuilder(val ref: TestRef?) {
        private val entries = mutableListOf<kotlin.Pair<Pair, Int>>()
        private val sourcePages = sortedSetOf<Int>()
        val marginQuestionPages = sortedSetOf<Int>()
        var lastQuestion: Int? = null
            private set
        private var wentBackwards = false

        fun add(read: Pair, page: Int) {
            // An answer that came from a page image (a filled bubble) needs a look.
            val pair = if (imageReadings[page]?.get(read.question) == read.answer && read.confidence == Confidence.HIGH) {
                read.copy(confidence = Confidence.LOW, note = AnswerNote.FROM_IMAGE)
            } else {
                read
            }
            lastQuestion?.let { if (pair.question < it && entries.none { e -> e.first.question == pair.question }) wentBackwards = true }
            entries += pair to page
            sourcePages += page
            lastQuestion = pair.question
        }

        fun build(kind: KeySourceKind, questionPages: List<Int> = emptyList()): DetectedTest {
            val answers = entries.groupBy { it.first.question }.toSortedMap().map { (question, found) ->
                val distinct = found.map { it.first.answer }.distinct()
                val first = found.first()
                val provenance = found.map { (pair, page) -> AnswerProvenance(kind, page, pair.answer) }.distinct()
                if (distinct.size > 1) {
                    DetectedAnswer(question, first.first.answer, Confidence.LOW, first.second, AnswerNote.CONFLICT, distinct, provenance)
                } else {
                    // Repeats with the same answer are just repeats; keep the most confident reading.
                    val best = found.minByOrNull { it.first.confidence.ordinal }!!
                    DetectedAnswer(question, best.first.answer, best.first.confidence, best.second, best.first.note, emptyList(), provenance)
                }
            }
            return DetectedTest(ref, kind, sourcePages.toList(), questionPages, answers, issuesOf(answers, kind, wentBackwards))
        }
    }
}
