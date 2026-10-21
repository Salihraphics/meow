package com.salihraphics.meow.analysis

import com.salihraphics.meow.analysis.key.BookFixtures
import com.salihraphics.meow.analysis.key.BookFixtures.L
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.analysis.text.OcrRegion
import com.salihraphics.meow.analysis.text.PageOcr
import com.salihraphics.meow.analysis.text.PdfTextDocument
import com.salihraphics.meow.analysis.text.PdfTextSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Books where the PDF's own text doesn't give the key: text that is unreadable (fonts without a
 * character map), keys whose letters are drawn rather than written, keys the student points at,
 * and keys in a separate PDF. The text source and OCR are scripted stand-ins.
 */
class AnswerKeyRecoveryTest {
    private class ScriptedText(private val pages: List<PageText>) : PdfTextSource {
        override fun open(file: File): PdfTextDocument = object : PdfTextDocument {
            override val pageCount = pages.size
            override fun page(index: Int) = pages[index]
            override fun close() = Unit
        }
    }

    private class ScriptedOcr(private val pages: Map<Int, PageText>) : PageOcr {
        val reads = mutableListOf<kotlin.Pair<Int, OcrRegion>>()
        override suspend fun read(pageIndex: Int, region: OcrRegion): PageText? {
            reads += pageIndex to region
            return if (region == OcrRegion.FULL_PAGE) pages[pageIndex] else null
        }
    }

    private val file = File("unused.pdf")

    private fun questionPages(count: Int) = (0 until count).map { BookFixtures.questionPage(it, if (it % 2 == 0) "TEST ${it / 2 + 1}" else null, 1) }

    private fun keyByOcr(index: Int) = BookFixtures.page(
        index, L("CEVAP ANAHTARI", 0.05f), L("Test 1 1.C 2.A 3.D 4.B", 0.15f), L("Test 2 1.E 2.B 3.A 4.C", 0.2f),
        source = TextSource.OCR,
    )

    /** What a PDF with a broken font map gives: symbols and letter soup. */
    private fun garbled(index: Int) = BookFixtures.page(
        index, L("ÿþ ¤¦ ˆ‰Š ¢£", 0.05f), L("Bpbgfdtv Wxzqr ¬®¯ Ł Ř", 0.15f), L("¸¹º »¼½ ÞßÐ ÐÑÒ", 0.2f), L("Krtpsd Mnbvc ¤¤ ¦¦", 0.25f),
    )

    @Test
    fun readableTextIsToldApartFromBrokenFontText() {
        assertTrue(AnswerKeyAnalyzer.isReadable(BookFixtures.questionPage(0, "TEST 1", 1)))
        assertTrue(AnswerKeyAnalyzer.isReadable(BookFixtures.page(0, L("1.A 2.B 3.C 4.D 5.E", 0.1f), L("x+y=12 ise 2x kaçtır?", 0.2f))))
        assertFalse(AnswerKeyAnalyzer.isReadable(garbled(0)))
        assertFalse(AnswerKeyAnalyzer.isReadable(BookFixtures.page(0, L("  ", 0.1f))))
    }

    @Test
    fun aKeyPageWithUnreadableTextIsReadWithOcr() = runBlocking {
        val pages = questionPages(4) + garbled(4)
        val ocr = ScriptedOcr(mapOf(4 to keyByOcr(4)))
        val outcome = AnswerKeyAnalyzer(ScriptedText(pages)).analyze(file, ocr)
        assertEquals(listOf(4), outcome.unreadablePages)
        assertEquals(listOf("CADB", "EBAC"), outcome.result.tests.map { t -> t.answers.joinToString("") { it.answer } })
        assertEquals(listOf(4), outcome.ocrPages)
        assertEquals(1, outcome.clues.scannedPages)
    }

    @Test
    fun aKeyHeadingWithDrawnAnswersIsReadWithOcrFirst() = runBlocking {
        // The heading is text; the letters are drawn shapes, so the page text has no answers.
        val pages = questionPages(30) + BookFixtures.page(30, L("CEVAP ANAHTARI", 0.05f), L("Test 1", 0.15f), L("Test 2", 0.2f))
        val ocr = ScriptedOcr(mapOf(30 to keyByOcr(30)))
        val outcome = AnswerKeyAnalyzer(ScriptedText(pages)).analyze(file, ocr)
        assertEquals(30 to OcrRegion.FULL_PAGE, ocr.reads.first())
        assertTrue(outcome.result.tests.any { it.kind == KeySourceKind.KEY_SECTION })
        assertEquals(listOf(0, 1), outcome.result.tests.first().questionPages)
    }

    @Test
    fun aKeyAlreadyFoundInTheTextCostsNoOcr() = runBlocking {
        val pages = questionPages(4) + BookFixtures.page(4, L("CEVAP ANAHTARI", 0.05f), L("Test 1 1.C 2.A 3.D 4.B", 0.15f))
        val ocr = ScriptedOcr(emptyMap())
        AnswerKeyAnalyzer(ScriptedText(pages)).analyze(file, ocr)
        assertTrue(ocr.reads.isEmpty())
    }

    @Test
    fun withNothingFoundTheLastPagesAreReadWithOcrAndTheCluesSaySo() = runBlocking {
        val pages = questionPages(12)
        val ocr = ScriptedOcr(emptyMap())
        val outcome = AnswerKeyAnalyzer(ScriptedText(pages)).analyze(file, ocr)
        assertFalse(outcome.result.found)
        assertEquals((11 downTo 4).toList(), ocr.reads.map { it.first })
        assertTrue(outcome.clues.headingPages.isEmpty())
        assertEquals(0, outcome.clues.answerLines)
    }

    @Test
    fun pagesTheStudentPointsAtAreReadAsTheKey() = runBlocking {
        val pages = questionPages(10) + BookFixtures.page(10, L("1 C   2 A   3 D", 0.2f), L("4 B   5 E   6 C", 0.25f)) + garbled(11)
        val ocr = ScriptedOcr(mapOf(11 to keyByOcr(11)))
        val outcome = AnswerKeyAnalyzer(ScriptedText(pages)).analyzeKeyPages(file, listOf(10, 11), ocr)
        val letters = outcome.result.tests.map { t -> t.answers.joinToString("") { it.answer } }
        assertEquals(listOf("CADBEC", "CADB", "EBAC"), letters)
        assertEquals(listOf(11 to OcrRegion.FULL_PAGE), ocr.reads) // page 10's text was enough
    }

    @Test
    fun aSeparateKeyPdfIsReadWhole() = runBlocking {
        val keyPdf = listOf(
            BookFixtures.page(0, L("TYT Matematik Cevap Anahtarları", 0.05f)),
            BookFixtures.page(1, L("1. GÜN", 0.05f), L("TEST 1 1-B 2-B 3-C 4-D", 0.1f), L("TEST 2 1-A 2-E 3-E 4-A", 0.14f)),
        )
        val outcome = AnswerKeyAnalyzer(ScriptedText(keyPdf)).analyzeKeyPages(file, null, null)
        assertEquals(listOf("1. GÜN · TEST 1", "1. GÜN · TEST 2"), outcome.result.tests.map { it.ref?.label })
    }
}
