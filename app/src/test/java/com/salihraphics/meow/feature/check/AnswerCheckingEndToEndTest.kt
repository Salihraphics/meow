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
import com.salihraphics.meow.analysis.FixturePdf.Companion.questionPage
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
 * A student circles options with the pen on the real viewer. Question positions come from the
 * real text of a generated Turkish PDF (PdfBox); the key lives in a real (in-memory) database.
 * The page images are blank: Android's PdfRenderer does not run on the JVM.
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

    private val store = ViewModelStore()

    @After fun tearDown() {
        // As when the screen closes: the ViewModel stops before the database goes away.
        store.clear()
        rule.waitForIdle()
        db.close()
    }

    private fun setUpBook(confirm: Boolean = true) {
        book = FixturePdf().apply { page { questionPage("TEST 1", first = 1, pageNumber = 1) } }.write(tmp.newFile("book.pdf"))
        layout = textSource.open(book).use { QuestionLayoutDetector.detect(it.page(0)) }
        runBlocking {
            val test = keys.addManualTest("doc-1", "Test 1", 1)
            keys.setAnswer(test, 1, AnswerType.CHOICE, "C")
            keys.setAnswer(test, 2, AnswerType.CHOICE, "D")
            keys.updateTest(test, "Test 1", 0..0)
            if (confirm) keys.confirm("doc-1")
        }
    }

    private fun show(zoom: Float = 1f): PdfViewerViewModel {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument(pages = 1)) }
        val factory = viewModelFactory {
            initializer {
                PdfViewerViewModel(
                    documentId = "doc-1",
                    savedState = SavedStateHandle(mapOf("viewer.viewport" to floatArrayOf(0f, 0f, zoom, 0f))),
                    repository = repo,
                    sessions = FakeSessionFactory(pages = 1),
                    annotationRepository = FakeAnnotationRepository(),
                    applicationScope = CoroutineScope(Dispatchers.Unconfined),
                    answerKeys = keys,
                    layoutSourceFactory = { _, _ -> PdfPageLayoutSource(book, textSource, ocr = null) },
                )
            }
        }
        val vm = ViewModelProvider.create(store, factory)[PdfViewerViewModel::class]
        rule.setContent {
            MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) {
                CompositionLocalProvider(LocalMeowWindow provides MeowWindow.fromDp(411, 891)) {
                    Box(Modifier.requiredSize(411.dp, 891.dp)) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            PdfViewerRoute(vm, onBack = {}, onOpenAnswerKey = { openedKey = true })
                        }
                    }
                }
            }
        }
        rule.waitUntil(5_000) { vm.state.value is ViewerUiState.Ready }
        rule.onNode(hasContentDescription("Kalem")).performClick()
        rule.waitForIdle()
        return vm
    }

    /** Draws a loop around ([cx], [cy]) on page 1, positions in page fractions. */
    private fun circleOnPage(cx: Float, cy: Float, rx: Float = 0.025f, ry: Float = 0.014f) {
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

    private fun option(question: Int, letter: String) =
        layout.questions.single { it.number == question }.options.single { it.letter == letter }.box

    private fun waitForText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun attempts() = runBlocking { keys.observeAttempts("doc-1").first() }

    @Test
    fun circlingTheRightOptionSaysCorrectAndRecordsIt() {
        setUpBook()
        show()
        val c = option(1, "C")
        circleOnPage(c.centerX, c.centerY)
        waitForText("Doğru! Soru 1: C")
        rule.waitUntil(5_000) { attempts().isNotEmpty() }
        val attempt = attempts().single()
        assertEquals(1, attempt.question)
        assertEquals("C", attempt.selected)
        assertEquals(AttemptResult.CORRECT, attempt.result)
        assertEquals(SelectionOrigin.RECOGNIZED, attempt.origin)
        assertTrue(attempt.strokeId != null)
    }

    @Test
    fun aWrongOptionShowsTheCorrectOne() {
        setUpBook()
        show()
        val b = option(2, "B")
        circleOnPage(b.centerX, b.centerY)
        waitForText("Soru 2: B seçtin, doğru cevap D.")
        rule.waitUntil(5_000) { attempts().any { it.result == AttemptResult.INCORRECT } }
    }

    @Test
    fun anUnclearMarkAsksAndTheStudentsChoiceIsGraded() {
        setUpBook()
        show()
        val b = option(1, "B")
        val c = option(1, "C")
        circleOnPage((b.centerX + c.centerX) / 2, b.centerY, rx = (c.centerX - b.centerX) / 2 + 0.02f)
        waitForText("Soru 1: hangi şıkkı işaretledin?")
        assertTrue(attempts().isEmpty()) // nothing graded until the student says
        rule.onNodeWithText("C").performClick()
        waitForText("Doğru! Soru 1: C")
        rule.waitUntil(5_000) { attempts().isNotEmpty() }
        assertEquals(SelectionOrigin.CHOSEN_BY_STUDENT, attempts().single().origin)
    }

    @Test
    fun cevapDegilDismissesWithoutGrading() {
        setUpBook()
        show()
        val b = option(1, "B")
        val c = option(1, "C")
        circleOnPage((b.centerX + c.centerX) / 2, b.centerY, rx = (c.centerX - b.centerX) / 2 + 0.02f)
        waitForText("hangi şıkkı")
        rule.onNodeWithText("Cevap değil").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("hangi şıkkı", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(attempts().isEmpty())
    }

    @Test
    fun theSameMarkGivesTheSameResultAtDoubleZoom() {
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
        val across = probe(Offset(0.5f, 0.5f), Offset(0.6f, 0.5f))
        val down = probe(Offset(0.5f, 0.3f), Offset(0.5f, 0.4f))
        val scaleX = (across[2] - across[0]) / 0.1f
        val scaleY = (down[3] - down[1]) / 0.1f
        assertEquals(0.5f, scaleX, 0.02f) // the page really is shown at twice the size

        // Circle option C at the spot it occupies on the zoomed page.
        val c = option(1, "C")
        circleOnPage(
            0.5f + (c.centerX - across[0]) / scaleX, 0.3f + (c.centerY - down[1]) / scaleY,
            rx = 0.025f / scaleX, ry = 0.014f / scaleY,
        )
        val mark = strokes.value!!.last().points
        val xs = mark.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = mark.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(c.centerX, (xs.min() + xs.max()) / 2, 0.01f)
        assertEquals(c.centerY, (ys.min() + ys.max()) / 2, 0.01f)
        waitForText("Doğru! Soru 1: C")
    }

    @Test
    fun anUnconfirmedKeyIsNeverUsedForGrading() {
        setUpBook(confirm = false)
        show()
        val c = option(1, "C")
        circleOnPage(c.centerX, c.centerY)
        waitForText("henüz onaylanmadı")
        assertTrue(attempts().isEmpty())
        rule.onNodeWithText("Cevap anahtarını aç").performClick()
        rule.waitForIdle()
        assertTrue(openedKey)
    }

    @Test
    fun withoutAKeyPenStrokesAreJustDrawings() {
        book = FixturePdf().apply { page { questionPage("TEST 1", first = 1, pageNumber = 1) } }.write(tmp.newFile("book.pdf"))
        layout = textSource.open(book).use { QuestionLayoutDetector.detect(it.page(0)) }
        val vm = show()
        val c = option(1, "C")
        circleOnPage(c.centerX, c.centerY)
        rule.waitForIdle()
        assertEquals(null, vm.answerCheck.value)
        assertEquals(1, (vm.state.value as ViewerUiState.Ready).annotations.strokes(0).value?.size)
    }
}
