package com.salihraphics.meow.analysis.key

import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextBox
import com.salihraphics.meow.analysis.TextLine
import com.salihraphics.meow.analysis.TextWord
import kotlin.math.abs

/** How dark a region of a page image is: 0 white … 1 black. Positions are page fractions. */
fun interface Darkness {
    fun mean(area: TextBox): Float
}

/**
 * Optical answer sheets: rows of "12 A B C D E" whose answer is a filled bubble, which text
 * can't carry. The rows and letter positions come from the page text (or OCR); whether a bubble
 * is filled comes from the page image. A row counts as answered only when one bubble is clearly
 * darker than all the others; a blank form gives nothing, two marks give nothing.
 */
object BubbleSheetReader {
    data class Row(val question: Int, val numberBox: TextBox, val cells: Map<String, TextBox>) {
        val box: TextBox get() = TextBox.of(cells.values.toList() + numberBox)
    }

    sealed interface Reading {
        data class Marked(val letter: String) : Reading
        data object Blank : Reading
        data object Ambiguous : Reading
    }

    /** Rows need this much more ink in the marked bubble than in the next darkest one. */
    private const val MARK_MARGIN = 0.18f
    private const val MARK_MIN = 0.3f
    const val MIN_ROWS = 3

    /** "n A B C D (E)" rows printed on the page, several per printed line for multi-column forms. */
    fun rows(page: PageText): List<Row> {
        val words = page.lines.flatMap { it.words }.sortedBy { it.box.centerY }
        val lines = mutableListOf<MutableList<TextWord>>()
        for (word in words) {
            val line = lines.lastOrNull()
            val center = line?.let { l -> l.sumOf { it.box.centerY.toDouble() }.toFloat() / l.size }
            if (line != null && center != null && abs(word.box.centerY - center) <= (word.box.height * 0.5f).coerceAtLeast(0.004f)) line += word else lines += mutableListOf(word)
        }
        val rows = mutableListOf<Row>()
        for (line in lines) {
            val sorted = line.sortedBy { it.box.left }
            var i = 0
            while (i < sorted.size) {
                val number = sorted[i].text.trimEnd('.', ')').toIntOrNull()?.takeIf { it in 1..300 }
                if (number == null) {
                    i++
                    continue
                }
                val cells = LinkedHashMap<String, TextBox>()
                var j = i + 1
                while (j < sorted.size && sorted[j].text.length == 1 && sorted[j].text[0] == 'A' + cells.size && cells.size < 5) {
                    cells[sorted[j].text] = sorted[j].box
                    j++
                }
                if (cells.size >= 4) rows += Row(number, sorted[i].box, cells)
                i = if (cells.size >= 4) j else i + 1
            }
        }
        return if (rows.size >= MIN_ROWS) rows else emptyList()
    }

    fun read(rows: List<Row>, darkness: Darkness): Map<Int, Reading> = rows.associate { row ->
        val ink = row.cells.mapValues { (_, box) -> darkness.mean(bubbleAround(box)) }.entries.sortedByDescending { it.value }
        val first = ink[0].value
        val second = ink.getOrNull(1)?.value ?: 0f
        val third = ink.getOrNull(2)?.value ?: 0f
        row.question to when {
            first >= MARK_MIN && first - second >= MARK_MARGIN -> Reading.Marked(ink[0].key)
            second >= MARK_MIN && second - third >= MARK_MARGIN -> Reading.Ambiguous // two marks
            else -> Reading.Blank
        }
    }

    /** The bubble printed around a letter: a square about twice the letter's size. */
    fun bubbleAround(letter: TextBox): TextBox {
        val half = maxOf(letter.width, letter.height) * 0.9f
        return TextBox(letter.centerX - half, letter.centerY - half, letter.centerX + half, letter.centerY + half)
    }

    /**
     * [page] with each marked row's printed "n A B C D E" replaced by an answer line ("n.C") in
     * the same place, for the line parser. Unmarked rows stay as printed (they read as nothing).
     */
    fun withAnswers(page: PageText, rows: List<Row>, readings: Map<Int, Reading>): PageText {
        val marked = rows.mapNotNull { row -> (readings[row.question] as? Reading.Marked)?.let { row to it.letter } }
        val printed = marked.flatMap { (row, _) -> row.cells.values + row.numberBox }.toSet()
        val kept = page.lines.mapNotNull { line -> line.words.filter { it.box !in printed }.takeIf { it.isNotEmpty() }?.let { TextLine(it) } }
        val answers = marked.map { (row, letter) -> TextLine(listOf(TextWord("${row.question}.$letter", row.box))) }
        return PageText(page.pageIndex, (kept + answers).sortedBy { it.box.top }, page.source)
    }
}
