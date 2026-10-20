package com.salihraphics.meow.analysis.layout

import com.salihraphics.meow.analysis.FixturePdf
import com.salihraphics.meow.analysis.FixturePdf.Companion.questionPage
import com.salihraphics.meow.analysis.FixturePdf.Text
import com.salihraphics.meow.analysis.text.PdfBoxTextSource
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
import kotlin.math.cos
import kotlin.math.sin

/** Question regions and option labels read from real PDF text, and what pen marks mean on them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionLayoutTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun layouts(build: FixturePdf.() -> Unit): List<PageLayout> {
        val file = FixturePdf().apply(build).write(tmp.newFile("book.pdf"))
        return PdfBoxTextSource(RuntimeEnvironment.getApplication()).open(file).use { doc ->
            List(doc.pageCount) { QuestionLayoutDetector.detect(doc.page(it)) }
        }
    }

    private fun PageLayout.option(question: Int, letter: String) =
        questions.single { it.number == question }.options.single { it.letter == letter }

    @Test
    fun findsQuestionsAndTheirOptionsOnATurkishPage() {
        val page = layouts { page { questionPage("TEST 1", first = 1, pageNumber = 1) } }.single()
        assertEquals(listOf(1, 2), page.questions.map { it.number })
        page.questions.forEach { q -> assertEquals(listOf("A", "B", "C", "D", "E"), q.options.map { it.letter }) }
        // Labels sit where they were printed: on the options line, left to right.
        val q1 = page.questions.first()
        assertTrue(q1.options.all { it.box.centerY in 0.17f..0.21f })
        assertEquals(q1.options.sortedBy { it.box.left }, q1.options)
        assertEquals(0.08f, q1.options.first().box.left, 0.01f)
        // The second question's region starts at its own number.
        assertEquals(2, page.questionAt(0.3f, 0.5f)?.number)
        assertEquals(1, page.questionAt(0.3f, 0.3f)?.number)
    }

    @Test
    fun twoColumnPagesKeepEachColumnsQuestionsApart() {
        val page = layouts {
            page {
                add(Text(0.06f, 0.10f, "1. Birinci soru"))
                add(Text(0.06f, 0.15f, "A) 1   B) 2   C) 3   D) 4"))
                add(Text(0.55f, 0.10f, "3. Üçüncü soru"))
                add(Text(0.55f, 0.15f, "A) x   B) y   C) z   D) w"))
                add(Text(0.06f, 0.50f, "2. İkinci soru"))
                add(Text(0.06f, 0.55f, "A) 5   B) 6   C) 7   D) 8"))
            }
        }.single()
        assertEquals(setOf(1, 2, 3), page.questions.map { it.number }.toSet())
        assertEquals(3, page.questionAt(0.7f, 0.15f)?.number)
        assertEquals(1, page.questionAt(0.2f, 0.15f)?.number)
        assertEquals(2, page.questionAt(0.2f, 0.55f)?.number)
        // Four-option tests are fine: no E is invented.
        page.questions.forEach { assertEquals(listOf("A", "B", "C", "D"), it.options.map { o -> o.letter }) }
        // Right column's options never leak into the left column's question.
        assertTrue(page.option(1, "A").box.left < 0.5f && page.option(3, "A").box.left > 0.5f)
    }

    @Test
    fun aPageWithoutNumberedQuestionsHasNoRegions() {
        val page = layouts { page { add(Text(0.1f, 0.1f, "Önsöz: Bu kitap TYT için hazırlandı.")) } }.single()
        assertTrue(page.questions.isEmpty())
        assertNull(page.questionAt(0.5f, 0.5f))
    }

    // --- What a pen mark means ---------------------------------------------------------------

    private val page by lazy { layouts { page { questionPage("TEST 1", first = 1, pageNumber = 1) } }.single() }

    @Test
    fun aCircleAroundOneLabelSelectsIt() {
        val c = page.option(1, "C").box
        val result = ChoiceRecognizer.recognize(circle(c.centerX, c.centerY, 0.025f, 0.012f), page)
        assertEquals(ChoiceRecognition.Selected(1, "C", listOf("A", "B", "C", "D", "E")), result)
        val e = page.option(2, "E").box
        assertEquals("E", (ChoiceRecognizer.recognize(circle(e.centerX, e.centerY, 0.02f, 0.012f), page) as ChoiceRecognition.Selected).letter)
    }

    @Test
    fun aCircleAroundTwoLabelsAsks() {
        val b = page.option(1, "B").box
        val c = page.option(1, "C").box
        val result = ChoiceRecognizer.recognize(circle((b.centerX + c.centerX) / 2, b.centerY, (c.centerX - b.centerX) / 2 + 0.02f, 0.012f), page)
        assertEquals(ChoiceRecognition.Unclear(1, listOf("B", "C"), listOf("A", "B", "C", "D", "E")), result)
    }

    @Test
    fun aCircleAroundTheValueNextToALabelAsksWithThatLabel() {
        val d = page.option(1, "D").box
        // Around "21", just right of "D)".
        val result = ChoiceRecognizer.recognize(circle(d.right + 0.025f, d.centerY, 0.018f, 0.012f), page)
        assertEquals(ChoiceRecognition.Unclear(1, listOf("D"), listOf("A", "B", "C", "D", "E")), result)
    }

    @Test
    fun anOpenTickOnALabelAsks() {
        val a = page.option(2, "A").box
        val tick = floatArrayOf(a.left, a.centerY, a.centerX, a.bottom, a.right + 0.02f, a.top - 0.01f)
        assertEquals(listOf("A"), (ChoiceRecognizer.recognize(tick, page) as ChoiceRecognition.Unclear).candidates)
    }

    @Test
    fun notesAndDrawingsAreNotAnswers() {
        // Working out in the empty space under question 1.
        assertEquals(ChoiceRecognition.NotAnAnswer, ChoiceRecognizer.recognize(circle(0.6f, 0.32f, 0.05f, 0.03f), page))
        // A big loop over half the page.
        assertEquals(ChoiceRecognition.NotAnAnswer, ChoiceRecognizer.recognize(circle(0.5f, 0.4f, 0.3f, 0.3f), page))
        // A scribble outside any question (above the first one).
        assertEquals(ChoiceRecognition.NotAnAnswer, ChoiceRecognizer.recognize(floatArrayOf(0.2f, 0.02f, 0.3f, 0.03f, 0.25f, 0.04f), page))
        // A page with no detected questions never produces answers.
        assertEquals(ChoiceRecognition.NotAnAnswer, ChoiceRecognizer.recognize(circle(0.5f, 0.5f, 0.02f, 0.02f), PageLayout(0, emptyList())))
    }

    @Test
    fun theResultDependsOnlyOnPageFractionsNotOnScreenSize() {
        // The same circle drawn on screens of different sizes and zoom levels: the stored points
        // are screen positions divided by the page's on-screen size, so they are the same.
        val c = page.option(1, "C").box
        val fractions = circle(c.centerX, c.centerY, 0.025f, 0.012f)
        val results = listOf(800f to 1131f, 1600f to 2262f, 2400f to 3393f).map { (w, h) ->
            val screen = FloatArray(fractions.size) { i -> fractions[i] * if (i % 2 == 0) w else h }
            val back = FloatArray(screen.size) { i -> screen[i] / if (i % 2 == 0) w else h }
            ChoiceRecognizer.recognize(back, page)
        }
        assertTrue(results.all { it == ChoiceRecognition.Selected(1, "C", listOf("A", "B", "C", "D", "E")) })
    }

    companion object {
        /** A hand-drawn-ish closed loop: starts at the right, goes round once, ends near the start. */
        fun circle(cx: Float, cy: Float, rx: Float, ry: Float, steps: Int = 28): FloatArray {
            val out = FloatArray(steps * 2)
            for (i in 0 until steps) {
                val t = 2 * Math.PI * i / steps
                out[2 * i] = cx + rx * cos(t).toFloat()
                out[2 * i + 1] = cy + ry * sin(t).toFloat()
            }
            return out
        }
    }
}
