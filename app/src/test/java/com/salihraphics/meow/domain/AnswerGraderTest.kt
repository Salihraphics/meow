package com.salihraphics.meow.domain

import com.salihraphics.meow.domain.model.AnswerConfidence
import com.salihraphics.meow.domain.model.AnswerGrader
import com.salihraphics.meow.domain.model.AnswerOrigin
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.KeyAnswer
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerGraderTest {
    private fun key(type: AnswerType, value: String, tolerance: Double? = null) =
        KeyAnswer("a", "t", 1, type, value, tolerance, AnswerOrigin.MANUAL, AnswerConfidence.HIGH, null, emptyList(), null)

    @Test
    fun choices() {
        assertEquals(AttemptResult.CORRECT, AnswerGrader.grade(key(AnswerType.CHOICE, "C"), "c"))
        assertEquals(AttemptResult.INCORRECT, AnswerGrader.grade(key(AnswerType.CHOICE, "D"), "B"))
    }

    @Test
    fun numbersWithDecimalCommaFractionsAndTolerance() {
        assertEquals(AttemptResult.CORRECT, AnswerGrader.grade(key(AnswerType.NUMBER, "12,5"), "12.5"))
        assertEquals(AttemptResult.CORRECT, AnswerGrader.grade(key(AnswerType.NUMBER, "0.25"), "1/4"))
        assertEquals(AttemptResult.CORRECT, AnswerGrader.grade(key(AnswerType.NUMBER, "3.14", 0.01), "3,141"))
        assertEquals(AttemptResult.INCORRECT, AnswerGrader.grade(key(AnswerType.NUMBER, "3.14", 0.001), "3.2"))
        assertEquals(AttemptResult.NEEDS_SELF_CHECK, AnswerGrader.grade(key(AnswerType.NUMBER, "7"), "yedi"))
    }

    @Test
    fun writtenAnswersAreNeverMarkedWrongAutomatically() {
        assertEquals(AttemptResult.CORRECT, AnswerGrader.grade(key(AnswerType.TEXT, "Fotosentez"), "  FOTOSENTEZ. "))
        assertEquals(AttemptResult.CORRECT, AnswerGrader.grade(key(AnswerType.TEXT, "Işık"), "isik"))
        assertEquals(AttemptResult.NEEDS_SELF_CHECK, AnswerGrader.grade(key(AnswerType.TEXT, "fotosentez"), "bitkiler güneşle besin üretir"))
    }
}
