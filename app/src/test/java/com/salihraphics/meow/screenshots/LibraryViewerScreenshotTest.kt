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
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.domain.model.PdfImportError
import com.salihraphics.meow.feature.library.LibraryActions
import com.salihraphics.meow.feature.library.LibraryContent
import com.salihraphics.meow.feature.library.LibraryMessage
import com.salihraphics.meow.feature.library.LibraryUiState
import com.salihraphics.meow.feature.viewer.PdfPageLoader
import com.salihraphics.meow.feature.viewer.PdfViewerContent
import com.salihraphics.meow.feature.viewer.ViewerError
import com.salihraphics.meow.feature.viewer.ViewerUiState
import com.salihraphics.meow.feature.viewer.ViewportSnapshot
import com.salihraphics.meow.testing.sampleDocument
import com.salihraphics.meow.feature.annotation.AnnotationSession
import com.salihraphics.meow.feature.annotation.AnnotationTool
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

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            MeowTheme(darkTheme = dark, mascotAnimationsEnabled = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/pdf/$name.png")
    }

    private fun library(name: String, documents: List<com.salihraphics.meow.domain.model.PdfDocument>?, state: LibraryUiState = LibraryUiState(), dark: Boolean = false) =
        shoot(name, dark) { LibraryContent(documents, state, thumbnails, LibraryActions()) }

    private fun viewer(name: String, state: ViewerUiState, dark: Boolean = false) =
        shoot(name, dark) { PdfViewerContent(state, initialViewport = { ViewportSnapshot(0) }, onViewportChanged = {}, onPageChanged = {}, onBack = {}) }

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
