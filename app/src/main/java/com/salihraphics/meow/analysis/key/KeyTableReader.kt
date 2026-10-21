package com.salihraphics.meow.analysis.key

import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextBox
import com.salihraphics.meow.analysis.TextLine
import com.salihraphics.meow.analysis.TextWord
import kotlin.math.abs

/**
 * Turns answer tables into answer lines ("1.C 2.A …") the line parser understands, using word
 * positions rather than line text. It handles what text extraction tends to break apart:
 * - table cells spaced wider than a word gap, which arrive as separate "lines" ([visualRows]
 *   joins everything printed on one row again);
 * - a row of question numbers over a row of letters, matched by horizontal position, so a
 *   missing or extra cell doesn't shift every answer after it;
 * - several tables side by side (numbering restarts along the row), named by headers above;
 * - tests as columns: "TEST 1  TEST 2  TEST 3" over rows of "1. C A E".
 */
internal class KeyTableReader(
    private val choices: CharRange,
    private val parseTestHeader: (String) -> kotlin.Pair<TestRef, String>?,
) {
    /** All words printed on the same row, across columns, left to right. */
    fun visualRows(page: PageText): List<TextLine> {
        val words = page.lines.flatMap { it.words }.sortedBy { it.box.centerY }
        val rows = mutableListOf<MutableList<TextWord>>()
        for (word in words) {
            val row = rows.lastOrNull()
            val center = row?.let { r -> r.sumOf { it.box.centerY.toDouble() }.toFloat() / r.size }
            val tolerance = (word.box.height * 0.5f).coerceAtLeast(0.004f)
            if (row != null && center != null && abs(word.box.centerY - center) <= tolerance) row += word else rows += mutableListOf(word)
        }
        return rows.map { TextLine(it.sortedBy { w -> w.box.left }) }
    }

    /**
     * [lines] in reading order, with recognised tables replaced by answer lines. The returned set
     * holds the lines that came from tables.
     */
    fun rewrite(lines: List<TextLine>): kotlin.Pair<List<TextLine>, Set<TextLine>> {
        val out = mutableListOf<TextLine>()
        val made = mutableSetOf<TextLine>()
        var i = 0
        while (i < lines.size) {
            val columns = matrix(lines, i)
            if (columns != null) {
                out += columns.first
                made += columns.first
                i += columns.second
                continue
            }
            val numbers = numberCells(lines[i])
            if (numbers != null) {
                val headers = if (i > 0) testHeaders(lines[i - 1]) else emptyList()
                var j = i + 1
                val produced = mutableListOf<TextLine>()
                while (j < lines.size) {
                    val rows = letterRow(lines[j], numbers, headers) ?: break
                    produced += rows
                    j++
                }
                if (produced.isNotEmpty()) {
                    // The header line above, if it only named these tables, is now part of them.
                    if (headers.isNotEmpty() && out.lastOrNull() == lines[i - 1] && headers.size > 1) out.removeAt(out.lastIndex)
                    out += produced
                    made += produced
                    i = j
                    continue
                }
            }
            out += lines[i]
            i++
        }
        return out to made
    }

    // ---- Numbers over letters ----------------------------------------------------------------

    private data class Cell(val text: String, val box: TextBox)

    /** A row of at least four question numbers (after an optional "Soru" label). */
    private fun numberCells(line: TextLine): List<kotlin.Pair<Int, TextBox>>? {
        val words = line.words.dropWhile { TurkishText.fold(it.text).trimEnd(':', '.') in ROW_LABELS }
        if (words.size < 4) return null
        val numbers = words.map { w -> w.text.trimEnd('.', ')').toIntOrNull()?.takeIf { it in 1..300 } ?: return null }
        return numbers.zip(words.map { it.box })
    }

    /**
     * A row of letters under [numbers]: each letter goes to the number above it. Numbering that
     * restarts along the row (tables side by side) splits it into one answer line per table,
     * named by the test header above that table if there is one.
     */
    private fun letterRow(line: TextLine, numbers: List<kotlin.Pair<Int, TextBox>>, headers: List<kotlin.Pair<TestRef, Float>>): List<TextLine>? {
        val label = parseTestHeader(TurkishText.normalizeSeparators(line.text))?.takeIf { it.second.isNotEmpty() }
        // The test name's words come first; the rest are the cells.
        val cellCount = label?.second?.split(' ')?.count { it.isNotBlank() } ?: line.words.size
        val cells = line.words.takeLast(cellCount).dropWhile { TurkishText.fold(it.text).trimEnd(':', '.') in ROW_LABELS }
        val letters = cells.map { w -> letterOf(w.text)?.let { Cell(it, w.box) } ?: return null }
        if (letters.size < 3) return null
        // "A A A A A" under the numbers: a row of a blank optical form, not answers.
        if (letters.size >= 4 && letters.map { it.text }.distinct().size == 1) return null
        val matched = byPosition(letters, numbers)
            // Positions that don't line up (rough OCR boxes) but one letter per number: in order.
            ?: if (letters.size == numbers.size) letters.indices.associateWith { letters[it].text } else return null

        // Split where numbering restarts: separate tables printed side by side.
        val runs = mutableListOf(mutableListOf(0))
        for (k in 1 until numbers.size) if (numbers[k].first == numbers[k - 1].first + 1) runs.last() += k else runs += mutableListOf(k)
        return runs.mapNotNull { run ->
            val pairs = run.mapNotNull { k -> matched[k]?.let { numbers[k].first to it } }
            if (pairs.isEmpty()) return@mapNotNull null
            val left = numbers[run.first()].second.left
            val right = numbers[run.last()].second.right
            val name = label?.first?.label ?: headers.firstOrNull { (_, x) -> x in (left - 0.06f)..right }?.first?.label
                ?: headers.singleOrNull()?.first?.label?.takeIf { runs.size == 1 }
            answerLine(name, pairs, TextBox(left, line.box.top, right, line.box.bottom))
        }
    }

    /** Each letter to the number printed above it; null if they don't line up. */
    private fun byPosition(letters: List<Cell>, numbers: List<kotlin.Pair<Int, TextBox>>): Map<Int, String>? {
        val spacing = numbers.zipWithNext { a, b -> b.second.centerX - a.second.centerX }.filter { it > 0 }.sorted()
            .let { if (it.isEmpty()) 0.05f else it[it.size / 2] }
        val tolerance = (spacing * 0.45f).coerceIn(0.01f, 0.06f)
        val matched = HashMap<Int, String>() // index into numbers → letter
        for (cell in letters) {
            val nearest = numbers.indices.minBy { abs(numbers[it].second.centerX - cell.box.centerX) }
            if (abs(numbers[nearest].second.centerX - cell.box.centerX) > tolerance || nearest in matched) return null
            matched[nearest] = cell.text
        }
        return matched.takeIf { it.size * 4 >= numbers.size * 3 }
    }

    // ---- Tests as columns --------------------------------------------------------------------

    /** "TEST 1  TEST 2 …" with their horizontal centres, when a line names two or more tests. */
    private fun testHeaders(line: TextLine): List<kotlin.Pair<TestRef, Float>> {
        val found = mutableListOf<kotlin.Pair<TestRef, Float>>()
        val words = line.words
        var k = 0
        while (k < words.size) {
            // "TEST 1" (two words) or "TEST-1" / "1.TEST" (one).
            val two = if (k + 1 < words.size) "${words[k].text} ${words[k + 1].text}" else null
            val asTwo = two?.let { parseTestHeader(it) }?.takeIf { it.second.isEmpty() && it.first.number != null }
            if (asTwo != null) {
                found += asTwo.first to (words[k].box.left + words[k + 1].box.right) / 2
                k += 2
                continue
            }
            val asOne = parseTestHeader(words[k].text)?.takeIf { it.second.isEmpty() && it.first.number != null }
            if (asOne != null) found += asOne.first to words[k].box.centerX
            k++
        }
        return if (found.size >= 2) found else emptyList()
    }

    /** A header naming several tests, then rows "1. C A E": one answer line per test column. */
    private fun matrix(lines: List<TextLine>, start: Int): kotlin.Pair<List<TextLine>, Int>? {
        val headers = testHeaders(lines[start])
        if (headers.isEmpty()) return null
        val columns = headers.map { mutableListOf<kotlin.Pair<Int, String>>() }
        var j = start + 1
        while (j < lines.size) {
            val words = lines[j].words
            val number = words.firstOrNull()?.text?.trimEnd('.', ')', ':', '-')?.toIntOrNull() ?: break
            val cells = words.drop(1).map { w -> letterOf(w.text)?.let { Cell(it, w.box) } }
            if (cells.isEmpty() || cells.any { it == null }) break
            for (cell in cells.filterNotNull()) {
                val nearest = headers.indices.minBy { abs(headers[it].second - cell.box.centerX) }
                if (abs(headers[nearest].second - cell.box.centerX) < 0.08f) columns[nearest] += number to cell.text
            }
            j++
        }
        if (j - start - 1 < 2 || columns.all { it.isEmpty() }) return null
        val top = lines[start].box.top
        val produced = headers.mapIndexedNotNull { c, (ref, x) ->
            if (columns[c].isEmpty()) null else answerLine(ref.label, columns[c], TextBox(x - 0.01f, top + c * 0.0001f, x + 0.01f, top + 0.01f))
        }
        return produced to (j - start)
    }

    private fun letterOf(text: String): String? {
        val t = TurkishText.normalizeSeparators(text).trim('.', ')', '(', ' ')
        return t.singleOrNull()?.uppercaseChar()?.takeIf { it in choices }?.toString()
    }

    /** "TEST 1 1.C 2.A …" placed where the table was, one word per answer. */
    private fun answerLine(name: String?, pairs: List<kotlin.Pair<Int, String>>, box: TextBox): TextLine {
        val words = mutableListOf<TextWord>()
        name?.split(' ')?.filter { it.isNotBlank() }?.forEach { words += TextWord(it, box) }
        pairs.sortedBy { it.first }.forEach { (n, l) -> words += TextWord("$n.$l", box) }
        return TextLine(words)
    }

    companion object {
        val ROW_LABELS = setOf("soru", "no", "sorular", "cevap", "cevaplar", "yanit", "yanitlar", "answer", "answers", "key")
    }
}
