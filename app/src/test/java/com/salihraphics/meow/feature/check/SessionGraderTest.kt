package com.salihraphics.meow.feature.check

import com.salihraphics.meow.domain.model.AnswerAttempt
import com.salihraphics.meow.domain.model.AnswerConfidence
import com.salihraphics.meow.domain.model.AnswerOrigin
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.KeyAnswer
import com.salihraphics.meow.domain.model.KeyTest
import com.salihraphics.meow.domain.model.KeyTestKind
import com.salihraphics.meow.domain.model.KeyTestStatus
import com.salihraphics.meow.domain.model.SelectionOrigin
import com.salihraphics.meow.domain.model.SolvingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGraderTest {
    private fun test(id: String, pages: IntRange?, vararg answers: Pair<Int, String>, confirmed: Boolean = true) = KeyTest(
        id = id, documentId = "doc", sourceDocumentId = "doc", label = id, number = null, kind = KeyTestKind.KEY_SECTION,
        status = if (confirmed) KeyTestStatus.CONFIRMED else KeyTestStatus.DETECTED, sourcePages = listOf(99), questionPages = pages,
        answers = answers.map { (q, v) ->
            KeyAnswer("$id-$q", id, q, AnswerType.CHOICE, v, null, AnswerOrigin.DETECTED, AnswerConfidence.HIGH, null, emptyList(), 99)
        },
    )

    private var time = 0L
    private fun answer(q: Int, letter: String, page: Int = 0, testId: String? = null) =
        AnswerAttempt("a${time}", "doc", testId, q, page, letter, SelectionOrigin.RECOGNIZED, AttemptResult.PENDING, "s$time", time++)

    private fun session(vararg answers: AnswerAttempt) = SolvingSession("s", "doc", 0, answers.toList())

    @Test
    fun countsCorrectWrongAndBlankForTheTestsAnswered() {
        val tests = listOf(test("t1", 0..1, 1 to "A", 2 to "D", 3 to "E", 4 to "C"), test("t2", 2..3, 1 to "B"))
        val check = SessionGrader.check(session(answer(1, "A"), answer(2, "B"), answer(4, "C")), tests) as SessionCheck.Checked
        assertEquals(2, check.result.correct)
        assertEquals(1, check.result.incorrect)
        assertEquals(1, check.result.unanswered) // question 3 of test 1; test 2 wasn't touched
        assertEquals(listOf(2), check.result.mistakes.map { it.question })
        assertEquals(listOf(3), check.result.blanks.map { it.question })
        assertEquals("D", check.result.mistakes.single().correct)
    }

    @Test
    fun theLatestAnswerToAQuestionCounts() {
        val tests = listOf(test("t1", null, 1 to "A"))
        val check = SessionGrader.check(session(answer(1, "B"), answer(1, "A")), tests) as SessionCheck.Checked
        assertEquals(1, check.result.correct)
        assertEquals(0, check.result.incorrect)
    }

    @Test
    fun sameQuestionNumberInTwoTestsIsGradedPerTest() {
        val tests = listOf(test("t1", 0..0, 1 to "A"), test("t2", 1..1, 1 to "B"))
        val check = SessionGrader.check(session(answer(1, "A", page = 0), answer(1, "B", page = 1)), tests) as SessionCheck.Checked
        assertEquals(2, check.result.correct)
    }

    @Test
    fun neverGradesAgainstAnUnconfirmedKeyOrAnUnknownTest() {
        assertTrue(SessionGrader.check(session(answer(1, "A")), listOf(test("t1", null, 1 to "A", confirmed = false))) is SessionCheck.KeyNotConfirmed)
        val unknown = SessionGrader.check(session(answer(1, "A", page = 7)), listOf(test("t1", 0..1, 1 to "A"), test("t2", 2..3, 1 to "B")))
        assertEquals(listOf(7), (unknown as SessionCheck.TestUnknown).pages)
        assertEquals(SessionCheck.NothingToCheck, SessionGrader.check(null, emptyList()))
        assertTrue(SessionGrader.check(session(answer(1, "A")), emptyList()).let { it is SessionCheck.TestUnknown && it.tests.isEmpty() })
    }

    @Test
    fun answersToQuestionsTheKeyLacksAreListedNotGraded() {
        val check = SessionGrader.check(session(answer(1, "A"), answer(9, "C")), listOf(test("t1", null, 1 to "A"))) as SessionCheck.Checked
        assertEquals(listOf(9), check.notInKey)
        assertEquals(1, check.result.questions.size)
    }
}
