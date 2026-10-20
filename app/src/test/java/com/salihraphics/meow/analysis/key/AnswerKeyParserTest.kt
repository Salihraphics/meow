package com.salihraphics.meow.analysis.key

import com.salihraphics.meow.analysis.key.BookFixtures.L
import com.salihraphics.meow.analysis.key.BookFixtures.page
import com.salihraphics.meow.analysis.key.BookFixtures.questionPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerKeyParserTest {
    private val parser = AnswerKeyParser()

    private fun DetectedTest.letters() = answers.associate { it.question to it.answer }

    @Test
    fun keyAtTheEndOfTheBookIsSplitIntoTestsAndLinkedToQuestionPages() {
        val book = listOf(
            questionPage(0, "TEST 1", 1),
            questionPage(1, null, 3),
            questionPage(2, "TEST 2", 1),
            questionPage(3, null, 3),
            page(
                4,
                L("CEVAP ANAHTARI", 0.05f),
                L("Test 1", 0.12f),
                L("1. D 2. A 3. C 4. B", 0.16f),
                L("Test 2", 0.25f),
                L("1-B 2-C 3-A 4-E", 0.29f),
            ),
        )
        val result = parser.parse(book)
        assertEquals(2, result.tests.size)
        val (t1, t2) = result.tests
        assertEquals(TestRef(1, "Test 1"), t1.ref)
        assertEquals(mapOf(1 to "D", 2 to "A", 3 to "C", 4 to "B"), t1.letters())
        assertEquals(listOf(0, 1), t1.questionPages)
        assertEquals(listOf(4), t1.sourcePages)
        assertEquals(KeySourceKind.KEY_SECTION, t1.kind)
        assertEquals(mapOf(1 to "B", 2 to "C", 3 to "A", 4 to "E"), t2.letters())
        assertEquals(listOf(2, 3), t2.questionPages)
        assertFalse(t1.needsReview)
    }

    @Test
    fun commonAnswerFormatsAreAllRead() {
        for (line in listOf("1. D  2. A  3. C", "1-D, 2-A, 3-C", "1) D, 2) A, 3) C", "1:D 2:A 3:C", "1D 2A 3C", "1 – D · 2 – A · 3 – C")) {
            val result = parser.parse(listOf(page(0, L("Cevap Anahtarı", 0.05f), L(line, 0.2f))))
            assertEquals(line, mapOf(1 to "D", 2 to "A", 3 to "C"), result.tests.single().letters())
        }
    }

    @Test
    fun testLabelOnTheSameLineAsItsAnswers() {
        val result = parser.parse(listOf(page(9, L("Test 5: 1-B, 2-E, 3-C", 0.3f), L("Test 6: 1-A, 2-A, 3-D", 0.35f), L("Test 7: 1-C, 2-B, 3-E", 0.4f))))
        assertEquals(listOf(5, 6, 7), result.tests.map { it.ref?.number })
        assertEquals(mapOf(1 to "B", 2 to "E", 3 to "C"), result.tests[0].letters())
    }

    @Test
    fun turkishAndEnglishHeadingsAreRecognised() {
        for (heading in listOf("YANITLAR", "Test Cevapları", "Answer Key", "Cevaplar", "CEVAP ANAHTARI")) {
            assertTrue(heading, parser.isKeyHeading(heading))
        }
        // Words that merely contain a heading, or long sentences, are not headings.
        assertFalse(parser.isKeyHeading("Aşağıdaki cevaplardan hangisi doğrudur ve neden böyle olduğunu açıklayınız?"))
    }

    @Test
    fun footerAnswersBelongToTheirPageAndItsTest() {
        val book = listOf(
            questionPage(10, "TEST 3", 5, L("5.B · 6.D · 7.A", 0.93f)),
            questionPage(11, null, 8, L("8.C · 9.E · 10.A", 0.93f)),
        )
        val test = parser.parse(book).tests.single()
        assertEquals(KeySourceKind.PAGE_MARGIN, test.kind)
        assertEquals(TestRef(3, "TEST 3"), test.ref)
        assertEquals(mapOf(5 to "B", 6 to "D", 7 to "A", 8 to "C", 9 to "E", 10 to "A"), test.letters())
        assertEquals(listOf(10, 11), test.questionPages)
        assertFalse("margin keys don't have to start at 1", test.issues.any { it is TestIssue.Missing })
    }

    @Test
    fun answersInThePageHeaderToo() {
        val test = parser.parse(listOf(page(3, L("1-C 2-A 3-E 4-D", 0.03f), L("1. Soru metni burada yer alır.", 0.2f)))).tests.single()
        assertEquals(KeySourceKind.PAGE_MARGIN, test.kind)
        assertEquals(4, test.answers.size)
    }

    @Test
    fun pageNumbersQuestionTextAndOptionLinesAreNotAnswers() {
        val book = (0 until 6).map { questionPage(it, if (it % 2 == 0) "TEST ${it / 2 + 1}" else null, 1) } + page(
            6,
            L("9. Sınıf Matematik", 0.03f),
            L("1. Ünite: Kümeler", 0.2f),
            L("A) 1 B) 2 C) 3 D) 4", 0.3f),
            L("A. 1 B. 2 C. 3 D. 4", 0.35f),
            L("2023 A Kitapçığı", 0.4f),
            L("Sayfa 12", 0.95f),
        )
        assertFalse(parser.parse(book).found)
    }

    @Test
    fun numberingThatRestartsStartsANewTest() {
        val result = parser.parse(listOf(page(20, L("Cevaplar", 0.05f), L("1.A 2.B 3.C 4.D", 0.1f), L("1.E 2.D 3.C", 0.15f))))
        assertEquals(2, result.tests.size)
        assertEquals(mapOf(1 to "E", 2 to "D", 3 to "C"), result.tests[1].letters())
        assertNull(result.tests[1].ref)
    }

    @Test
    fun answerGridsWithAndWithoutRowLabels() {
        val labelled = parser.parse(
            listOf(page(30, L("CEVAP ANAHTARI", 0.05f), L("1 2 3 4 5", 0.1f), L("Test 1 A B C D E", 0.14f), L("Test 2 E D C B A", 0.18f))),
        )
        assertEquals(listOf(1, 2), labelled.tests.map { it.ref?.number })
        assertEquals(mapOf(1 to "E", 2 to "D", 3 to "C", 4 to "B", 5 to "A"), labelled.tests[1].letters())

        val plain = parser.parse(listOf(page(31, L("Yanıtlar", 0.05f), L("1 2 3 4", 0.1f), L("C A B D", 0.14f))))
        assertEquals(mapOf(1 to "C", 2 to "A", 3 to "B", 4 to "D"), plain.tests.single().letters())
    }

    @Test
    fun twoColumnKeyPagesKeepEachColumnsTestTogether() {
        val result = parser.parse(
            listOf(
                page(
                    40,
                    L("Test 1", 0.10f, 0.08f, 0.4f), L("Test 2", 0.10f, 0.55f, 0.9f),
                    L("1.A 2.B 3.C", 0.15f, 0.08f, 0.4f), L("1.E 2.E 3.E", 0.15f, 0.55f, 0.9f),
                    L("4.D 5.A 6.B", 0.20f, 0.08f, 0.4f), L("4.C 5.C 6.C", 0.20f, 0.55f, 0.9f),
                ),
            ),
        )
        assertEquals(listOf(1, 2), result.tests.map { it.ref?.number })
        assertEquals(mapOf(1 to "A", 2 to "B", 3 to "C", 4 to "D", 5 to "A", 6 to "B"), result.tests[0].letters())
        assertEquals(setOf("E", "C"), result.tests[1].letters().values.toSet())
    }

    @Test
    fun gapsAreReportedNotFilled() {
        val test = parser.parse(listOf(page(0, L("Cevap Anahtarı", 0.05f), L("1.A 2.B 4.D 5.E 6.A", 0.1f)))).tests.single()
        assertEquals(listOf(1, 2, 4, 5, 6), test.answers.map { it.question })
        assertTrue(TestIssue.Missing(listOf(3)) in test.issues)
        assertTrue(test.needsReview)
    }

    @Test
    fun conflictingAnswersAreKeptAsAConflictAndRepeatsAreMerged() {
        val conflict = parser.parse(listOf(page(0, L("Cevap Anahtarı", 0.05f), L("1.A 2.B 2.D 3.C", 0.1f)))).tests.single()
        val second = conflict.answers.single { it.question == 2 }
        assertEquals(AnswerNote.CONFLICT, second.note)
        assertEquals(listOf("B", "D"), second.alternatives)
        assertEquals(Confidence.LOW, second.confidence)
        assertTrue(TestIssue.Conflict(2, listOf("B", "D")) in conflict.issues)

        val repeat = parser.parse(listOf(page(0, L("Cevap Anahtarı", 0.05f), L("1.A 2.B 2.B 3.C", 0.1f)))).tests.single()
        assertEquals(3, repeat.answers.size)
        assertFalse(repeat.needsReview)
    }

    @Test
    fun ocrMisreadsAreKeptButMarkedUncertain() {
        val footer = parser.parse(listOf(page(5, L("5.8 6.D 7.A", 0.93f)))).tests.single()
        val five = footer.answers.single { it.question == 5 }
        assertEquals("B", five.answer)
        assertEquals(Confidence.LOW, five.confidence)
        assertEquals(AnswerNote.OCR_GUESS, five.note)
        assertEquals(Confidence.HIGH, footer.answers.single { it.question == 6 }.confidence)

        val key = parser.parse(listOf(page(0, L("Cevap Anahtarı", 0.05f), L("l.C 2.A 3.0 4.b", 0.1f)))).tests.single()
        assertEquals(mapOf(1 to "C", 2 to "A", 3 to "D", 4 to "B"), key.letters())
        assertEquals(Confidence.LOW, key.answers.single { it.question == 1 }.confidence)
        assertEquals(Confidence.HIGH, key.answers.single { it.question == 4 }.confidence)
    }

    @Test
    fun aSingleStrayPairIsNotAKey() {
        assertFalse(parser.parse(listOf(page(0, L("5.B", 0.93f)))).found)
    }

    @Test
    fun fourOptionBooksWork() {
        val lgs = AnswerKeyParser('A'..'D')
        val test = lgs.parse(listOf(page(0, L("Cevap Anahtarı", 0.05f), L("1.A 2.D 3.C 4.B", 0.1f)))).tests.single()
        assertEquals(4, test.answers.size)
        assertFalse(lgs.parse(listOf(page(0, L("Cevap Anahtarı", 0.05f), L("1.E 2.E 3.E", 0.1f)))).found)
    }

    @Test
    fun noKeyMeansNoKey() {
        assertFalse(parser.parse((0 until 4).map { questionPage(it, "TEST 1", 1) }).found)
        assertFalse(parser.parse(emptyList()).found)
    }
}
