package com.salihraphics.meow.analysis.layout

import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextBox
import com.salihraphics.meow.analysis.TextWord
import com.salihraphics.meow.analysis.key.TurkishText
import kotlin.math.abs

/** An option label printed on the page ("C)"), in page fractions. */
data class OptionMark(val letter: String, val box: TextBox)

/**
 * One question's printed options (A, B, C…), and its number when that is known for sure.
 * [number] is null when the page doesn't say which question these options belong to: then the
 * student is asked, never guessed for.
 */
data class QuestionRegion(
    val number: Int?,
    /** The options' area, plus the question number when found. */
    val box: TextBox,
    val options: List<OptionMark>,
)

data class PageLayout(val pageIndex: Int, val questions: List<QuestionRegion>) {
    /** Question numbers found on this page, in reading order. */
    val numbers: List<Int> get() = questions.mapNotNull { it.number }
}

/**
 * Finds the questions on a page from positioned text. The unit is an option group, a run of
 * labels A, B, C… printed close together (in a row, a column or a grid); each group is then
 * given the question number printed nearest above it in its own column. A question's options
 * therefore never depend on how far its region reaches, and a stray "1." in a header can't
 * claim the whole page.
 */
object QuestionLayoutDetector {
    private val optionLabel = Regex("^\\(?([A-E])[).](.*)$")
    private val circledLabel = Regex("^([\\u24B6-\\u24BA])(.*)$") // Ⓐ–Ⓔ
    private val numberLabel = Regex("^(\\d{1,3})([.)]?)$")

    /** Lines that start with a number but are headings, not questions: "1. GÜN", "2. Ünite". */
    private val headingWords = Regex(
        "^(gun|gunu|unite|bolum|test|testi|konu|kazanim|sayfa|deneme|ders|hafta|modul|kitapcik|kisim|asama|seviye|tyt|ayt|lgs|yks)\\b",
    )

    private const val SAME_ROW = 0.012f
    private const val ROW_GAP = 0.07f
    private const val ALIGN = 0.03f

    /** How far right of its number a question's options may start. */
    private const val MAX_LABEL_INDENT = 0.2f

    private data class Candidate(val letter: Char, val box: TextBox, val explicit: Boolean)
    private data class Label(val number: Int, val box: TextBox, val explicit: Boolean)

    fun detect(page: PageText): PageLayout {
        val groups = optionGroups(page)
        if (groups.isEmpty()) return PageLayout(page.pageIndex, emptyList())
        val labels = questionLabels(page)

        // Each group takes the nearest label above it in its own column; a label claimed by two
        // groups (a missing number between them) goes to the first, the second stays unknown.
        val columns = columnStarts(labels)
        val claims = groups.map { group -> labelFor(group, labels, columns, groups) }
        // A number claimed by two option groups can't be told apart: both are asked.
        val claimCount = claims.filterNotNull().groupingBy { it }.eachCount()
        val numbered = groups.zip(claims).map { (group, label) -> group to label?.takeIf { claimCount[it] == 1 } }
        // The same number twice on one page means something was misread: trust neither.
        val counts = numbered.mapNotNull { it.second?.number }.groupingBy { it }.eachCount()
        val questions = numbered.map { (group, label) ->
            val number = label?.number?.takeIf { counts[it] == 1 }
            val box = TextBox.of(group.map { it.box } + listOfNotNull(label?.box))
            QuestionRegion(number, box, group.map { OptionMark(it.letter.toString(), it.box) })
        }
        return PageLayout(page.pageIndex, questions.sortedWith(compareBy({ it.box.left >= 0.5f }, { it.box.top })))
    }

    // ---- Options -----------------------------------------------------------------------------

    private fun optionCandidates(page: PageText): List<Candidate> = page.lines.flatMap { it.words }.mapNotNull { word ->
        optionLabel.find(word.text)?.let { m ->
            Candidate(m.groupValues[1][0], labelPart(word, m.groupValues[2].length), explicit = true)
        } ?: circledLabel.find(word.text)?.let { m ->
            Candidate('A' + (m.groupValues[1][0] - 'Ⓐ'), labelPart(word, m.groupValues[2].length), explicit = true)
        } ?: word.text.singleOrNull()?.takeIf { it in 'A'..'E' }?.let { Candidate(it, word.box, explicit = false) }
    }

    /** "A)12" printed without a space: the box of just the label part. */
    private fun labelPart(word: TextWord, restLength: Int): TextBox {
        if (restLength == 0) return word.box
        val labelChars = word.text.length - restLength
        return word.box.copy(right = word.box.left + word.box.width * labelChars / word.text.length)
    }

