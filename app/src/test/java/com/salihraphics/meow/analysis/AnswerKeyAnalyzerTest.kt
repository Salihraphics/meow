package com.salihraphics.meow.analysis

import com.salihraphics.meow.analysis.FixturePdf.Companion.questionPage
import com.salihraphics.meow.analysis.FixturePdf.Text
import com.salihraphics.meow.analysis.key.BookFixtures
import com.salihraphics.meow.analysis.key.BookFixtures.L
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.analysis.key.TestRef
import com.salihraphics.meow.analysis.text.OcrRegion
import com.salihraphics.meow.analysis.text.PageOcr
import com.salihraphics.meow.analysis.text.PdfBoxTextSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Real PDF files through real PdfBox extraction and the parser. OCR is a stand-in (ML Kit only
 * runs on a device), so scanned-book tests check the strategy, not recognition quality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnswerKeyAnalyzerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val analyzer by lazy { AnswerKeyAnalyzer(PdfBoxTextSource(RuntimeEnvironment.getApplication())) }

    /** Returns scripted OCR text and records what was asked for. */
    private class FakeOcr(private val answers: Map<kotlin.Pair<Int, OcrRegion>, PageText> = emptyMap()) : PageOcr {
        val reads = mutableListOf<kotlin.Pair<Int, OcrRegion>>()
        override suspend fun read(pageIndex: Int, region: OcrRegion): PageText? {
            reads += pageIndex to region
            return answers[pageIndex to region] ?: PageText(pageIndex, emptyList(), TextSource.OCR)
        }
    }

    private fun pdf(build: FixturePdf.() -> Unit) = FixturePdf().apply(build).write(tmp.newFile("book${System.nanoTime()}.pdf"))

    @Test
    fun textBookWithKeyAtTheEndNeedsNoOcr() = runBlocking {
        val file = pdf {
            page { questionPage("TEST 1", 1, 1) }
            page { questionPage(null, 3, 2) }
            page { questionPage("TEST 2", 1, 3) }
            page { questionPage(null, 3, 4) }
            page {
                add(Text(0.3f, 0.06f, "CEVAP ANAHTARI", 16f))
                add(Text(0.08f, 0.14f, "Test 1"))
                add(Text(0.08f, 0.18f, "1. D   2. A   3. C   4. B"))
                add(Text(0.08f, 0.26f, "Test 2"))
                add(Text(0.08f, 0.30f, "1-B   2-C   3-A   4-E"))
            }
        }
        val ocr = FakeOcr()
        val outcome = analyzer.analyze(file, ocr)
        assertEquals(5, outcome.pageCount)
        assertEquals(5, outcome.textPages)
        assertTrue(ocr.reads.isEmpty())
        val (t1, t2) = outcome.result.tests
        assertEquals(TestRef(1, "Test 1"), t1.ref)
        assertEquals(listOf("D", "A", "C", "B"), t1.answers.map { it.answer })
        assertEquals(listOf(0, 1), t1.questionPages)
        assertEquals(listOf(2, 3), t2.questionPages)
        assertEquals(listOf(4), t2.sourcePages)
    }

    @Test
    fun footerAnswersSpreadAcrossThePageAreStillOneLine() = runBlocking {
        val file = pdf {
            page {
                questionPage("TEST 4", 1, 7)
                add(Text(0.08f, 0.93f, "1.B", 8f))
                add(Text(0.45f, 0.93f, "2.D", 8f))
                add(Text(0.85f, 0.93f, "3.A", 8f))
            }
        }
        val test = analyzer.analyze(file, FakeOcr()).result.tests.single()
        assertEquals(KeySourceKind.PAGE_MARGIN, test.kind)
        assertEquals(listOf("B", "D", "A"), test.answers.map { it.answer })
        assertEquals(4, test.ref?.number)
    }

    @Test
    fun twoColumnKeyPageFromARealPdf() = runBlocking {
        val file = pdf {
            page {
                add(Text(0.35f, 0.05f, "Cevap Anahtarı", 15f))
                add(Text(0.08f, 0.12f, "Test 1")); add(Text(0.55f, 0.12f, "Test 2"))
                add(Text(0.08f, 0.16f, "1.A 2.B 3.C")); add(Text(0.55f, 0.16f, "1.E 2.E 3.D"))
                add(Text(0.08f, 0.20f, "4.D 5.A 6.B")); add(Text(0.55f, 0.20f, "4.C 5.B 6.A"))
            }
        }
        val tests = analyzer.analyze(file, FakeOcr()).result.tests
        assertEquals(listOf(1, 2), tests.map { it.ref?.number })
        assertEquals(listOf("A", "B", "C", "D", "A", "B"), tests[0].answers.map { it.answer })
        assertEquals(listOf("E", "E", "D", "C", "B", "A"), tests[1].answers.map { it.answer })
    }

    @Test
    fun rotatedPagesStillGivePositionsInsideThePage() = runBlocking {
        val file = pdf {
            page(rotation = 90) {
                add(Text(0.1f, 0.1f, "Cevap Anahtarı"))
                add(Text(0.1f, 0.2f, "1.C 2.A 3.B"))
            }
        }
        PdfBoxTextSource(RuntimeEnvironment.getApplication()).open(file).use { doc ->
            val pageText = doc.page(0)
            val boxes = pageText.lines.flatMap { line -> line.words.map { it.box } }
            assertTrue(boxes.isNotEmpty())
            assertTrue(boxes.all { it.left in -0.01f..1.01f && it.top in -0.01f..1.01f && it.right <= 1.01f && it.bottom <= 1.01f })
        }
        assertEquals(listOf("C", "A", "B"), analyzer.analyze(file, FakeOcr()).result.tests.single().answers.map { it.answer })
    }

    @Test
    fun aShortKeyPageWithLittleTextIsNotSkipped() = runBlocking {
        val file = pdf {
            page { questionPage("TEST 1", 1, 1) }
            page { add(Text(0.1f, 0.1f, "Yanıtlar")); add(Text(0.1f, 0.2f, "1.C 2.A")) }
        }
        assertEquals(listOf("C", "A"), analyzer.analyze(file, FakeOcr()).result.tests.single().answers.map { it.answer })
    }

    @Test
    fun scannedBookReadsOnlyTheLastPagesWithOcrWhenTheKeyIsThere() = runBlocking {
        val file = pdf { repeat(40) { scannedPage() } }
        val key = BookFixtures.page(39, L("CEVAP ANAHTARI", 0.05f), L("1.C 2.A 3.E 4.B", 0.1f), source = TextSource.OCR)
        val ocr = FakeOcr(mapOf((39 to OcrRegion.FULL_PAGE) to key))
        val outcome = analyzer.analyze(file, ocr)
        assertEquals(listOf("C", "A", "E", "B"), outcome.result.tests.single().answers.map { it.answer })
        assertEquals(0, outcome.textPages)
        // Last pages first, in full, and nothing more once the key was found.
        assertEquals(39, ocr.reads.first().first)
        assertTrue(ocr.reads.all { it.second == OcrRegion.FULL_PAGE })
        assertTrue(ocr.reads.size <= AnswerKeyAnalyzer.MAX_FULL_OCR_PAGES)
    }

    @Test
    fun scannedBookWithPerPageKeysFallsBackToMarginStrips() = runBlocking {
        val file = pdf { repeat(30) { scannedPage() } }
        val footer = BookFixtures.page(3, L("5.B · 6.D · 7.A", 0.93f), source = TextSource.OCR)
        val ocr = FakeOcr(mapOf((3 to OcrRegion.BOTTOM_MARGIN) to footer))
        val outcome = analyzer.analyze(file, ocr)
        val test = outcome.result.tests.single()
        assertEquals(KeySourceKind.PAGE_MARGIN, test.kind)
        assertEquals(listOf(3), test.questionPages)
        assertTrue(ocr.reads.any { it.second == OcrRegion.TOP_MARGIN })
        assertTrue("full pages are not read twice", ocr.reads.filter { it.second == OcrRegion.FULL_PAGE }.size <= AnswerKeyAnalyzer.MAX_FULL_OCR_PAGES)
    }

    @Test
    fun noKeyFoundIsReportedAsNotFound() = runBlocking {
        val file = pdf { repeat(3) { i -> page { questionPage("TEST 1", 1 + i * 2, i + 1) } } }
        assertFalse(analyzer.analyze(file, FakeOcr()).result.found)
    }

    @Test
    fun largeBookIsAnalysedAndCanBeCancelledPartWay() = runBlocking {
        val file = pdf {
            repeat(199) { i -> page { questionPage(if (i % 4 == 0) "TEST ${i / 4 + 1}" else null, 1 + (i % 4) * 2, i + 1) } }
            page {
                add(Text(0.3f, 0.05f, "CEVAP ANAHTARI", 16f))
                add(Text(0.08f, 0.12f, "Test 1  1.A 2.B 3.C 4.D 5.E 6.A 7.B 8.C"))
            }
        }
        val started = System.currentTimeMillis()
        val outcome = analyzer.analyze(file, FakeOcr())
        assertEquals(200, outcome.pageCount)
        assertEquals(8, outcome.result.tests.single { it.ref?.number == 1 }.answers.size)
        println("200-page analysis took ${System.currentTimeMillis() - started} ms")

        // Cancel once a fifth of the pages are read: the run must stop and return nothing.
        val job = Job()
        var lastProgress = 0f
        try {
            withContext(job) {
                analyzer.analyze(file, FakeOcr()) { p ->
                    lastProgress = p
                    if (p > 0.1f) job.cancel()
                }
            }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertTrue("stopped early, at $lastProgress", lastProgress < 0.2f)
        }
    }

    @Test(expected = IOException::class)
    fun aFileThatIsNotAPdfFailsClearly() {
        val file = tmp.newFile("not.pdf").apply { writeText("hello") }
        runBlocking { analyzer.analyze(file, FakeOcr()) }
    }
}
