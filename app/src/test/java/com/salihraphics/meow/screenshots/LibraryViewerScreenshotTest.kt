package com.salihraphics.meow.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.domain.model.PdfImportError
import com.salihraphics.meow.feature.library.LibraryActions
import com.salihraphics.meow.feature.library.LibraryContent
import com.salihraphics.meow.feature.library.LibraryMessage
import com.salihraphics.meow.feature.library.LibraryUiState
import com.salihraphics.meow.domain.model.AnswerAttempt
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.SelectionOrigin
import com.salihraphics.meow.feature.check.AnswerCheckUi
import com.salihraphics.meow.feature.check.CheckOutcome
import com.salihraphics.meow.feature.check.GradedQuestion
import com.salihraphics.meow.feature.check.SessionCheck
import com.salihraphics.meow.feature.check.TestResult
import com.salihraphics.meow.feature.viewer.PdfPageLoader
import com.salihraphics.meow.feature.viewer.ViewerAnswerActions
import com.salihraphics.meow.feature.viewer.ViewerAnswers
import com.salihraphics.meow.feature.viewer.PdfViewerContent
import com.salihraphics.meow.feature.viewer.ViewerError
import com.salihraphics.meow.feature.viewer.ViewerUiState
import com.salihraphics.meow.feature.viewer.ViewportSnapshot
import com.salihraphics.meow.testing.sampleDocument
import com.salihraphics.meow.feature.annotation.AnnotationSession
import com.salihraphics.meow.feature.annotation.AnnotationTool
import com.salihraphics.meow.feature.annotation.AnnotationToolState
import com.salihraphics.meow.feature.study.FakeStudyDao
import com.salihraphics.meow.feature.study.StudyTimerController
import com.salihraphics.meow.feature.viewer.ViewerWorkspace
import com.salihraphics.meow.domain.model.StickyNote
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import com.salihraphics.meow.testing.FakeAnnotationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w393dp-h852dp-xhdpi"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-mdpi"

