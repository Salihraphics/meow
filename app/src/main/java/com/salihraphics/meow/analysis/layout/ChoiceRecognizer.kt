package com.salihraphics.meow.analysis.layout

import com.salihraphics.meow.analysis.TextBox
import kotlin.math.hypot

/** What a pen stroke means as an answer, if anything. */
sealed interface ChoiceRecognition {
    /** One option clearly circled, in a question whose number is printed on the page. */
    data class Selected(val question: Int, val letter: String, val options: List<String>) : ChoiceRecognition

    /**
     * Looks like an answer, but the option ([candidates]) or the question ([question] null) is
     * unclear: ask the student. [nearbyQuestions] are numbers printed on this page, to offer first.
     */
    data class Unclear(
        val question: Int?,
        val candidates: List<String>,
        val options: List<String>,
        val nearbyQuestions: List<Int> = emptyList(),
    ) : ChoiceRecognition

    /** Not an answer mark (notes, working, underlining): leave it alone. */
    data object NotAnAnswer : ChoiceRecognition
}

/**
 * Decides which option a mark selects. Works only in page fractions (the stored stroke points
 * and the page's text positions), so zoom, rotation and screen size cannot change the result.
 *
 * The question is always the one the marked option label belongs to, never "whatever region the
 * mark falls in", and it never guesses: anything short of one clearly circled label in a
 * numbered question comes back as [ChoiceRecognition.Unclear].
 */
object ChoiceRecognizer {
    /** Strokes larger than this (fraction of page) are drawings or notes, not a circled option. */
    private const val MAX_MARK_SIZE = 0.35f

    fun recognize(points: FloatArray, layout: PageLayout): ChoiceRecognition {
        if (points.size < 6 || layout.questions.isEmpty()) return ChoiceRecognition.NotAnAnswer
        val xs = points.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = points.filterIndexed { i, _ -> i % 2 == 1 }
        val box = TextBox(xs.min(), ys.min(), xs.max(), ys.max())
        if (box.width > MAX_MARK_SIZE || box.height > MAX_MARK_SIZE) return ChoiceRecognition.NotAnAnswer

        val closed = hypot(xs.first() - xs.last(), ys.first() - ys.last()) <= 0.4f * maxOf(box.width, box.height)
        val marks = layout.questions.flatMap { q -> q.options.map { q to it } }
        val inside = marks.filter { (_, o) -> o.box.centerX in box.left..box.right && o.box.centerY in box.top..box.bottom }
        val touching = marks.filter { (_, o) -> overlaps(o.box, box) }
        // A circle drawn around an option's value ("18") rather than its label: the label sits just left of it.
        val labelLeftOf = marks.filter { (_, o) ->
            o.box.bottom > box.top && o.box.top < box.bottom && o.box.right <= box.left + 0.01f && box.left - o.box.right < 0.06f
        }.groupBy { it.first }.map { (_, list) -> list.maxBy { it.second.box.right } } // the nearest label per question

        val hits = when {
            closed && inside.isNotEmpty() -> inside
            closed && labelLeftOf.isNotEmpty() -> labelLeftOf
            touching.isNotEmpty() -> touching
            else -> return ChoiceRecognition.NotAnAnswer
        }
        val certain = closed && inside.isNotEmpty()
        val nearby = layout.numbers

        val questions = hits.map { it.first }.distinct()
        if (questions.size > 1) {
            // Labels of different questions: which question is itself the question.
            return ChoiceRecognition.Unclear(null, hits.map { it.second.letter }.distinct().sorted(), letters(questions), nearby)
        }
        val question = questions.single()
        val letters = hits.map { it.second.letter }.distinct()
        val options = letters(listOf(question))
        return when {
            question.number == null -> ChoiceRecognition.Unclear(null, letters, options, nearby)
            certain && letters.size == 1 -> ChoiceRecognition.Selected(question.number, letters.single(), options)
            else -> ChoiceRecognition.Unclear(question.number, letters, options, nearby)
        }
    }

    private fun letters(questions: List<QuestionRegion>) = questions.flatMap { q -> q.options.map { it.letter } }.distinct().sorted()

    private fun overlaps(a: TextBox, b: TextBox) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
