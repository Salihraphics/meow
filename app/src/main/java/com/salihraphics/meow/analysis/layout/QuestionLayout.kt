package com.salihraphics.meow.analysis.layout

import com.salihraphics.meow.analysis.PageText
import com.salihraphics.meow.analysis.TextBox

/** An option label printed on the page ("C)"), in page fractions. */
data class OptionMark(val letter: String, val box: TextBox)

data class QuestionRegion(
    val number: Int,
    /** From the question number down to the next question in the same column. */
    val box: TextBox,
    val options: List<OptionMark>,
)

data class PageLayout(val pageIndex: Int, val questions: List<QuestionRegion>) {
    fun questionAt(x: Float, y: Float): QuestionRegion? =
        questions.firstOrNull { x >= it.box.left && x <= it.box.right && y >= it.box.top && y <= it.box.bottom }
}

/**
 * Finds numbered questions and their option labels from positioned page text. Works on one or
 * two columns. It doesn't assume five options or A–E: whatever labels are printed are used.
 */
object QuestionLayoutDetector {
    private val questionLabel = Regex("^(\\d{1,3})[.)]$")
    private val optionLabel = Regex("^([A-Ha-h])[).](.*)$")

    fun detect(page: PageText): PageLayout {
        data class Label(val number: Int, val box: TextBox)

        val labels = page.lines.mapNotNull { line ->
            val first = line.words.firstOrNull() ?: return@mapNotNull null
            val number = questionLabel.find(first.text)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            Label(number, first.box)
        }
        val options = page.lines.flatMap { it.words }.mapNotNull { word ->
            val match = optionLabel.find(word.text) ?: return@mapNotNull null
            // "A)12" printed without a space: shrink the box to the label part.
            val labelWidth = if (match.groupValues[2].isEmpty()) word.box.width else word.box.width * 2f / word.text.length
            OptionMark(match.groupValues[1].uppercase(), word.box.copy(right = word.box.left + labelWidth))
        }
        if (labels.isEmpty()) return PageLayout(page.pageIndex, emptyList())

        // Two columns when question numbers start in both halves of the page.
        val twoColumns = labels.any { it.box.left < 0.4f } && labels.any { it.box.left >= 0.5f }
        val columns = if (twoColumns) listOf(0f to 0.5f, 0.5f to 1f) else listOf(0f to 1f)

        val questions = columns.flatMap { (start, end) ->
            val inColumn = labels.filter { it.box.left >= start && it.box.left < end }.sortedBy { it.box.top }
            inColumn.mapIndexed { i, label ->
                val bottom = inColumn.getOrNull(i + 1)?.box?.top?.minus(0.002f) ?: 0.97f
                val region = TextBox(start, label.box.top - 0.01f, end, bottom)
                QuestionRegion(
                    number = label.number,
                    box = region,
                    options = options
                        .filter { it.box.centerX in region.left..region.right && it.box.centerY in region.top..region.bottom }
                        .sortedWith(compareBy({ it.letter }, { it.box.top })),
                )
            }
        }
        return PageLayout(page.pageIndex, questions)
    }
}
