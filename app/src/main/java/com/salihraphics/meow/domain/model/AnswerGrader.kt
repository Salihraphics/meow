package com.salihraphics.meow.domain.model

import com.salihraphics.meow.analysis.key.TurkishText
import kotlin.math.abs

/**
 * Compares a student's answer with a key answer. It only says CORRECT or INCORRECT when that is
 * certain; a written answer that doesn't match exactly is left for the student to judge.
 */
object AnswerGrader {
    fun grade(key: KeyAnswer, given: String): AttemptResult = when (key.type) {
        AnswerType.CHOICE ->
            if (given.trim().equals(key.value.trim(), ignoreCase = true)) AttemptResult.CORRECT else AttemptResult.INCORRECT
        AnswerType.NUMBER -> {
            val expected = parseNumber(key.value)
            val actual = parseNumber(given)
            when {
                expected == null || actual == null -> AttemptResult.NEEDS_SELF_CHECK
                abs(expected - actual) <= (key.tolerance ?: 0.0) + 1e-9 -> AttemptResult.CORRECT
                else -> AttemptResult.INCORRECT
            }
        }
        AnswerType.TEXT ->
            if (normalize(given) == normalize(key.value)) AttemptResult.CORRECT else AttemptResult.NEEDS_SELF_CHECK
    }

    /** Accepts "12,5" (Turkish decimal comma), "12.5", "-3", "1/4". */
    fun parseNumber(text: String): Double? {
        val t = text.trim().replace(" ", "")
        if (t.contains('/')) {
            val (a, b) = t.split('/', limit = 2)
            val denominator = b.replace(',', '.').toDoubleOrNull() ?: return null
            if (denominator == 0.0) return null
            return a.replace(',', '.').toDoubleOrNull()?.div(denominator)
        }
        return t.replace(',', '.').toDoubleOrNull()
    }

    private fun normalize(text: String) = TurkishText.fold(text).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
