package com.salihraphics.meow.analysis.layout

import com.salihraphics.meow.analysis.TextBox
import kotlin.math.hypot

/** What a pen stroke means as an answer, if anything. */
sealed interface ChoiceRecognition {
    /** One option clearly circled. */
    data class Selected(val question: Int, val letter: String, val options: List<String>) : ChoiceRecognition

    /** Looks like an answer, but which option is unclear: ask the student. */
    data class Unclear(val question: Int, val candidates: List<String>, val options: List<String>) : ChoiceRecognition

    /** Not an answer mark (notes, working, underlining): leave it alone. */
    data object NotAnAnswer : ChoiceRecognition
}

/**
 * Decides which option a circle marks. Works only in page fractions (the stored stroke points
 * and the page's text positions), so zoom, rotation and screen size cannot change the result.
 * It never guesses: anything but one clearly circled label comes back as [ChoiceRecognition.Unclear].
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

        val question = layout.questionAt(box.centerX, box.centerY) ?: return ChoiceRecognition.NotAnAnswer
        if (question.options.isEmpty()) return ChoiceRecognition.NotAnAnswer
        val letters = question.options.map { it.letter }.distinct()

        val closed = hypot(xs.first() - xs.last(), ys.first() - ys.last()) <= 0.4f * maxOf(box.width, box.height)
        val inside = question.options.filter { it.box.centerX in box.left..box.right && it.box.centerY in box.top..box.bottom }
            .map { it.letter }.distinct()
        val touching = question.options.filter { overlaps(it.box, box) }.map { it.letter }.distinct()
        // A circle drawn around an option's value ("18") rather than its label: the label sits just left of it.
        val labelLeftOf = question.options.filter { o ->
            o.box.bottom > box.top && o.box.top < box.bottom && o.box.right <= box.left + 0.01f && box.left - o.box.right < 0.06f
        }.map { it.letter }.distinct()

        return when {
            closed && inside.size == 1 -> ChoiceRecognition.Selected(question.number, inside.single(), letters)
            closed && inside.size > 1 -> ChoiceRecognition.Unclear(question.number, inside, letters)
            closed && labelLeftOf.isNotEmpty() -> ChoiceRecognition.Unclear(question.number, labelLeftOf, letters)
            touching.isNotEmpty() -> ChoiceRecognition.Unclear(question.number, touching, letters)
            else -> ChoiceRecognition.NotAnAnswer
        }
    }

    private fun overlaps(a: TextBox, b: TextBox) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