/**
 * Layout checks for the library and viewer. Page images here are synthetic stand-ins (grey
 * "text lines"), because Android's PdfRenderer does not run on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LibraryViewerScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val docs = listOf(
        sampleDocument("1", "TYT Matematik Soru Bankası", pages = 312, lastPage = 41),
        sampleDocument("2", "AYT Fizik Deneme 3", pages = 48),
        sampleDocument("3", "Paragraf 50 Günde", pages = 120, lastPage = 7),
        sampleDocument("4", "Kimya Konu Anlatımı ve Çok Uzun Bir Dosya Adı", pages = 220),
    )

    private val thumbnails: suspend (com.salihraphics.meow.domain.model.PdfDocument) -> ImageBitmap? = { syntheticPage(240) }

    private fun shoot(name: String, dark: Boolean = false, wholeScreen: Boolean = false, act: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.setContent {
            MeowTheme(darkTheme = dark, mascotAnimationsEnabled = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        act()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        // Dialogs are windows of their own; capture everything on screen for those.
        if (wholeScreen) captureScreenRoboImage("build/outputs/roborazzi/pdf/$name.png")
        else rule.onRoot().captureRoboImage("build/outputs/roborazzi/pdf/$name.png")
    }

    private fun library(name: String, documents: List<com.salihraphics.meow.domain.model.PdfDocument>?, state: LibraryUiState = LibraryUiState(), dark: Boolean = false) =
        shoot(name, dark) { LibraryContent(documents, state, thumbnails, LibraryActions()) }

    private fun viewer(
        name: String,
        state: ViewerUiState,
        dark: Boolean = false,
        check: AnswerCheckUi? = null,
        answers: ViewerAnswers = ViewerAnswers(check),
        workspace: ViewerWorkspace = ViewerWorkspace(),
        act: () -> Unit = {},
    ) = shoot(
        name, dark,
        // Phones ask about marks in a dialog, a window of its own.
        wholeScreen = answers.sessionCheck != null || (answers.check != null && answers.check !is AnswerCheckUi.Recorded),
        act = act,
    ) {
        PdfViewerContent(
            state, initialViewport = { ViewportSnapshot(0) }, onViewportChanged = {}, onPageChanged = {}, onBack = {},
            answers = answers, answerActions = ViewerAnswerActions(onOpenAnswerKey = {}), workspace = workspace,
        )
    }

    private val ready = ViewerUiState.Ready(docs[0], SyntheticLoader(312), initialPage = 0, annotations = AnnotationSession("doc-1", FakeAnnotationRepository(), CoroutineScope(Dispatchers.Unconfined)))

    @Test @Config(qualifiers = PHONE) fun libraryEmpty() = library("phone_library_empty", emptyList())
    @Test @Config(qualifiers = PHONE) fun libraryList() = library("phone_library", docs)
    @Test @Config(qualifiers = PHONE) fun libraryImportingDark() = library("phone_library_importing_dark", docs, LibraryUiState(isImporting = true), dark = true)
    @Test @Config(qualifiers = PHONE) fun libraryError() =
        library("phone_library_error", docs, LibraryUiState(message = LibraryMessage.ImportFailed(PdfImportError.PASSWORD_PROTECTED)))
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun libraryTablet() = library("tablet_library", docs)

    @Test @Config(qualifiers = PHONE) fun viewerPhone() = viewer("phone_viewer", ready)
    @Test @Config(qualifiers = PHONE) fun viewerPhoneDark() = viewer("phone_viewer_dark", ready, dark = true)
    @Test @Config(qualifiers = PHONE) fun viewerError() = viewer("phone_viewer_error", ViewerUiState.Error(ViewerError.DAMAGED))
    @Test @Config(qualifiers = PHONE) fun viewerLoading() = viewer("phone_viewer_loading", ViewerUiState.Loading)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun viewerTablet() = viewer("tablet_viewer", ready)

    private fun annotated(tool: AnnotationTool?): ViewerUiState.Ready {
        val repo = FakeAnnotationRepository().apply {
            add(Stroke("a", "1", 0, StrokeTool.PEN, 0xFFE0383E.toInt(), 0.006f, floatArrayOf(0.15f, 0.32f, 0.3f, 0.3f, 0.45f, 0.33f, 0.6f, 0.31f)))
            add(Stroke("b", "1", 0, StrokeTool.PEN, 0xFF1E5EFF.toInt(), 0.004f, floatArrayOf(0.7f, 0.2f, 0.78f, 0.16f, 0.86f, 0.2f, 0.78f, 0.26f, 0.7f, 0.2f)))
        }
        val session = AnnotationSession("1", repo, CoroutineScope(Dispatchers.Unconfined)).apply {
            selectTool(tool)
            selectColor(2)
        }
        return ready.copy(annotations = session)
    }

    @Test @Config(qualifiers = PHONE) fun viewerPen() = viewer("phone_viewer_pen", annotated(AnnotationTool.PEN))
    @Test @Config(qualifiers = PHONE) fun viewerReadingDark() = viewer("phone_viewer_annotated_dark", annotated(null), dark = true)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun viewerTabletEraser() = viewer("tablet_viewer_eraser", annotated(AnnotationTool.ERASER))

    /** Highlights, pen ink and a note on page 1, for the study workspace shots. */
    private fun studied(tool: AnnotationTool?): ViewerUiState.Ready {
        val repo = FakeAnnotationRepository().apply {
            add(Stroke("h1", "1", 0, StrokeTool.HIGHLIGHTER, AnnotationToolState.HIGHLIGHT_COLORS[0], 0.022f, floatArrayOf(0.1f, 0.185f, 0.55f, 0.185f)))
            add(Stroke("h2", "1", 0, StrokeTool.HIGHLIGHTER, AnnotationToolState.HIGHLIGHT_COLORS[1], 0.022f, floatArrayOf(0.1f, 0.24f, 0.7f, 0.24f)))
            add(Stroke("a", "1", 0, StrokeTool.PEN, 0xFFE0383E.toInt(), 0.006f, floatArrayOf(0.15f, 0.42f, 0.3f, 0.4f, 0.45f, 0.43f, 0.6f, 0.41f)))
            putNote(StickyNote("n1", "1", 0, 0.55f, 0.55f, 0.32f, 0.16f, "Türev kurallarını tekrar et!", StickyNote.YELLOW))
            // Handwriting on the note: a check mark and an underline, in the note's own fractions.
            add(Stroke("n1ink", "1", 0, StrokeTool.PEN, 0xFF1E5EFF.toInt(), 0.02f, floatArrayOf(0.1f, 0.6f, 0.2f, 0.8f, 0.4f, 0.45f), "n1"))
            add(Stroke("n1ink2", "1", 0, StrokeTool.PEN, 0xFFE0383E.toInt(), 0.015f, floatArrayOf(0.5f, 0.75f, 0.7f, 0.72f, 0.9f, 0.76f), "n1"))
        }
        val session = AnnotationSession("1", repo, CoroutineScope(Dispatchers.Unconfined)).apply {
            selectTool(tool)
            selectNote("n1")
        }
        return ready.copy(annotations = session)
    }

    private fun timer(): StudyTimerController {
        val dao = FakeStudyDao()
        return StudyTimerController(dao, CoroutineScope(Dispatchers.Unconfined), { 0L }, { 0L }).apply { setDurationMinutes(25) }
    }

    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun workspaceTablet() =
        viewer("tablet_workspace", studied(AnnotationTool.HIGHLIGHTER), workspace = ViewerWorkspace(timer = timer()))
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun workspaceTabletDarkRotated() =
        viewer("tablet_workspace_rotated_dark", studied(AnnotationTool.PEN), dark = true, workspace = ViewerWorkspace(rotation = 90, timer = timer()))
    @Test @Config(qualifiers = PHONE) fun workspacePhoneRail() = viewer("phone_workspace_rail", studied(AnnotationTool.HIGHLIGHTER))
    @Test @Config(qualifiers = PHONE) fun workspacePhoneDrawer() = viewer(
        "phone_workspace_drawer", studied(null), workspace = ViewerWorkspace(timer = timer()),
        act = { rule.onNodeWithContentDescription("Çalışma paneli").performClick() },
    )
    @Test @Config(qualifiers = TABLET_LANDSCAPE, fontScale = 1.6f) fun workspaceTabletLargeText() =
        viewer("tablet_workspace_large_text", studied(AnnotationTool.PEN), workspace = ViewerWorkspace(timer = timer()))
    @Test @Config(qualifiers = PHONE, fontScale = 1.6f) fun workspacePhoneDrawerLargeTextDark() = viewer(
        "phone_workspace_drawer_large_text_dark", studied(null), dark = true, workspace = ViewerWorkspace(timer = timer()),
        act = { rule.onNodeWithContentDescription("Çalışma paneli").performClick() },
    )
    @Test @Config(qualifiers = PHONE) fun workspacePhoneHalfZoom() = shoot("phone_workspace_zoom_50") {
        PdfViewerContent(studied(null), initialViewport = { ViewportSnapshot(0, zoom = 0.5f) }, onViewportChanged = {}, onPageChanged = {}, onBack = {})
    }

    private fun graded(result: AttemptResult, selected: String) =
        AnswerCheckUi.Result(CheckOutcome.Graded(4, selected, "C", result, "t1"))

    @Test @Config(qualifiers = PHONE) fun checkCorrect() = viewer("phone_check_correct", annotated(AnnotationTool.PEN), check = graded(AttemptResult.CORRECT, "C"))
    @Test @Config(qualifiers = PHONE) fun checkIncorrectDark() =
        viewer("phone_check_incorrect_dark", annotated(AnnotationTool.PEN), dark = true, check = graded(AttemptResult.INCORRECT, "B"))
    @Test @Config(qualifiers = PHONE) fun checkAsk() = viewer(
        "phone_check_ask", annotated(AnnotationTool.PEN),
        check = AnswerCheckUi.Ask(4, listOf("B", "C"), listOf("A", "B", "C", "D", "E"), emptyList(), 0, "s", listOf(0.2f, 0.3f, 0.3f, 0.32f)),
    )
    @Test @Config(qualifiers = PHONE) fun checkAskQuestion() = viewer(
        "phone_check_ask_question", annotated(AnnotationTool.PEN),
        check = AnswerCheckUi.Ask(null, listOf("C"), listOf("A", "B", "C", "D", "E"), (listOf(3, 4) + (1..16)).distinct(), 0, "s", listOf(0.2f, 0.3f, 0.3f, 0.32f)),
    )
    @Test @Config(qualifiers = PHONE) fun recordedWithSessionBar() = viewer(
        "phone_recorded_session_bar", annotated(AnnotationTool.PEN),
        answers = ViewerAnswers(AnswerCheckUi.Recorded(4, "C", "a4"), recorded = sampleAnswers(7)),
    )
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun sessionBarTablet() = viewer(
        "tablet_session_bar", annotated(AnnotationTool.PEN), answers = ViewerAnswers(recorded = sampleAnswers(12)),
    )

    private fun sampleAnswers(count: Int) = (1..count).map { q ->
        AnswerAttempt("a$q", "1", "t1", q, q / 4, "ABCDE"[q % 5].toString(), SelectionOrigin.RECOGNIZED, AttemptResult.PENDING, "s$q", q.toLong())
    }

    private val checked = SessionCheck.Checked(
        TestResult(
            (1..16).map { q ->
                val selected = when (q) { 4 -> "B"; 9 -> "E"; 13, 15 -> null; else -> "C" }
                val correct = when (q) { 4 -> "D"; 9 -> "A"; else -> "C" }
                GradedQuestion(
                    "t1", "Test 3", q, selected, correct,
                    selected?.let { if (it == correct) AttemptResult.CORRECT else AttemptResult.INCORRECT },
                    if (selected != null) 40 + q / 5 else null, "a$q",
                )
            },
        ),
        notInKey = emptyList(),
    )
    @Test @Config(qualifiers = PHONE) fun results() = viewer("phone_results", annotated(null), answers = ViewerAnswers(sessionCheck = checked))
    @Test @Config(qualifiers = PHONE) fun resultsDark() = viewer("phone_results_dark", annotated(null), dark = true, answers = ViewerAnswers(sessionCheck = checked))
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun checkTabletNotConfirmed() =
        viewer("tablet_check_not_confirmed", annotated(AnnotationTool.PEN), check = AnswerCheckUi.KeyNotConfirmed)
}

private class SyntheticLoader(override val pageCount: Int) : PdfPageLoader {
    override fun aspectRatio(index: Int) = 595f / 842f
    override suspend fun load(index: Int, widthPx: Int): ImageBitmap = syntheticPage(widthPx)
}

/** A white page with grey bars standing in for text. Test-only. */
private fun syntheticPage(widthPx: Int): ImageBitmap {
    val w = widthPx.coerceAtMost(800)
    val h = w * 842 / 595
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint().apply { color = 0xFFD5D2CC.toInt() }
    val margin = w * 0.1f
    var y = h * 0.1f
    canvas.drawRect(margin, y, w * 0.6f, y + h * 0.025f, Paint().apply { color = 0xFF8C8794.toInt() })
    y += h * 0.07f
    while (y < h * 0.88f) {
        canvas.drawRect(margin, y, w - margin - (y.toInt() % 5) * w * 0.04f, y + h * 0.011f, paint)
        y += h * 0.028f
    }
    return bitmap.asImageBitmap()
}
