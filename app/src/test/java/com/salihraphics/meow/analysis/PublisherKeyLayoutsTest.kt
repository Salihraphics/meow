package com.salihraphics.meow.analysis

import com.salihraphics.meow.analysis.FixturePdf.Companion.questionPage
import com.salihraphics.meow.analysis.FixturePdf.Text
import com.salihraphics.meow.analysis.text.PdfBoxTextSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Answer-key layouts publishers print, written as real PDFs (embedded text) and read through
 * PdfBox and the full analyzer. Each is a layout the earlier parser missed: table cells spaced
 * wider than a word gap, answers without spaces, tests as table columns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublisherKeyLayoutsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val analyzer by lazy { AnswerKeyAnalyzer(PdfBoxTextSource(RuntimeEnvironment.getApplication())) }

    private fun analyze(build: FixturePdf.() -> Unit) = runBlocking {
        analyzer.analyze(FixturePdf().apply(build).write(tmp.newFile("book${System.nanoTime()}.pdf")), ocr = null)
    }

    private fun FixturePdf.questions(count: Int) = repeat(count) { i -> page { questionPage(if (i % 2 == 0) "TEST ${i / 2 + 1}" else null, 1 + (i % 2) * 2, i + 1) } }

    private fun letters(outcome: AnalysisOutcome) = outcome.result.tests.map { t -> t.answers.joinToString("") { it.answer } }

    /** One table cell per word, [step] apart: wider than the gap that splits columns. */
    private fun MutableList<Text>.cells(y: Float, x: Float, step: Float, values: List<String>) =
        values.forEachIndexed { i, v -> add(Text(x + i * step, y, v)) }

    @Test
    fun aTableWithWidelySpacedCells() {
        val outcome = analyze {
            questions(4)
            page {
                add(Text(0.35f, 0.05f, "CEVAP ANAHTARI", 15f))
                add(Text(0.08f, 0.12f, "TEST 1"))
                cells(0.16f, 0.08f, 0.11f, (1..8).map { "$it" })
                cells(0.20f, 0.08f, 0.11f, "CADBECAD".map { "$it" })
                add(Text(0.08f, 0.28f, "TEST 2"))
                cells(0.32f, 0.08f, 0.11f, (1..8).map { "$it" })
                cells(0.36f, 0.08f, 0.11f, "EBACDDBA".map { "$it" })
            }
        }
        assertEquals(listOf("CADBECAD", "EBACDDBA"), letters(outcome))
        assertEquals(listOf(0, 1), outcome.result.tests.first().questionPages)
    }

    @Test
    fun answerPairsSpreadAcrossThePage() {
        val outcome = analyze {
            questions(2)
            page {
                add(Text(0.35f, 0.05f, "CEVAPLAR", 15f))
                add(Text(0.08f, 0.12f, "TEST 1"))
                cells(0.16f, 0.08f, 0.17f, listOf("1. C", "2. A", "3. D", "4. B", "5. E"))
                cells(0.20f, 0.08f, 0.17f, listOf("6. C", "7. A", "8. D", "9. B", "10. E"))
            }
        }
        assertEquals(listOf("CADBECADBE"), letters(outcome))
    }

    @Test
    fun answersPrintedWithoutSpaces() {
        val outcome = analyze {
            questions(2)
            page {
                add(Text(0.35f, 0.05f, "Cevap Anahtarı", 15f))
                add(Text(0.08f, 0.12f, "Test 1  1.C2.A3.D4.B5.E6.C"))
                add(Text(0.08f, 0.16f, "Test 2  1C2A3D4B5E6C"))
            }
        }
        assertEquals(listOf("CADBEC", "CADBEC"), letters(outcome))
    }

    @Test
    fun testsAsTableColumns() {
        // Question numbers down the left, one column per test.
        val outcome = analyze {
            questions(6)
            page {
                add(Text(0.35f, 0.05f, "CEVAP ANAHTARI", 15f))
                cells(0.12f, 0.20f, 0.15f, listOf("TEST 1", "TEST 2", "TEST 3"))
                val rows = listOf("CAE", "ABD", "DDA", "BEC")
                rows.forEachIndexed { q, row ->
                    add(Text(0.08f, 0.16f + q * 0.04f, "${q + 1}."))
                    cells(0.16f + q * 0.04f, 0.22f, 0.15f, row.map { "$it" })
                }
            }
        }
        assertEquals(listOf("CADB", "ABDE", "EDAC"), letters(outcome))
        assertEquals(listOf("TEST 1", "TEST 2", "TEST 3"), outcome.result.tests.map { it.ref?.label })
        assertEquals(listOf(0, 1), outcome.result.tests.first().questionPages)
    }

    @Test
    fun dayKeysWithTablesUnderEachTest() {
        val outcome = analyze {
            repeat(4) { i -> page { questionPage(null, 1, i + 1); add(Text(0.08f, 0.03f, "${i / 2 + 1}. GÜN")); add(Text(0.6f, 0.03f, if (i % 2 == 0) "TEST 1" else "TEST 2")) } }
            page {
                add(Text(0.35f, 0.04f, "CEVAP ANAHTARI", 15f))
                var y = 0.10f
                for (day in 1..2) {
                    add(Text(0.08f, y, "$day. GÜN")); y += 0.04f
                    for (test in 1..2) {
                        add(Text(0.08f, y, "TEST $test")); y += 0.03f
                        cells(y, 0.08f, 0.09f, (1..4).map { "$it" }); y += 0.03f
                        cells(y, 0.08f, 0.09f, (if (day == test) "ABCD" else "DCBA").map { "$it" }); y += 0.05f
                    }
                }
            }
        }
        assertEquals(listOf("1. GÜN · TEST 1", "1. GÜN · TEST 2", "2. GÜN · TEST 1", "2. GÜN · TEST 2"), outcome.result.tests.map { it.ref?.label })
        assertEquals(listOf("ABCD", "DCBA", "DCBA", "ABCD"), letters(outcome))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3)), outcome.result.tests.map { it.questionPages })
    }
}
