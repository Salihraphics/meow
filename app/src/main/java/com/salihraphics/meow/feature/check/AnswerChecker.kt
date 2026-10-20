package com.salihraphics.meow.feature.check

import com.salihraphics.meow.domain.model.AnswerGrader
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.KeyTest

sealed interface CheckOutcome {
    data class Graded(
        val question: Int,
        val selected: String,
        val correct: String,
        val result: AttemptResult,
        val testId: String,
    ) : CheckOutcome

    /** The document has no answer key at all: stay quiet, it's just a drawing. */
    data object NoKey : CheckOutcome
    data object KeyNotConfirmed : CheckOutcome

    /** Several tests and this page isn't assigned to one. */
    data object PageTestUnknown : CheckOutcome
    data class NoAnswerForQuestion(val question: Int) : CheckOutcome
}

/** Finds the test a page belongs to and grades a selected option against the confirmed key. */
object AnswerChecker {
    fun check(pageIndex: Int, question: Int, selected: String, tests: List<KeyTest>): CheckOutcome {
        val withAnswers = tests.filter { it.answers.isNotEmpty() }
        if (withAnswers.isEmpty()) return CheckOutcome.NoKey
        val test = testFor(pageIndex, question, withAnswers) ?: return CheckOutcome.PageTestUnknown
        if (!test.isConfirmed) return CheckOutcome.KeyNotConfirmed
        val key = test.answers.firstOrNull { it.question == question } ?: return CheckOutcome.NoAnswerForQuestion(question)
        return CheckOutcome.Graded(question, selected, key.value, AnswerGrader.grade(key, selected), test.id)
    }

    private fun testFor(pageIndex: Int, question: Int, tests: List<KeyTest>): KeyTest? {
        val onPage = tests.filter { it.questionPages?.contains(pageIndex) == true }
        return when {
            onPage.size == 1 -> onPage.single()
            // Overlapping ranges: the one that actually has this question.
            onPage.size > 1 -> onPage.filter { t -> t.answers.any { it.question == question } }.singleOrNull()
            // No page ranges at all and a single test: it's the test.
            tests.size == 1 && tests.single().questionPages == null -> tests.single()
            else -> null
        }
    }
}
