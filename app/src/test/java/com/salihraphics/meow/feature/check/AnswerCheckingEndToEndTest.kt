package com.salihraphics.meow.feature.check

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import com.salihraphics.meow.analysis.FixturePdf
import com.salihraphics.meow.analysis.FixturePdf.Companion.dayPage
import com.salihraphics.meow.analysis.layout.PageLayout
import com.salihraphics.meow.analysis.layout.PdfPageLayoutSource
import com.salihraphics.meow.analysis.layout.QuestionLayoutDetector
import com.salihraphics.meow.analysis.layout.QuestionLayoutTest.Companion.circle
import com.salihraphics.meow.analysis.text.PdfBoxTextSource
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWindow
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.data.answerkey.AnswerKeyRepository
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.SelectionOrigin
import com.salihraphics.meow.feature.viewer.PdfViewerRoute
import com.salihraphics.meow.feature.viewer.PdfViewerViewModel
import com.salihraphics.meow.feature.viewer.ViewerUiState
import com.salihraphics.meow.testing.FakeAnnotationRepository
import com.salihraphics.meow.testing.FakePdfLibraryRepository
import com.salihraphics.meow.testing.FakeSessionFactory
import com.salihraphics.meow.testing.sampleDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A student solves on the real viewer: circles options with the pen, then checks the test.
 * Question positions come from the real text of a generated Turkish PDF (PdfBox); the key and
 * answers live in a real (in-memory) database. The page images are blank: Android's
 * PdfRenderer does not run on the JVM, so this is not a device test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1400dp-h1400dp-mdpi")
class AnswerCheckingEndToEndTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowDatabase::class.java).allowMainThreadQueries().build()
    private val keys = AnswerKeyRepository(db)
    private val textSource = PdfBoxTextSource(RuntimeEnvironment.getApplication())
    private lateinit var book: java.io.File
    private lateinit var layout: PageLayout
    private var openedKey = false
    private val immediate = MutableStateFlow(false)
    private val annotations = FakeAnnotationRepository()

    private val store = ViewModelStore()

    @After fun tearDown() {
        // As when the screen closes: the ViewModel stops before the database goes away.
        store.clear()
        rule.waitForIdle()
        db.close()
    }

    /** The reported page; the key is 1-A, 2-D, 3-E, 4-C. */
    private fun setUpBook(confirm: Boolean = true, numbered: Boolean = true, withKey: Boolean = true, pages: IntRange? = 0..0): String? {
        book = FixturePdf().apply { page { dayPage(numbered) } }.write(tmp.newFile("book.pdf"))
        layout = textSource.open(book).use { QuestionLayoutDetector.detect(it.page(0)) }
        if (!withKey) return null
        return runBlocking {
            val test = keys.addManualTest("doc-1", "Test 1", 1)
            listOf(1 to "A", 2 to "D", 3 to "E", 4 to "C").forEach { (q, a) -> keys.setAnswer(test, q, AnswerType.CHOICE, a) }
            keys.updateTest(test, "Test 1", pages)
            if (confirm) keys.confirm("doc-1")
            test
        }
    }

    private fun viewModel(zoom: Float = 1f): PdfViewerViewModel {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument(pages = 1)) }
        val factory = viewModelFactory {
            initializer {
                PdfViewerViewModel(
                    documentId = "doc-1",
                    savedState = SavedStateHandle(mapOf("viewer.viewport" to floatArrayOf(0f, 0f, zoom, 0f))),
                    repository = repo,
                    sessions = FakeSessionFactory(pages = 1),
                    annotationRepository = annotations,
                    applicationScope = CoroutineScope(Dispatchers.Unconfined),
                    answerKeys = keys,
                    layoutSourceFactory = { _, _ -> PdfPageLayoutSource(book, textSource, ocr = null) },
                    immediateFeedbackSetting = immediate,
                )
            }
        }
        return ViewModelProvider.create(store, factory)[PdfViewerViewModel::class]
    }

    private fun show(zoom: Float = 1f): PdfViewerViewModel {
        val vm = viewModel(zoom)
        rule.setContent {
            MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) {
                // Tall enough that the whole page sits above the floating bars (a real student scrolls).
                CompositionLocalProvider(LocalMeowWindow provides MeowWindow.fromDp(411, 1300)) {
                    Box(Modifier.requiredSize(411.dp, 1300.dp)) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            PdfViewerRoute(vm, onBack = {}, onOpenAnswerKey = { openedKey = true })
                        }
                    }
                }
            }
        }
        until { vm.state.value is ViewerUiState.Ready }
        rule.onNode(hasContentDescription("Kalem")).performClick()
        rule.waitForIdle()
        return vm
    }

    /** Draws a loop around ([cx], [cy]) on page 1, positions in page fractions. */
    private fun circleOnPage(cx: Float, cy: Float, rx: Float = 0.02f, ry: Float = 0.012f) {
        val points = circle(cx, cy, rx, ry)
        rule.onNode(hasContentDescription("Sayfa 1 / 1")).performTouchInput {
            fun at(i: Int) = Offset(width * points[2 * i], height * points[2 * i + 1])
            down(at(0))
            for (i in 1 until points.size / 2) moveTo(at(i))
            moveTo(at(0))
            up()
        }
        rule.waitForIdle()
    }

    private fun circle(question: Int, letter: String) {
        val box = option(question, letter)
        circleOnPage(box.centerX, box.centerY)
    }

    private fun option(question: Int, letter: String) =
        layout.questions.single { it.number == question }.options.single { it.letter == letter }.box

    /**
     * Waits for [condition]. The ViewModel's database and PDF work finishes on other threads and
     * continues on the main looper, which only runs when the test idles it.
     */
    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            rule.waitForIdle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "Condition still not satisfied after 5000 ms" }
            Thread.sleep(20)
        }
    }

    private fun waitForText(text: String) =
        until { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun shown(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun attempts() = runBlocking { keys.observeAttempts("doc-1").first() }

    private fun answered() = runBlocking { keys.openSession("doc-1") }?.latest.orEmpty()

    @Test
    fun circlingRecordsTheRightQuestionWithoutRevealingTheResult() {
        setUpBook()
        show()
        circle(4, "C")
        waitForText("Soru 4: C işaretlendi")
        assertFalse(shown("Doğru") || shown("Yanlış"))
        until { attempts().isNotEmpty() }
        val attempt = attempts().single()
        assertEquals(4, attempt.question)
        assertEquals("C", attempt.selected)
        assertEquals(AttemptResult.PENDING, attempt.result)
        assertEquals(SelectionOrigin.RECOGNIZED, attempt.origin)
        waitForText("1 cevap işaretlendi")
    }

    @Test
    fun eachQuestionOnThePageIsRecordedAsItself() {
        setUpBook()
        show()
        listOf(1 to "B", 2 to "D", 3 to "A", 4 to "E").forEachIndexed { i, (q, l) ->
            circle(q, l)
            until { answered().size == i + 1 }
        }
        assertEquals(listOf(1 to "B", 2 to "D", 3 to "A", 4 to "E"), answered().map { it.question to it.selected })
    }

    @Test
    fun checkingTheTestGradesEverythingAndListsMistakesAndBlanks() {
        setUpBook()
        show()
        circle(1, "A") // correct
        circle(2, "B") // wrong, D
        circle(4, "C") // correct; 3 left blank
        until { answered().size == 3 }
        waitForText("3 cevap işaretlendi")
        rule.onNodeWithText("Testi kontrol et").performClick()
        waitForText("Test sonucu")
        rule.onNodeWithText("Soru 2 · Senin cevabın B · Doğrusu D").assertExists()
        rule.onNodeWithText("Soru 3 · Doğrusu E").assertExists()
        val results = attempts().associate { it.question to it.result }
        assertEquals(mapOf(1 to AttemptResult.CORRECT, 2 to AttemptResult.INCORRECT, 4 to AttemptResult.CORRECT), results)
        // The session is closed: nothing is waiting to be checked any more.
        rule.onNodeWithText("Tamam").performClick()
        rule.waitForIdle()
        assertTrue(answered().isEmpty())
        assertFalse(shown("cevap işaretlendi"))
    }

    @Test
    fun aLaterCircleOnTheSameQuestionReplacesTheEarlierOne() {
        setUpBook()
        show()
        circle(1, "B")
        circle(1, "A")
        until { attempts().size == 2 }
        assertEquals(listOf(1 to "A"), answered().map { it.question to it.selected })
    }

    @Test
    fun undoingACircleWithdrawsItsAnswerAndRedoBringsItBack() {
        setUpBook()
        show()
        circle(1, "B")
        circle(1, "A")
        until { answered().singleOrNull()?.selected == "A" }
        rule.onNode(hasContentDescription("Geri al")).performClick()
        // The A circle is gone, so the earlier B (still on the page) counts again.
        until { answered().singleOrNull()?.selected == "B" }
        rule.onNode(hasContentDescription("Geri al")).performClick()
        until { answered().isEmpty() }
        rule.onNode(hasContentDescription("Yinele")).performClick()
        until { answered().singleOrNull()?.selected == "B" }
    }

    @Test
    fun anUnclearMarkAsksWhichOptionAndSavesTheChoice() {
        setUpBook()
        show()
        val b = option(3, "B")
        val c = option(3, "C")
        circleOnPage((b.centerX + c.centerX) / 2, b.centerY, rx = (c.centerX - b.centerX) / 2 + 0.02f)
        waitForText("Soru 3: hangi şıkkı işaretledin?")
        assertTrue(attempts().isEmpty()) // nothing saved until the student says
        rule.onNodeWithText("C").performClick()
        waitForText("Soru 3: C işaretlendi")
        until { attempts().isNotEmpty() }
        assertEquals(SelectionOrigin.CHOSEN_BY_STUDENT, attempts().single().origin)
    }

    @Test
    fun whenThePageDoesntSayWhichQuestionTheStudentIsAsked() {
        setUpBook(numbered = false)
        show()
        val c = layout.questions.first { it.box.left > 0.5f && it.box.top > 0.5f }.options.single { it.letter == "C" }.box
        circleOnPage(c.centerX, c.centerY)
        waitForText("Bu işaret hangi soru için?")
        assertTrue(attempts().isEmpty())
        rule.onNodeWithText("4").performClick()
        waitForText("Soru 4: C işaretlendi")
        until { answered().isNotEmpty() }
        assertEquals(4 to "C", answered().single().let { it.question to it.selected })
    }

    @Test
    fun cevapDegilLeavesTheMarkAsADrawing() {
        setUpBook()
        show()
        val b = option(1, "B")
        val c = option(1, "C")
        circleOnPage((b.centerX + c.centerX) / 2, b.centerY, rx = (c.centerX - b.centerX) / 2 + 0.02f)
        waitForText("hangi şıkkı")
        rule.onNodeWithText("Cevap değil").performClick()
        rule.waitForIdle()
        assertFalse(shown("hangi şıkkı"))
        assertTrue(attempts().isEmpty())
        assertEquals(1, annotations.all.size)
    }

    @Test
    fun anUnconfirmedKeyBlocksCheckingButKeepsTheAnswers() {
        setUpBook(confirm = false)
        show()
        circle(4, "C")
        until { answered().isNotEmpty() }
        rule.onNodeWithText("Testi kontrol et").performClick()
        waitForText("önce cevap anahtarını onaylaman")
        assertEquals(AttemptResult.PENDING, attempts().single().result)
        rule.onNodeWithText("Cevap anahtarını aç").performClick()
        rule.waitForIdle()
        assertTrue(openedKey)
        // After confirming, the same answers are checked.
        runBlocking { keys.confirm("doc-1") }
        rule.onNodeWithText("Testi kontrol et").performClick()
        waitForText("Test sonucu")
    }

    @Test
    fun whenThePagesTestIsUnknownTheStudentPicksItOnce() {
        val test1 = setUpBook(pages = null)!!
        runBlocking {
            val other = keys.addManualTest("doc-1", "Test 2", 2)
            keys.setAnswer(other, 1, AnswerType.CHOICE, "E")
            keys.confirm("doc-1")
        }
        show()
        circle(1, "A")
        until { answered().isNotEmpty() }
        rule.onNodeWithText("Testi kontrol et").performClick()
        waitForText("hangi teste ait")
        rule.onNodeWithText("Test 1").performClick()
        waitForText("Test sonucu")
        assertEquals(0..0, runBlocking { keys.tests("doc-1") }.single { it.id == test1 }.questionPages)
        assertEquals(AttemptResult.CORRECT, attempts().single().result)
    }

    @Test
    fun immediateFeedbackShowsTheResultRightAway() {
        setUpBook()
        immediate.value = true
        show()
        circle(4, "C")
        waitForText("Doğru! Soru 4: C")
        circle(2, "B")
        waitForText("Soru 2: B seçtin, doğru cevap D.")
        until { attempts().size == 2 }
        assertEquals(setOf(AttemptResult.CORRECT, AttemptResult.INCORRECT), attempts().map { it.result }.toSet())
    }

    @Test
    fun answersSurviveLeavingAndReopeningTheDocument() {
        setUpBook()
        show()
        circle(3, "E")
        until { answered().isNotEmpty() }
        store.clear() // the viewer closes
        val reopened = viewModel()
        until { reopened.solving.value?.latest?.map { it.question to it.selected } == listOf(3 to "E") }
    }

    @Test
    fun theSameMarkGivesTheSameQuestionAtDoubleZoom() {
        setUpBook()
        val vm = show(zoom = 2f)
        // At 200% the page is drawn scaled, so first find where page spots are on screen: two short
        // probe lines (too small to be answers) show how touch positions map onto the page.
        val strokes = (vm.state.value as ViewerUiState.Ready).annotations.strokes(0)
        fun probe(from: Offset, to: Offset): FloatArray {
            rule.onNode(hasContentDescription("Sayfa 1 / 1")).performTouchInput {
                down(Offset(width * from.x, height * from.y))
                moveTo(Offset(width * to.x, height * to.y))
                up()
            }
            rule.waitForIdle()
            return strokes.value!!.last().points
        }
        val across = probe(Offset(0.3f, 0.3f), Offset(0.4f, 0.3f))
        val down = probe(Offset(0.3f, 0.2f), Offset(0.3f, 0.3f))
        val scaleX = (across[2] - across[0]) / 0.1f
        val scaleY = (down[3] - down[1]) / 0.1f
        assertEquals(0.5f, scaleX, 0.02f) // the page really is shown at twice the size

        // Question 1's option C, at the spot it occupies on the zoomed page.
        val c = option(1, "C")
        circleOnPage(0.3f + (c.centerX - across[0]) / scaleX, 0.2f + (c.centerY - down[1]) / scaleY, rx = 0.02f / scaleX, ry = 0.012f / scaleY)
        val mark = strokes.value!!.last().points
        val xs = mark.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = mark.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(c.centerX, (xs.min() + xs.max()) / 2, 0.01f)
        assertEquals(c.centerY, (ys.min() + ys.max()) / 2, 0.01f)
        waitForText("Soru 1: C işaretlendi")
    }

    @Test
    fun withoutAKeyAnswersAreKeptAndCheckingSaysTheKeyIsMissing() {
        setUpBook(withKey = false)
        show()
        circle(2, "A")
        waitForText("Soru 2: A işaretlendi")
        rule.onNodeWithText("Testi kontrol et").performClick()
        waitForText("henüz bir cevap anahtarı yok")
        assertEquals(1, annotations.all.size)
    }
}