    /**
     * Chains A → B → C… where each next label is either to the right on the same row or starts
     * the next row under the group's first label. Bare letters ("A" without ")") only count as a
     * full A–D run, so a stray capital letter in a sentence never becomes an option.
     */
    private fun optionGroups(page: PageText): List<List<Candidate>> {
        val candidates = optionCandidates(page)
        val used = HashSet<Candidate>()
        val groups = mutableListOf<List<Candidate>>()
        for (start in candidates.filter { it.letter == 'A' }.sortedWith(compareBy({ !it.explicit }, { it.box.top }, { it.box.left }))) {
            if (start in used) continue
            val chain = mutableListOf(start)
            while (true) {
                val previous = chain.last()
                val wanted = previous.letter + 1
                val next = candidates
                    .filter { it.letter == wanted && it !in used && it !in chain && it.explicit == start.explicit }
                    .mapNotNull { c -> step(previous, chain.first(), c)?.let { c to it } }
                    .minByOrNull { it.second }?.first ?: break
                chain += next
            }
            val enough = if (start.explicit) chain.size >= 3 else chain.size >= 4
            if (enough) {
                groups += chain
                used += chain
            }
        }
        return groups
    }

    /** How good [c] is as the label after [previous]; null if it can't be. */
    private fun step(previous: Candidate, first: Candidate, c: Candidate): Float? {
        val sameRow = abs(c.box.centerY - previous.box.centerY) < maxOf(previous.box.height * 0.8f, SAME_ROW)
        if (sameRow) {
            val gap = c.box.left - previous.box.right
            return if (gap > -0.002f && gap < 0.45f) gap else null
        }
        val below = c.box.top > previous.box.top + 0.004f && c.box.top - previous.box.bottom < ROW_GAP
        return if (below && abs(c.box.left - first.box.left) < ALIGN) 1f + (c.box.top - previous.box.bottom) else null
    }

    private fun groupTop(group: List<Candidate>) = group.minOf { it.box.top }

    // ---- Question numbers --------------------------------------------------------------------

    /**
     * Numbers at the start of a line: "4.", "4)", or a bare "4" (often printed in a box). Bare
     * numbers are accepted only when at least two of them line up as a column of question
     * numbers, so a line of question text starting with "12 kg" isn't taken for question 12.
     */
    private fun questionLabels(page: PageText): List<Label> {
        val found = page.lines.mapNotNull { line ->
            val first = line.words.firstOrNull() ?: return@mapNotNull null
            val match = numberLabel.find(first.text) ?: return@mapNotNull null
            val number = match.groupValues[1].toInt()
            if (number == 0) return@mapNotNull null
            val rest = TurkishText.fold(line.words.drop(1).joinToString(" ") { it.text }).trim()
            if (headingWords.containsMatchIn(rest)) return@mapNotNull null
            Label(number, first.box, explicit = match.groupValues[2].isNotEmpty())
        }
        val bare = found.filter { !it.explicit }
        val alignedBare = bare.filter { b -> bare.count { abs(it.box.left - b.box.left) < 0.012f } >= 2 }
        return found.filter { it.explicit } + alignedBare
    }

    /** Left edges of the question-number columns (one or two, sometimes three). */
    private fun columnStarts(labels: List<Label>): List<Float> {
        val lefts = labels.map { it.box.left }.sorted()
        val starts = mutableListOf<Float>()
        for (left in lefts) if (starts.isEmpty() || left - starts.last() > 0.2f) starts += left
        return starts
    }

    private fun labelFor(group: List<Candidate>, labels: List<Label>, columns: List<Float>, groups: List<List<Candidate>>): Label? {
        val left = group.minOf { it.box.left }
        val top = groupTop(group)
        // The group's column: the rightmost number column starting at or before the options.
        val column = columns.lastOrNull { it <= left + 0.02f } ?: return null
        val nextColumn = columns.firstOrNull { it > column } ?: Float.MAX_VALUE
        return labels
            .filter { it.box.left >= column - 0.02f && it.box.left < nextColumn - 0.02f }
            // A question number sits just left of (or above) its options, never across the page.
            .filter { left - it.box.left in -0.02f..MAX_LABEL_INDENT }
            .filter { it.box.top <= top + 0.005f }
            // No other option group between the number and these options: that group is its question.
            .filter { label ->
                groups.none { other ->
                    other !== group && groupTop(other) > label.box.top && groupTop(other) < top - 0.001f &&
                        other.minOf { it.box.left } >= column - 0.02f && other.minOf { it.box.left } < nextColumn - 0.02f
                }
            }
            .maxByOrNull { it.box.top }
    }
}
