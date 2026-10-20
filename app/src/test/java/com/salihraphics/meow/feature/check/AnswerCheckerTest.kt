package com.salihraphics.meow.feature.check

import com.salihraphics.meow.domain.model.AnswerConfidence
import com.salihraphics.meow.domain.model.AnswerOrigin
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.KeyAnswer
import com.salihraphics.meow.domain.model.KeyTest
import com.salihraphics.meow.domain.model.KeyTestKind
import com.salihraphics.meow.domain.model.KeyTestStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerCheckerTest {
    private fun test(id: String, pages: IntRange?, vararg answers: Pair<Int, String>, confirmed: Boolean = true) = KeyTest(
        id = id, documentId = "doc", sourceDocumentId = "doc", label = id, number = null, kind = KeyTestKind.KEY_SECTION,
        status = if (confirmed) KeyTestStatus.CONFIRMED else KeyTestStatus.DETECTED, sourcePages = listOf(99), questionPages = pages,
        answers = answers.map { (q, v) ->
            KeyAnswer("$id-$q", id, q, AnswerType.CHOICE, v, null, AnswerOrigin.DETECTED, AnswerConfidence.HIGH, null, emptyList(), 99)
        },
    )

    @Test
    fun gradesAgainstTheTestThatOwnsThePage() {
        val tests = listOf(test("t1", 0..4, 1 to "C", 2 to "A"), test("t2", 5..9, 1 to "D", 2 to "B"))
        assertEquals(CheckOutcome.Graded(1, "C", "C", AttemptResult.CORRECT, "t1"), AnswerChecker.check(2, 1, "C", tests))
        assertEquals(CheckOutcome.Graded(1, "C", "D", AttemptResult.INCORRECT, "t2"), AnswerChecker.check(6, 1, "C", tests))
        assertEquals(AttemptResult.CORRECT, (AnswerChecker.check(6, 1, "d", tests) as CheckOutcome.Graded).result)
    }

    @Test
    fun neverGradesAgainstAnUnconfirmedKey() {
        assertEquals(CheckOutcome.KeyNotConfirmed, AnswerChecker.check(0, 1, "C", listOf(test("t1", 0..4, 1 to "C", confirmed = false))))
    }

    @Test
    fun aPageOutsideEveryTestIsNotGuessed() {
        val tests = listOf(test("t1", 0..4, 1 to "C"), test("t2", 5..9, 1 to "D"))
        assertEquals(CheckOutcome.PageTestUnknown, AnswerChecker.check(20, 1, "C", tests))
        // Several tests without page ranges: which one is unknowable.
        assertEquals(CheckOutcome.PageTestUnknown, AnswerChecker.check(0, 1, "C", listOf(test("a", null, 1 to "C"), test("b", null, 1 to "D"))))
    }

    @Test
    fun aSingleTestWithoutPagesCoversTheDocument() {
        assertEquals(AttemptResult.CORRECT, (AnswerChecker.check(30, 1, "C", listOf(test("t1", null, 1 to "C"))) as CheckOutcome.Graded).result)
    }

    @Test
    fun overlappingRangesPickTheTestThatHasTheQuestion() {
        val tests = listOf(test("t1", 0..5, 1 to "A", 2 to "B"), test("t2", 5..9, 21 to "E"))
        assertEquals("t2", (AnswerChecker.check(5, 21, "E", tests) as CheckOutcome.Graded).testId)
        assertEquals("t1", (AnswerChecker.check(5, 2, "B", tests) as CheckOutcome.Graded).testId)
    }

    @Test
    fun missingQuestionsAndKeylessDocumentsAreReported() {
        assertEquals(CheckOutcome.NoAnswerForQuestion(7), AnswerChecker.check(0, 7, "C", listOf(test("t1", 0..4, 1 to "C"))))
        assertEquals(CheckOutcome.NoKey, AnswerChecker.check(0, 1, "C", emptyList()))
        assertEquals(CheckOutcome.NoKey, AnswerChecker.check(0, 1, "C", listOf(test("empty", 0..4))))
    }
}
