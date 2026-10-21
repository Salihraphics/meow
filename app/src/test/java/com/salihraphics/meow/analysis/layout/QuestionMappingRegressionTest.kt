package com.salihraphics.meow.analysis.layout

import com.salihraphics.meow.analysis.FixturePdf
import com.salihraphics.meow.analysis.FixturePdf.Text
import com.salihraphics.meow.analysis.layout.QuestionLayoutTest.Companion.circle
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

/**
 * Device report: circling an option of question 4 graded question 1. The page had a "1. GÜN"
 * header, and the question numbers weren't "4." text, so the header's "1." was taken for the
 * only question and its region covered the whole page. These pages reproduce that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionMappingRegressionTest {
    @get:Rule val tmp = TemporaryFolder()

    private val all = listOf("A", "B", "C", "D", "E")

    private fun layoutOf(rotation: Int = 0, build: MutableList<Text>.() -> Unit): PageLayout {
        val file = FixturePdf().apply { page(rotation, upright = rotation != 0, block = build) }.write(tmp.newFile())
        return PdfBoxTextSource(RuntimeEnvironment.getApplication()).open(file).use { QuestionLayoutDetector.detect(it.page(0)) }
    }

    /** Question [n] at ([x], [y]): a number ("4" boxed, or "4."), text beside it, options below. */
    private fun MutableList<Text>.question(n: Int, x: Float, y: Float, label: String = "$n", stacked: Boolean = false) {
        add(Text(x, y, label, 13f))
        add(Text(x + 0.045f, y, "Aşağıdakilerden hangisi doğrudur?"))
        add(Text(x + 0.045f, y + 0.03f, "x + y = ${n * 3} ise x kaçtır?"))
        if (stacked) {
            all.forEachIndexed { i, l -> add(Text(x + 0.045f, y + 0.08f + i * 0.025f, "$l) ${n * 10 + i}")) }
        } else {
            add(Text(x + 0.045f, y + 0.08f, all.mapIndexed { i, l -> "$l) ${n * 10 + i}" }.joinToString("    ")))
        }
    }

    /** The reported page: day header, four boxed numbers in two columns, page number. */
    private val dayPage: MutableList<Text>.() -> Unit = {
        add(Text(0.08f, 0.035f, "1. GÜN", 14f))
        add(Text(0.70f, 0.035f, "TYT Matematik", 10f))
        question(1, 0.06f, 0.12f)
        question(2, 0.06f, 0.52f)
        question(3, 0.53f, 0.12f)
        question(4, 0.53f, 0.52f)
        add(Text(0.49f, 0.96f, "7", 9f))
    }

    private fun PageLayout.option(question: Int, letter: String) =
        questions.single { it.number == question }.options.single { it.letter == letter }.box

    private fun PageLayout.circleAround(question: Int, letter: String): ChoiceRecognition {
        val box = option(question, letter)
        return ChoiceRecognizer.recognize(circle(box.centerX, box.centerY, 0.02f, 0.012f), this)
    }

    @Test
    fun theDayHeaderIsNotAQuestionAndEveryQuestionHasItsOwnOptions() {
        val layout = layoutOf(build = dayPage)
        assertEquals(listOf(1, 2, 3, 4), layout.numbers.sorted())
        layout.questions.forEach { assertEquals(all, it.options.map { o -> o.letter }) }
    }

    @Test
    fun eachOfFourQuestionsOnOnePageIsMappedToItself() {
        val layout = layoutOf(build = dayPage)
        val marks = listOf(1 to "A", 2 to "D", 3 to "E", 4 to "C")
        for ((q, letter) in marks) assertEquals(ChoiceRecognition.Selected(q, letter, all), layout.circleAround(q, letter))
    }

    @Test
    fun severalCirclesOnOnePageEachKeepTheirQuestion() {
        val layout = layoutOf(build = dayPage)
        // The student works through the page in any order.
        val results = listOf(4 to "B", 1 to "E", 3 to "A", 2 to "C", 4 to "D").map { (q, l) -> layout.circleAround(q, l) }
        assertEquals(
            listOf(
                ChoiceRecognition.Selected(4, "B", all), ChoiceRecognition.Selected(1, "E", all), ChoiceRecognition.Selected(3, "A", all),
                ChoiceRecognition.Selected(2, "C", all), ChoiceRecognition.Selected(4, "D", all),
            ),
            results,
        )
    }

    @Test
    fun numbersWithFullStopsAndAHeaderAlsoMapCorrectly() {
        val layout = layoutOf {
            add(Text(0.08f, 0.035f, "2. ÜNİTE  Sayılar", 14f))
            question(5, 0.06f, 0.12f, label = "5.")
            question(6, 0.06f, 0.52f, label = "6.")
            question(7, 0.53f, 0.12f, label = "7.")
            question(8, 0.53f, 0.52f, label = "8.")
        }
        assertEquals(listOf(5, 6, 7, 8), layout.numbers.sorted())
        assertEquals(ChoiceRecognition.Selected(8, "A", all), layout.circleAround(8, "A"))
        assertEquals(ChoiceRecognition.Selected(6, "E", all), layout.circleAround(6, "E"))
    }

    @Test
    fun optionsStackedInAColumnBelongToTheirQuestion() {
        val layout = layoutOf {
            question(11, 0.06f, 0.10f, label = "11.", stacked = true)
            question(12, 0.06f, 0.55f, label = "12.", stacked = true)
            question(13, 0.53f, 0.10f, label = "13.", stacked = true)
        }
        assertEquals(listOf(11, 12, 13), layout.numbers.sorted())
        assertEquals(ChoiceRecognition.Selected(12, "D", all), layout.circleAround(12, "D"))
        assertEquals(ChoiceRecognition.Selected(13, "B", all), layout.circleAround(13, "B"))
    }

    @Test
    fun whenNumbersCantBeReadTheQuestionIsAskedNotGuessed() {
        // Numbers drawn as graphics: no text at all for them, only the header's "1.".
        val layout = layoutOf {
            add(Text(0.08f, 0.035f, "1. GÜN", 14f))
            listOf(0.06f to 0.12f, 0.06f to 0.52f, 0.53f to 0.12f, 0.53f to 0.52f).forEachIndexed { i, (x, y) ->
                add(Text(x + 0.045f, y, "Aşağıdakilerden hangisi doğrudur?"))
                add(Text(x + 0.045f, y + 0.08f, all.mapIndexed { k, l -> "$l) ${i * 10 + k}" }.joinToString("    ")))
            }
        }
        assertEquals(4, layout.questions.size)
        assertTrue(layout.questions.all { it.number == null })
        val c = layout.questions.first { it.box.left > 0.5f && it.box.top > 0.5f }.options.single { it.letter == "C" }.box
        val result = ChoiceRecognizer.recognize(circle(c.centerX, c.centerY, 0.02f, 0.012f), layout)
        assertEquals(ChoiceRecognition.Unclear(null, listOf("C"), all), result)
    }

    @Test
    fun aMissingNumberLeavesThatQuestionUnknownInsteadOfBorrowingTheOneAbove() {
        val layout = layoutOf {
            question(1, 0.06f, 0.12f, label = "1.")
            // Question 2's number is missing (an image); its options sit below question 1's.
            add(Text(0.105f, 0.52f, "Buna göre hangisi yanlıştır?"))
            add(Text(0.105f, 0.60f, all.mapIndexed { k, l -> "$l) ${k + 50}" }.joinToString("    ")))
        }
        val unknown = layout.questions.single { it.number == null }
        assertTrue(unknown.options.first().box.top > 0.55f)
        val d = unknown.options.single { it.letter == "D" }.box
        assertNull((ChoiceRecognizer.recognize(circle(d.centerX, d.centerY, 0.02f, 0.012f), layout) as ChoiceRecognition.Unclear).question)
        assertEquals(ChoiceRecognition.Selected(1, "B", all), layout.circleAround(1, "B"))
    }

    @Test
    fun aLineOfTextStartingWithANumberIsNotAQuestionNumber() {
        val layout = layoutOf {
            question(3, 0.06f, 0.12f, label = "3.")
            // Inside question 3: "12 kg elma…" starts a line, right above the options.
            add(Text(0.105f, 0.18f, "12 kg elma ve 5 kg armut alınıyor."))
            question(4, 0.06f, 0.52f, label = "4.")
        }
        assertEquals(listOf(3, 4), layout.numbers)
        assertEquals(ChoiceRecognition.Selected(3, "A", all), layout.circleAround(3, "A"))
    }

    @Test
    fun aCircleTakingInTwoQuestionsOptionsAsksWhichQuestion() {
        val layout = layoutOf {
            question(1, 0.06f, 0.10f, label = "1.", stacked = true)
            question(2, 0.06f, 0.30f, label = "2.", stacked = true)
        }
        // A loop from question 1's E down over question 2's A.
        val e1 = layout.option(1, "E")
        val a2 = layout.option(2, "A")
        val loop = circle(e1.centerX, (e1.centerY + a2.centerY) / 2, 0.02f, (a2.centerY - e1.centerY) / 2 + 0.01f)
        val result = ChoiceRecognizer.recognize(loop, layout) as ChoiceRecognition.Unclear
        assertNull(result.question)
        assertEquals(listOf(1, 2), result.nearbyQuestions)
    }

    @Test
    fun aRotatedPageMapsTheSameWay() {
        // Pages stored with /Rotate: positions must be where things appear on screen (that is
        // where the pen draws), so question 4's options are bottom right whatever the rotation.
        for (rotation in listOf(0, 90, 180, 270)) {
            val layout = layoutOf(rotation, dayPage)
            assertEquals("rotation $rotation", listOf(1, 2, 3, 4), layout.numbers.sorted())
            val a4 = layout.option(4, "A")
            assertEquals("rotation $rotation", 0.575f, a4.left, 0.01f)
            assertEquals("rotation $rotation", 0.59f, a4.centerY, 0.015f)
            assertEquals("rotation $rotation", ChoiceRecognition.Selected(4, "C", all), layout.circleAround(4, "C"))
            assertEquals("rotation $rotation", ChoiceRecognition.Selected(1, "A", all), layout.circleAround(1, "A"))
        }
    }

    @Test
    fun aPrintPdfWithBleedMeasuresFromTheCropBoxLikeTheViewer() {
        // 9 mm of bleed around the trimmed page; Android's renderer shows only the crop box.
        val file = FixturePdf().apply { bleedPage(bleed = 25.5f, block = dayPage) }.write(tmp.newFile())
        val layout = PdfBoxTextSource(RuntimeEnvironment.getApplication()).open(file).use { QuestionLayoutDetector.detect(it.page(0)) }
        val a4 = layout.option(4, "A")
        assertEquals(0.575f, a4.left, 0.01f)
        assertEquals(0.59f, a4.centerY, 0.015f)
        assertEquals(ChoiceRecognition.Selected(4, "C", all), layout.circleAround(4, "C"))
    }

    @Test
    fun zoomDoesNotChangeTheMapping() {
        val layout = layoutOf(build = dayPage)
        val c = layout.option(4, "C")
        // Screen sizes of the page at 100%, 200% and 400% zoom; points stored as page fractions.
        val results = listOf(1f, 2f, 4f).map { zoom ->
            val w = 1080f * zoom
            val h = w * 842f / 595f
            val screen = circle(c.centerX, c.centerY, 0.02f, 0.012f).mapIndexed { i, v -> v * if (i % 2 == 0) w else h }
            val fractions = screen.mapIndexed { i, v -> v / if (i % 2 == 0) w else h }.toFloatArray()
            ChoiceRecognizer.recognize(fractions, layout)
        }
        assertTrue(results.all { it == ChoiceRecognition.Selected(4, "C", all) })
    }

    @Test
    fun aColumnWithoutReadableNumbersNeverBorrowsTheOtherColumnsNumbers() {
        // Left column: "1." and "2." are text. Right column: numbers are pictures.
        val layout = layoutOf {
            question(1, 0.06f, 0.12f, label = "1.")
            question(2, 0.06f, 0.52f, label = "2.")
            listOf(0.12f, 0.52f).forEachIndexed { i, y ->
                add(Text(0.575f, y, "Aşağıdakilerden hangisi doğrudur?"))
                add(Text(0.575f, y + 0.08f, all.mapIndexed { k, l -> "$l) ${70 + i * 10 + k}" }.joinToString("    ")))
            }
        }
        val right = layout.questions.filter { it.box.left > 0.5f }
        assertEquals(2, right.size)
        assertTrue(right.all { it.number == null })
        val c = right.first { it.box.top > 0.5f }.options.single { it.letter == "C" }.box
        val result = ChoiceRecognizer.recognize(circle(c.centerX, c.centerY, 0.02f, 0.012f), layout) as ChoiceRecognition.Unclear
        assertNull(result.question)
        assertEquals(listOf(1, 2), layout.numbers)
        assertEquals(ChoiceRecognition.Selected(2, "C", all), layout.circleAround(2, "C"))
    }

    @Test
    fun aSecondOptionSetUnderANumberDoesNotTakeThatNumber() {
        // Question 5's own options keep 5; another option set further down (its number missing)
        // is unknown, so circling there is asked, not graded as question 5.
        val layout = layoutOf {
            question(5, 0.06f, 0.12f, label = "5.")
            add(Text(0.105f, 0.40f, all.mapIndexed { k, l -> "$l) ${k + 90}" }.joinToString("    ")))
        }
        assertEquals(listOf(5, null), layout.questions.sortedBy { it.box.top }.map { it.number })
    }
}
