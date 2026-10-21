package com.salihraphics.meow.analysis

import com.salihraphics.meow.analysis.FixturePdf.Companion.questionPage
import com.salihraphics.meow.analysis.FixturePdf.Text
import com.salihraphics.meow.analysis.key.AnswerNote
import com.salihraphics.meow.analysis.key.BookFixtures
import com.salihraphics.meow.analysis.key.BookFixtures.L
import com.salihraphics.meow.analysis.key.DetectedTest
import com.salihraphics.meow.analysis.text.OcrRegion
import com.salihraphics.meow.analysis.text.PageOcr
import com.salihraphics.meow.analysis.text.PdfBoxTextSource
import com.salihraphics.meow.analysis.text.PdfTextDocument
import com.salihraphics.meow.analysis.text.PdfTextSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The failure matrix from the discovery audit (docs/ANSWER_DISCOVERY_AUDIT.md), one test per
 * case that the audit found failing in code. Each asserts the expected behaviour; the audit
 * records which of them failed before the fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryAuditTest {
    @get:Rule val tmp = TemporaryFolder()

    private val textSource by lazy { PdfBoxTextSource(RuntimeEnvironment.getApplication()) }
    private val analyzer by lazy { AnswerKeyAnalyzer(textSource) }

    private fun pdf(build: FixturePdf.() -> Unit): File = FixturePdf().apply(build).write(tmp.newFile("book${System.nanoTime()}.pdf"))

    private fun analyze(file: File) = runBlocking { analyzer.analyze(file, ocr = null) }

    private fun DetectedTest.letters() = answers.associate { it.question to it.answer }

    private class ScriptedText(private val pages: List<PageText>) : PdfTextSource {
        override fun open(file: File): PdfTextDocument = object : PdfTextDocument {
            override val pageCount = pages.size
            override fun page(index: Int) = pages[index]
            override fun close() = Unit
        }
    }

    private class ScriptedOcr(private val pages: Map<Int, PageText>) : PageOcr {
        val reads = mutableListOf<Int>()
        override suspend fun read(pageIndex: Int, region: OcrRegion): PageText? {
            if (region == OcrRegion.FULL_PAGE) reads += pageIndex
            return if (region == OcrRegion.FULL_PAGE) pages[pageIndex] else null
        }
    }

    /** A solution: its number at the left margin, two indented step lines, then [answer] at [answerX]. */
    private fun MutableList<Text>.solution(number: String, y: Float, answer: String, answerX: Float = 0.10f): Float {
        add(Text(0.06f, y, number)); add(Text(0.10f, y, "Verilen ifadede x yalnız bırakılır."))
        add(Text(0.10f, y + 0.035f, "1. adımda her iki taraf 2 ile çarpılır."))
        add(Text(0.10f, y + 0.07f, "2. adımda sadeleştirilir."))
        add(Text(answerX, y + 0.105f, answer))
        return y + 0.16f
    }

    // ---- M3: "CEVAP: X" on worked-solution pages -----------------------------------------------

    @Test
    fun m3a_rightAlignedAnswerLabelsOnASingleColumnPage() {
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page {
                    add(Text(0.08f, 0.04f, "TEST 1 ÇÖZÜMLERİ", 14f))
                    var y = 0.12f
                    for ((q, a) in listOf(1 to "C", 2 to "A", 3 to "E")) y = solution("$q.", y, "CEVAP: $a", answerX = 0.78f)
                }
            },
        ).result
        assertEquals(mapOf(1 to "C", 2 to "A", 3 to "E"), result.tests.single().letters())
    }

    @Test
    fun m3b_answerLetterPrintedApartFromItsLabel() {
        // "CEVAP:" and the letter in a box further right: text extraction splits them into two lines.
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page {
                    add(Text(0.08f, 0.04f, "TEST 1 ÇÖZÜMLERİ", 14f))
                    var y = 0.12f
                    for ((q, a) in listOf(1 to "D", 2 to "B", 3 to "C")) {
                        y = solution("$q.", y, "CEVAP:", answerX = 0.60f)
                        add(Text(0.75f, y - 0.16f + 0.105f, a, 14f))
                    }
                }
            },
        ).result
        assertEquals(mapOf(1 to "D", 2 to "B", 3 to "C"), result.tests.single().letters())
    }

    @Test
    fun m3c_aSolutionStartingAtTheTopOfThePage() {
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page { add(Text(0.08f, 0.02f, "TEST 1 ÇÖZÜMLERİ", 12f)); var y = 0.06f; for ((q, a) in listOf(1 to "B", 2 to "D")) y = solution("$q.", y, "Cevap: $a") }
            },
        ).result
        assertEquals(mapOf(1 to "B", 2 to "D"), result.tests.single().letters())
    }

    @Test
    fun m3d_solutionNumbersPrintedAgainstTheirText() {
        // "1.Verilen…": no space after the number.
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page {
                    add(Text(0.08f, 0.04f, "TEST 1 ÇÖZÜMLERİ", 14f))
                    add(Text(0.06f, 0.12f, "1.Verilen ifadede x yalnız bırakılır.")); add(Text(0.10f, 0.16f, "Cevap: E"))
                    add(Text(0.06f, 0.24f, "2.Her iki taraf sadeleştirilir.")); add(Text(0.10f, 0.28f, "Cevap: A"))
                }
            },
        ).result
        assertEquals(mapOf(1 to "E", 2 to "A"), result.tests.single().letters())
    }

    @Test
    fun m3e_pointingAtSolutionPagesReadsTheirLabels() = runBlocking {
        val file = pdf {
            page { questionPage("TEST 1", 1, 1) }
            page { var y = 0.12f; for ((q, a) in listOf(1 to "C", 2 to "B")) y = solution("$q.", y, "CEVAP: $a") }
        }
        val outcome = analyzer.analyzeKeyPages(file, listOf(1), ocr = null)
        assertEquals(mapOf(1 to "C", 2 to "B"), outcome.result.tests.single().letters())
    }

    // ---- M7 / C: solutions for more than one test on a page ------------------------------------

    @Test
    fun m7_aPageWithSolutionsForTwoTestsKeepsEachInItsTest() {
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page { questionPage("TEST 2", 1, 2) }
                page {
                    add(Text(0.08f, 0.04f, "ÇÖZÜMLER", 14f))
                    add(Text(0.08f, 0.09f, "TEST 1"))
                    var y = solution("3.", 0.13f, "CEVAP: D")
                    y = solution("4.", y, "CEVAP: A")
                    add(Text(0.08f, y, "TEST 2"))
                    y = solution("1.", y + 0.04f, "CEVAP: B")
                    solution("2.", y, "CEVAP: E")
                }
            },
        ).result
        val byLabel = result.tests.associate { it.ref?.label to it.letters() }
        assertEquals(mapOf(3 to "D", 4 to "A"), byLabel["TEST 1"])
        assertEquals(mapOf(1 to "B", 2 to "E"), byLabel["TEST 2"])
    }

    @Test
    fun c_solutionsWithoutATestNameDoNotInheritTheLastTestsName() {
        // The book's last questions are TEST 3; the solutions at the end name no test.
        val result = analyze(
            pdf {
                page { questionPage("TEST 3", 1, 1) }
                page { add(Text(0.08f, 0.04f, "ÇÖZÜMLER", 14f)); var y = 0.12f; for ((q, a) in listOf(1 to "C", 2 to "B", 3 to "A")) y = solution("$q.", y, "CEVAP: $a") }
            },
        ).result
        val test = result.tests.single()
        assertNull("not silently TEST 3", test.ref)
    }

    @Test
    fun c_differentKindsOfTestWithTheSameNumberAreNotMerged() {
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page { questionPage("DENEME 1", 1, 2) }
                page {
                    add(Text(0.35f, 0.05f, "CEVAP ANAHTARI", 15f))
                    add(Text(0.08f, 0.12f, "Test 1   1.C   2.A   3.D   4.B"))
                    add(Text(0.08f, 0.18f, "Deneme 1   1.E   2.E   3.A   4.C"))
                }
            },
        ).result
        assertEquals(2, result.tests.size)
        assertTrue(result.tests.none { t -> t.answers.any { it.note == AnswerNote.CONFLICT } })
    }

    // ---- M6: numbering restarting at 1 ---------------------------------------------------------

    @Test
    fun m6_numberingRestartingWithinOneLineStartsANewTestNotConflicts() {
        val result = analyze(
            pdf {
                page { questionPage(null, 1, 1) }
                page {
                    add(Text(0.35f, 0.05f, "CEVAP ANAHTARI", 15f))
                    add(Text(0.08f, 0.12f, "1.A 2.B 3.C 4.D 1.E 2.A 3.B 4.C"))
                }
            },
        ).result
        assertEquals(listOf(mapOf(1 to "A", 2 to "B", 3 to "C", 4 to "D"), mapOf(1 to "E", 2 to "A", 3 to "B", 4 to "C")), result.tests.map { it.letters() })
        assertTrue(result.tests.none { t -> t.answers.any { it.note == AnswerNote.CONFLICT } })
    }

    // ---- M11: mixed native text and scans ------------------------------------------------------

    private fun keyLines(index: Int, vararg tests: kotlin.Pair<Int, String>) = BookFixtures.page(
        index, *tests.mapIndexed { i, (n, letters) -> L("Test $n " + letters.mapIndexed { k, c -> "${k + 1}.$c" }.joinToString(" "), 0.15f + i * 0.05f) }.toTypedArray(),
    )

    @Test
    fun m11a_aKeyPartlyTextAndPartlyScannedIsReadWhole() = runBlocking {
        val questions = (0 until 8).map { BookFixtures.questionPage(it, if (it % 2 == 0) "TEST ${it / 2 + 1}" else null, 1) }
        val textKey = BookFixtures.page(8, L("CEVAP ANAHTARI", 0.05f), L("Test 1 1.C 2.A 3.D 4.B", 0.15f), L("Test 2 1.E 2.B 3.A 4.C", 0.2f))
        val scanned = PageText(9, emptyList(), TextSource.EMBEDDED)
        val ocr = ScriptedOcr(mapOf(9 to keyLines(9, 3 to "ABCD", 4 to "DCBA").copy(source = TextSource.OCR)))
        val outcome = AnswerKeyAnalyzer(ScriptedText(questions + textKey + scanned)).analyze(File("x"), ocr)
        assertEquals(listOf("Test 1", "Test 2", "Test 3", "Test 4"), outcome.result.tests.map { it.ref?.label })
    }

    @Test
    fun m11b_aScannedPageWithAPrintedHeaderIsStillReadWithOcr() = runBlocking {
        val questions = (0 until 4).map { BookFixtures.questionPage(it, if (it % 2 == 0) "TEST ${it / 2 + 1}" else null, 1) }
        // The publisher's text layer has only the running header; the key itself is a scan.
        val scannedWithHeader = BookFixtures.page(4, L("TYT Matematik Soru Bankası · 2024 · Cevaplar Bölümü", 0.02f))
        // OCR reads the scan: the key's heading and its answers.
        val key = keyLines(4, 1 to "ABCD", 2 to "DCBA")
        val ocr = ScriptedOcr(mapOf(4 to key.copy(lines = BookFixtures.page(4, L("CEVAP ANAHTARI", 0.08f)).lines + key.lines, source = TextSource.OCR)))
        val outcome = AnswerKeyAnalyzer(ScriptedText(questions + scannedWithHeader)).analyze(File("x"), ocr)
        assertEquals(listOf("Test 1", "Test 2"), outcome.result.tests.map { it.ref?.label })
        assertEquals(listOf(listOf("A", "B", "C", "D"), listOf("D", "C", "B", "A")), outcome.result.tests.map { t -> t.answers.map { it.answer } })
    }

    @Test
    fun m11c_aLongScannedKeyIsReadToItsEnd() = runBlocking {
        val questions = (0 until 40).map { BookFixtures.questionPage(it, if (it % 2 == 0) "TEST ${it / 2 + 1}" else null, 1) }
        // 20 tests' keys on 20 scanned pages at the end.
        val scans = (40 until 60).map { PageText(it, emptyList(), TextSource.EMBEDDED) }
        // The heading is on the first key page, as printed books have it.
        val ocr = ScriptedOcr(
            (40 until 60).associateWith { p ->
                val key = keyLines(p, (p - 39) to "ABCD")
                (if (p == 40) key.copy(lines = BookFixtures.page(p, L("CEVAP ANAHTARI", 0.05f)).lines + key.lines) else key).copy(source = TextSource.OCR)
            },
        )
        val outcome = AnswerKeyAnalyzer(ScriptedText(questions + scans)).analyze(File("x"), ocr)
        assertEquals(20, outcome.result.tests.size)
    }
}
