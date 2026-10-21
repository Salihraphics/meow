package com.salihraphics.meow.analysis.key

import com.salihraphics.meow.analysis.key.BookFixtures.L
import com.salihraphics.meow.analysis.key.BookFixtures.page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Key layouts beyond "Test N: 1.C 2.A" at the end of the book: books split into days or units
 * whose test numbers repeat, answer tables closing each test, one-answer-per-line tables,
 * circled or bracketed letters, and pages the student points at.
 */
class AnswerKeyLayoutsTest {
    private val parser = AnswerKeyParser()

    private fun DetectedTest.letters() = answers.joinToString("") { it.answer }

    /** A question page; [day] and [test] headers at the top when given. */
    private fun questions(index: Int, day: String?, test: String?, first: Int) = page(
        index,
        *listOfNotNull(
            day?.let { L(it, 0.03f, 0.08f, 0.3f) },
            test?.let { L(it, 0.07f, 0.08f, 0.3f) },
            L("$first. Aşağıdakilerden hangisi doğrudur?", 0.15f),
            L("A) 12 B) 15 C) 18 D) 21 E) 24", 0.20f),
            L("${first + 1}. Buna göre x kaçtır?", 0.45f),
            L("A) 1 B) 2 C) 3 D) 4 E) 5", 0.50f),
            L("${index + 1}", 0.96f, 0.48f, 0.52f),
        ).toTypedArray(),
    )

