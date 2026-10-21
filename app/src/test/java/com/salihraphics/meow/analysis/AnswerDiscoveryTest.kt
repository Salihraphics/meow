package com.salihraphics.meow.analysis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.salihraphics.meow.analysis.FixturePdf.Companion.questionPage
import com.salihraphics.meow.analysis.FixturePdf.Text
import com.salihraphics.meow.analysis.key.AnswerNote
import com.salihraphics.meow.analysis.key.AnswerProvenance
import com.salihraphics.meow.analysis.key.BubbleSheetReader
import com.salihraphics.meow.analysis.key.Confidence
import com.salihraphics.meow.analysis.key.Darkness
import com.salihraphics.meow.analysis.key.DetectedTest
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.analysis.key.TestIssue
import com.salihraphics.meow.analysis.text.BitmapDarkness
import com.salihraphics.meow.analysis.text.PageImages
import com.salihraphics.meow.analysis.text.PdfBoxTextSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * One discovery pipeline, many places answers are printed. Each fixture is a real PDF (embedded
 * Turkish text, read by PdfBox) run through the whole analyzer. Page images for optical forms
 * come from a stand-in: Android's PdfRenderer doesn't run on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AnswerDiscoveryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val textSource by lazy { PdfBoxTextSource(RuntimeEnvironment.getApplication()) }
    private val analyzer by lazy { AnswerKeyAnalyzer(textSource) }

    private fun pdf(build: FixturePdf.() -> Unit): File = FixturePdf().apply(build).write(tmp.newFile("book${System.nanoTime()}.pdf"))

    private fun analyze(file: File, images: PageImages? = null) = runBlocking { analyzer.analyze(file, ocr = null, images = images) }

    private fun DetectedTest.letters() = answers.associate { it.question to it.answer }

    /** Four questions numbered "1."–"4." in two columns, each with [label] lines under it. */
    private fun MutableList<Text>.labelledQuestions(header: String?, first: Int, label: (Int) -> String?) {
        header?.let { add(Text(0.08f, 0.05f, it, 14f)) }
        listOf(0.06f to 0.12f, 0.06f to 0.52f, 0.53f to 0.12f, 0.53f to 0.52f).forEachIndexed { i, (x, y) ->
            val n = first + i
            add(Text(x, y, "$n.", 12f))
            add(Text(x + 0.04f, y, "Aşağıdakilerden hangisi doğrudur?"))
            add(Text(x + 0.04f, y + 0.08f, "A) 1    B) 2    C) 3    D) 4    E) 5"))
            label(n)?.let { add(Text(x + 0.04f, y + 0.14f, it)) }
        }
    }

    // ---- 1. End-of-book answer tables --------------------------------------------------------

    @Test
    fun endOfBookAnswerTable() {
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page { questionPage(null, 3, 2) }
                page {
                    add(Text(0.35f, 0.05f, "CEVAP ANAHTARI", 15f))
                    add(Text(0.08f, 0.12f, "TEST 1"))
                    listOf("1", "2", "3", "4").forEachIndexed { i, n -> add(Text(0.08f + i * 0.12f, 0.16f, n)) }
                    listOf("C", "A", "D", "B").forEachIndexed { i, l -> add(Text(0.08f + i * 0.12f, 0.20f, l)) }
                }
            },
        ).result
        val test = result.tests.single()
        assertEquals(mapOf(1 to "C", 2 to "A", 3 to "D", 4 to "B"), test.letters())
        assertEquals(KeySourceKind.KEY_SECTION, test.kind)
        assertEquals(listOf(0, 1), test.questionPages)
        assertTrue(test.answers.all { a -> a.provenance == listOf(AnswerProvenance(KeySourceKind.KEY_SECTION, 2, a.answer)) })
    }

    // ---- 2. "CEVAP: X" next to questions -----------------------------------------------------

    @Test
    fun cevapLabelsNextToQuestionsGoToTheQuestionAboveThemInTheirColumn() {
        val labels = mapOf(1 to "CEVAP: B", 2 to "Cevap: E", 3 to "Doğru cevap: A", 4 to "Yanıt: D")
        val result = analyze(pdf { page { labelledQuestions("TEST 1", 1) { labels[it] } } }).result
        val test = result.tests.single()
        assertEquals(KeySourceKind.LABELLED, test.kind)
        assertEquals(mapOf(1 to "B", 2 to "E", 3 to "A", 4 to "D"), test.letters())
        assertEquals("TEST 1", test.ref?.label)
        assertEquals(listOf(0), test.questionPages)
        assertEquals(4, result.clues.labelledAnswers)
    }

    @Test
    fun aLabelWithNoQuestionNumberAboveItIsNotUsed() {
        val result = analyze(
            pdf {
                page {
                    add(Text(0.08f, 0.07f, "CEVAP: C")) // above every question: whose answer?
                    labelledQuestions(null, 1) { if (it == 2) "CEVAP: A" else null }
                }
            },
        ).result
        assertEquals(mapOf(2 to "A"), result.tests.single().letters())
        assertEquals(1, result.clues.unplacedLabels)
    }

    @Test
    fun questionTextIsNeverReadAsAnAnswer() {
        val result = analyze(
            pdf {
                page {
                    add(Text(0.06f, 0.12f, "1."))
                    add(Text(0.10f, 0.12f, "Buna göre doğru cevap hangisidir?"))
                    add(Text(0.10f, 0.16f, "Cevaplar arasında en büyük değer A ile gösterilmiştir."))
                    add(Text(0.10f, 0.20f, "A) 1    B) 2    C) 3    D) 4    E) 5"))
                }
            },
        ).result
        assertFalse(result.found)
    }

    // ---- 3. Header and footer sequences ------------------------------------------------------

    @Test
    fun footerAnswersBelongToThePagesOwnQuestions() {
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1); add(Text(0.08f, 0.94f, "1.C   2.A")) }
                page { questionPage(null, 3, 2); add(Text(0.08f, 0.94f, "3.D   4.B")) }
            },
        ).result
        val test = result.tests.single()
        assertEquals(KeySourceKind.PAGE_MARGIN, test.kind)
        assertEquals(mapOf(1 to "C", 2 to "A", 3 to "D", 4 to "B"), test.letters())
        assertEquals(listOf(0, 1), test.questionPages)
    }

    @Test
    fun aFooterAnsweringAnotherSectionIsNotGivenToThisPage() {
        // Page 2 holds questions 5 and 6 of TEST 2; its footer answers questions 1–3 of something else.
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page { questionPage("TEST 2", 5, 2); add(Text(0.08f, 0.94f, "1.C   2.A   3.D")) }
            },
        ).result
        val test = result.tests.single()
        assertNull(test.ref)
        assertTrue(test.questionPages.isEmpty()) // the student assigns it; nothing is guessed
        assertEquals(1, result.clues.marginLinesOutOfContext)
    }

    // ---- 4. Optical forms --------------------------------------------------------------------

    /** A "YANITLAR" optical form: rows "n A B C D E" for [count] questions. */
    private fun MutableList<Text>.opticalForm(count: Int) {
        add(Text(0.40f, 0.05f, "YANITLAR", 15f))
        for (q in 1..count) {
            add(Text(0.10f, 0.10f + q * 0.04f, "$q"))
            ('A'..'E').forEachIndexed { i, l -> add(Text(0.18f + i * 0.06f, 0.10f + q * 0.04f, "$l")) }
        }
    }

    /** Page images where [marks] (question → letters) are filled bubbles, found from the page text. */
    private fun imagesWith(file: File, marks: Map<Int, List<String>>) = PageImages { pageIndex ->
        val rows = textSource.open(file).use { BubbleSheetReader.rows(it.page(pageIndex)) }
        val filled = rows.flatMap { row -> marks[row.question].orEmpty().mapNotNull { row.cells[it] } }
        Darkness { area -> if (filled.any { it.centerX in area.left..area.right && it.centerY in area.top..area.bottom }) 0.75f else 0.12f }
    }

    @Test
    fun aBlankOpticalFormLabelledYanitlarIsNotAKey() {
        val file = pdf { page { questionPage("TEST 1", 1, 1) }; page { opticalForm(10) } }
        assertFalse(analyze(file).result.found)
        val withImages = analyze(file, imagesWith(file, emptyMap()))
        assertFalse(withImages.result.found)
        assertTrue(withImages.report, withImages.report.contains("işaretli 0, boş 10, belirsiz 0"))
    }

    @Test
    fun aFilledOpticalFormIsReadFromTheImageAndMarkedForReview() {
        val file = pdf { page { questionPage("TEST 1", 1, 1) }; page { opticalForm(6) } }
        // Question 4 has two marks, question 6 none.
        val marks = mapOf(1 to listOf("C"), 2 to listOf("A"), 3 to listOf("E"), 4 to listOf("B", "D"), 5 to listOf("B"))
        val outcome = analyze(file, imagesWith(file, marks))
        val test = outcome.result.tests.single()
        assertEquals(mapOf(1 to "C", 2 to "A", 3 to "E", 5 to "B"), test.letters())
        assertTrue(test.answers.all { it.confidence == Confidence.LOW && it.note == AnswerNote.FROM_IMAGE })
        assertEquals(TestIssue.Missing(listOf(4)), test.issues.filterIsInstance<TestIssue.Missing>().single())
        assertTrue(outcome.report, outcome.report.contains("işaretli 4, boş 1, belirsiz 1"))
    }

    @Test
    fun inkIsMeasuredFromThePageImage() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        Canvas(bitmap).drawCircle(100f, 100f, 14f, Paint().apply { color = android.graphics.Color.BLACK })
        val darkness = BitmapDarkness(bitmap)
        val bubble = com.salihraphics.meow.analysis.TextBox(0.22f, 0.22f, 0.28f, 0.28f)
        val empty = com.salihraphics.meow.analysis.TextBox(0.62f, 0.22f, 0.68f, 0.28f)
        assertTrue(darkness.mean(bubble) > 0.6f)
        assertTrue(darkness.mean(empty) < 0.05f)
    }

    // ---- 5. Worked solutions -----------------------------------------------------------------

    @Test
    fun workedSolutionsGiveTheirExplicitAnswersNotTheirStepNumbers() {
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page { questionPage(null, 3, 2) }
                page {
                    add(Text(0.08f, 0.05f, "TEST 1 ÇÖZÜMLERİ", 14f))
                    var y = 0.12f
                    for ((q, answer) in listOf(1 to "Cevap: C", 2 to "Doğru cevap D seçeneğidir.", 3 to "Yanıt: A")) {
                        add(Text(0.06f, y, "$q.")); add(Text(0.10f, y, "Verilen ifadede x yalnız bırakılır.")); y += 0.04f
                        // Numbered steps, indented: not question numbers.
                        add(Text(0.10f, y, "1. adımda her iki taraf 2 ile çarpılır.")); y += 0.04f
                        add(Text(0.10f, y, "2. adımda sadeleştirilir.")); y += 0.04f
                        add(Text(0.10f, y, answer)); y += 0.07f
                    }
                }
            },
        ).result
        val test = result.tests.single()
        assertEquals(KeySourceKind.SOLUTION, test.kind)
        assertEquals(mapOf(1 to "C", 2 to "D", 3 to "A"), test.letters())
        assertEquals("TEST 1", test.ref?.label)
        assertEquals(listOf(2), result.clues.solutionPages)
        // Solutions pages are not where TEST 1's questions are.
        assertFalse(2 in test.questionPages)
    }

    // ---- 6. Several sources, conflicts and gaps ----------------------------------------------

    @Test
    fun agreeingSourcesCombineAndDisagreeingOnesAreAConflictWithProvenance() {
        val result = analyze(
            pdf {
                page { labelledQuestions("TEST 1", 1) { if (it == 2) "CEVAP: E" else if (it == 3) "CEVAP: D" else null } }
                page {
                    add(Text(0.35f, 0.05f, "CEVAP ANAHTARI", 15f))
                    add(Text(0.08f, 0.12f, "Test 1   1.C   2.A   3.D   4.B"))
                }
            },
        ).result
        val test = result.tests.single()
        assertEquals(KeySourceKind.KEY_SECTION, test.kind)
        val q2 = test.answers.single { it.question == 2 }
        assertEquals(AnswerNote.CONFLICT, q2.note)
        assertEquals(listOf("A", "E"), q2.alternatives.sorted())
        assertEquals(
            setOf(AnswerProvenance(KeySourceKind.KEY_SECTION, 1, "A"), AnswerProvenance(KeySourceKind.LABELLED, 0, "E")),
            q2.provenance.toSet(),
        )
        val q3 = test.answers.single { it.question == 3 }
        assertNull(q3.note)
        assertEquals(Confidence.HIGH, q3.confidence)
        assertEquals(2, q3.provenance.size) // read twice, the same
        assertEquals(TestIssue.Conflict(2, q2.alternatives), test.issues.filterIsInstance<TestIssue.Conflict>().single())
    }

    @Test
    fun missingQuestionNumbersAreReportedNeverFilled() {
        val result = analyze(
            pdf {
                page { questionPage("TEST 1", 1, 1) }
                page {
                    add(Text(0.35f, 0.05f, "CEVAP ANAHTARI", 15f))
                    add(Text(0.08f, 0.12f, "Test 1   1.C   2.A   4.B   5.E   7.D"))
                }
            },
        ).result
        val test = result.tests.single()
        assertEquals(listOf(1, 2, 4, 5, 7), test.answers.map { it.question })
        assertEquals(TestIssue.Missing(listOf(3, 6)), test.issues.filterIsInstance<TestIssue.Missing>().single())
    }
}
