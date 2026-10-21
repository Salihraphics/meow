package com.salihraphics.meow.feature.check

import com.salihraphics.meow.domain.model.AnswerAttempt
import com.salihraphics.meow.domain.model.AnswerGrader
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.KeyTest
import com.salihraphics.meow.domain.model.SolvingSession

/** One question in a checked test. [selected] is null for a question left blank. */
data class GradedQuestion(
    val testId: String,
    val testLabel: String,
    val question: Int,
    val selected: String?,
    val correct: String,
    val result: AttemptResult?,
    /** Where the student marked it; null for blanks. */
    val pageIndex: Int?,
    val attemptId: String?,
)

data class TestResult(val questions: List<GradedQuestion>) {
    val correct get() = questions.count { it.result == AttemptResult.CORRECT }
    val incorrect get() = questions.count { it.result == AttemptResult.INCORRECT }
    val unanswered get() = questions.count { it.selected == null }
    val selfCheck get() = questions.count { it.result == AttemptResult.NEEDS_SELF_CHECK }
    val mistakes get() = questions.filter { it.result == AttemptResult.INCORRECT }
    val blanks get() = questions.filter { it.selected == null }
}

sealed interface SessionCheck {
    data object NothingToCheck : SessionCheck

    /** Answers on pages whose test isn't known: the student says which test those pages are. */
    data class TestUnknown(val pages: List<Int>, val tests: List<KeyTest>) : SessionCheck

    /** The tests answered have a key that isn't confirmed yet. */
    data class KeyNotConfirmed(val tests: List<KeyTest>) : SessionCheck

    data class Checked(
        val result: TestResult,
        /** Answered questions the key has no answer for; not graded. */
        val notInKey: List<Int>,
    ) : SessionCheck
}

/**
 * Grades a solving session against the confirmed key, all at once. Only tests the student
 * answered something in are counted, so blanks are the unanswered questions of those tests.
 * Never grades against an unconfirmed key or a guessed test.
 */
object SessionGrader {
    fun check(session: SolvingSession?, tests: List<KeyTest>): SessionCheck {
        if (session == null || session.latest.isEmpty()) return SessionCheck.NothingToCheck
        val withAnswers = tests.filter { it.answers.isNotEmpty() }

        // Resolve each answer's test first, then keep the latest per test and question.
        val byTest = session.answers.map { attempt -> attempt to testOf(attempt, withAnswers) }
            .groupBy { (attempt, test) -> Triple(test?.id, if (test == null) attempt.pageIndex else null, attempt.question) }
            .map { (_, list) -> list.last() }
        val unknown = byTest.filter { it.second == null }.map { it.first.pageIndex }.distinct().sorted()
        if (unknown.isNotEmpty()) return SessionCheck.TestUnknown(unknown, withAnswers)

        val touched = byTest.mapNotNull { it.second }.distinctBy { it.id }
        val unconfirmed = touched.filter { !it.isConfirmed }
        if (unconfirmed.isNotEmpty()) return SessionCheck.KeyNotConfirmed(unconfirmed)

        val notInKey = mutableListOf<Int>()
        val questions = touched.flatMap { test ->
            val mine = byTest.filter { it.second?.id == test.id }.map { it.first }.associateBy { it.question }
            notInKey += mine.keys.filter { q -> test.answers.none { it.question == q } }
            test.answers.map { key ->
                val attempt = mine[key.question]
                GradedQuestion(
                    testId = test.id,
                    testLabel = test.label,
                    question = key.question,
                    selected = attempt?.selected,
                    correct = key.value,
                    result = attempt?.let { AnswerGrader.grade(key, it.selected) },
                    pageIndex = attempt?.pageIndex,
                    attemptId = attempt?.id,
                )
            }
        }
        return SessionCheck.Checked(TestResult(questions), notInKey.sorted())
    }

    private fun testOf(attempt: AnswerAttempt, tests: List<KeyTest>): KeyTest? =
        attempt.testId?.let { id -> tests.firstOrNull { it.id == id } } ?: AnswerChecker.testFor(attempt.pageIndex, attempt.question, tests)
}