    @Test
    fun daysWithRepeatingTestNumbersStayApartAndFindTheirPages() {
        val book = listOf(
            questions(0, "1. GÜN", "TEST 1", 1), questions(1, "1. GÜN", null, 3),
            questions(2, "1. GÜN", "TEST 2", 1), questions(3, "1. GÜN", null, 3),
            questions(4, "2. GÜN", "TEST 1", 1), questions(5, "2. GÜN", null, 3),
            questions(6, "2. GÜN", "TEST 2", 1), questions(7, "2. GÜN", null, 3),
            page(
                8,
                L("CEVAP ANAHTARI", 0.04f),
                L("1. GÜN", 0.10f), L("TEST 1: 1.C 2.A 3.D 4.B", 0.14f), L("TEST 2: 1.E 2.E 3.A 4.C", 0.18f),
                L("2. GÜN", 0.26f), L("TEST 1: 1.B 2.B 3.C 4.D", 0.30f), L("TEST 2: 1.A 2.D 3.E 4.B", 0.34f),
            ),
        )
        val tests = parser.parse(book).tests
        assertEquals(listOf("1. GÜN · TEST 1", "1. GÜN · TEST 2", "2. GÜN · TEST 1", "2. GÜN · TEST 2"), tests.map { it.ref?.label })
        assertEquals(listOf("CADB", "EEAC", "BBCD", "ADEB"), tests.map { it.letters() })
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5), listOf(6, 7)), tests.map { it.questionPages })
    }

    @Test
    fun aDayWithoutTestsIsOneTestCoveringItsPages() {
        val book = listOf(
            questions(0, "GÜN 1", null, 1), questions(1, "GÜN 1", null, 3),
            questions(2, "GÜN 2", null, 1),
            page(3, L("CEVAPLAR", 0.04f), L("GÜN 1 1-C 2-A 3-D 4-B", 0.12f), L("GÜN 2 1-E 2-D", 0.16f)),
        )
        val (day1, day2) = parser.parse(book).tests
        assertEquals("GÜN 1", day1.ref?.label)
        assertEquals("CADB", day1.letters())
        assertEquals(listOf(0, 1), day1.questionPages)
        assertEquals(listOf(2), day2.questionPages)
    }

    @Test
    fun answerTablesClosingEachTestAreFoundAmongTheQuestions() {
        fun table(index: Int, first: Int) = page(
            index,
            L("${first}. Aşağıdakilerden hangisi doğrudur?", 0.15f),
            L("A) 12 B) 15 C) 18 D) 21 E) 24", 0.20f),
            L("1 2 3 4 5 6 7 8", 0.80f),
            L("C A D B E C A D", 0.84f),
        )
        val book = listOf(questions(0, null, "TEST 1", 1), table(1, 3), questions(2, null, "TEST 2", 1), table(3, 3))
        val tests = parser.parse(book).tests
        assertEquals(2, tests.size)
        assertTrue(tests.all { it.kind == KeySourceKind.IN_TEST })
        assertEquals(listOf("CADBECAD", "CADBECAD"), tests.map { it.letters() })
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), tests.map { it.questionPages })
        assertEquals(listOf(TestRef(1, "TEST 1"), TestRef(2, "TEST 2")), tests.map { it.ref })
    }

    @Test
    fun twoTablesOnOnePageAreTwoTestsAndASplitTableIsOne() {
        val page = page(
            5,
            L("TEST 1", 0.10f), L("1 2 3 4 5 6 7 8", 0.14f), L("C A D B E C A D", 0.18f), L("9 10 11 12", 0.22f), L("B B C A", 0.26f),
            L("TEST 2", 0.40f), L("1 2 3 4", 0.44f), L("TEST 2 E D C B", 0.48f),
        )
        val tests = parser.parse(listOf(questions(0, null, null, 1), page)).tests.filter { it.kind == KeySourceKind.IN_TEST }
        assertEquals(listOf("CADBECADBBCA", "EDCB"), tests.map { it.letters() })
    }

    @Test
    fun tableRowsMayBeLabelled() {
        val book = listOf(questions(0, null, null, 1), page(1, L("Soru 1 2 3 4 5", 0.8f), L("Cevap B D A C E", 0.84f)))
        assertEquals("BDACE", parser.parse(book).tests.single().letters())
    }

    @Test
    fun aKeyPrintedOneAnswerPerLineIsAKey() {
        val lines = (1..12).map { q -> L("$q. ${"ABCDE"[q % 5]}", 0.05f + q * 0.06f, 0.1f, 0.3f) }
        val tests = parser.parse(listOf(page(40, *lines.toTypedArray()))).tests
        assertEquals(12, tests.single().answers.size)
        assertEquals(KeySourceKind.KEY_SECTION, tests.single().kind)
    }

    @Test
    fun circledAndBracketedLettersAreRead() {
        val book = listOf(page(9, L("CEVAP ANAHTARI", 0.05f), L("1.Ⓒ 2.Ⓐ 3.Ⓓ 4.Ⓑ", 0.12f), L("5.(E) 6.(A) 7.(C) 8.(B)", 0.16f)))
        assertEquals("CADBEACB", parser.parse(book).tests.single().letters())
    }

    @Test
    fun pagesTheStudentPointsAtAreReadEvenWithoutAHeading() {
        // Three short lines: not enough to look like a key on their own.
        val keyPage = page(30, L("1 C   2 A   3 D", 0.2f), L("4 B   5 E   6 C", 0.25f))
        assertFalse(parser.parse(listOf(keyPage)).found)
        assertEquals("CADBEC", parser.parse(listOf(keyPage), forcedKeyPages = setOf(30)).tests.single().letters())
    }

    @Test
    fun cluesSayWhereAHeadingWasSeenWithoutAnswers() {
        // The heading is text but the answers are drawn: nothing to read.
        val book = listOf(questions(0, null, "TEST 1", 1), page(1, L("CEVAP ANAHTARI", 0.05f)))
        val result = parser.parse(book)
        assertFalse(result.found)
        assertEquals(listOf(1), result.clues.headingPages)
        assertEquals(0, result.clues.answerLines)
    }

    @Test
    fun aDayHeaderOnAQuestionPageIsNotATestAndQuestionLinesAreNotAnswers() {
        val book = (0 until 6).map { questions(it, "${it / 2 + 1}. GÜN", if (it % 2 == 0) "TEST 1" else null, 1) }
        assertFalse(parser.parse(book).found)
    }
}
