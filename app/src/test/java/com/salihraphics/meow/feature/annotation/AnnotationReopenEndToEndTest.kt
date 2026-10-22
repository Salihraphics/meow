package com.salihraphics.meow.feature.annotation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWindow
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.data.annotation.LocalAnnotationRepository
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.feature.viewer.PdfViewerRoute
import com.salihraphics.meow.feature.viewer.PdfViewerViewModel
import com.salihraphics.meow.testing.FakePdfLibraryRepository
import com.salihraphics.meow.testing.FakeSessionFactory
import com.salihraphics.meow.testing.sampleDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
import kotlin.reflect.KClass

/**
 * Close-and-reopen through the real viewer stack: ViewModel, route, toolbar, drawing layer,
 * ordered write queue and a file-backed Room database. Only the PDF pages are stand-ins.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1000dp-mdpi")
class AnnotationReopenEndToEndTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun openDb(file: java.io.File) =
        Room.databaseBuilder(RuntimeEnvironment.getApplication(), MeowDatabase::class.java, file.path).build()

    private fun viewModel(store: ViewModelStore, library: FakePdfLibraryRepository, annotations: LocalAnnotationRepository) =
        ViewModelProvider.create(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = PdfViewerViewModel(
                "doc-1", SavedStateHandle(), library, FakeSessionFactory(pages = 6),
                annotations, appScope,
            ) as T
        })[PdfViewerViewModel::class]

    private fun pixelAt(fx: Float, fy: Float): Int {
        rule.waitForIdle()
        val node = rule.onNode(hasContentDescription("Sayfa 1 / 6")).fetchSemanticsNode()
        val root = rule.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        var color = 0
        rule.runOnUiThread {
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            color = bitmap.getPixel((node.positionInRoot.x + node.size.width * fx).toInt(), (node.positionInRoot.y + node.size.height * fy).toInt())
        }
        return color
    }

    @Test
    fun annotationsSurviveClosingAndReopeningTheDocument() {
        val library = FakePdfLibraryRepository(tmp.newFolder("lib")).apply { add(sampleDocument(pages = 6)) }
        val dbFile = tmp.root.resolve("meow.db")
        var db = openDb(dbFile)
        var annotations = LocalAnnotationRepository(db.strokes(), appScope)
        var store = ViewModelStore()
        var vm by mutableStateOf(viewModel(store, library, annotations))

        rule.setContent {
            key(vm) {
                MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) {
                    CompositionLocalProvider(LocalMeowWindow provides MeowWindow.fromDp(393, 852)) {
                        Box(Modifier.requiredSize(393.dp, 852.dp)) {
                            Surface(color = MaterialTheme.colorScheme.background) { PdfViewerRoute(vm, onBack = {}) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        // Draw a red line with the pen.
        listOf("Kalem", "Kırmızı", "Kalın çizgi").forEach {
            rule.onNode(hasContentDescription(it)).performClick()
            rule.waitForIdle()
        }
        rule.onNode(hasContentDescription("Sayfa 1 / 6")).performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.3f), Offset(width * 0.8f, height * 0.3f), 400)
        }
        rule.waitForIdle()
        assertTrue(isRed(pixelAt(0.5f, 0.3f)))

        // Close the viewer straight away, then the database, as when leaving the app.
        store.clear()
        runBlocking { annotations.flush() } // the app-wide queue finishes after the screen is gone
        db.close()

        // Reopen everything from disk.
        db = openDb(dbFile)
        annotations = LocalAnnotationRepository(db.strokes(), appScope)
        store = ViewModelStore()
        vm = viewModel(store, library, annotations)
        // Loading runs on Room's own thread, which Compose's idling does not track.
        rule.waitUntil(5_000) { runCatching { isRed(pixelAt(0.5f, 0.3f)) }.getOrDefault(false) }

        assertTrue("the stroke is drawn again on the same spot", isRed(pixelAt(0.5f, 0.3f)))
        assertTrue(isWhite(pixelAt(0.5f, 0.5f)))
        val saved = runBlocking { annotations.strokesOnPage("doc-1", 0) }
        assertEquals(1, saved.size)
        db.close()
    }

    @Test
    fun highlightsNotesAndTheTurnSurviveReopening() {
        val library = FakePdfLibraryRepository(tmp.newFolder("lib")).apply { add(sampleDocument(pages = 6)) }
        val dbFile = tmp.root.resolve("meow2.db")
        var db = openDb(dbFile)
        var annotations = LocalAnnotationRepository(db.strokes(), appScope)
        var store = ViewModelStore()
        var vm by mutableStateOf(viewModel(store, library, annotations))
        rule.setContent {
            key(vm) {
                MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) {
                    CompositionLocalProvider(LocalMeowWindow provides MeowWindow.fromDp(393, 852)) {
                        Box(Modifier.requiredSize(393.dp, 852.dp)) {
                            Surface(color = MaterialTheme.colorScheme.background) { PdfViewerRoute(vm, onBack = {}) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        fun tap(label: String) {
            rule.onNode(hasContentDescription(label)).apply { runCatching { performScrollTo() } }.performClick()
            rule.waitForIdle()
        }

        tap("Fosforlu kalem")
        rule.onNode(hasContentDescription("Sayfa 1 / 6")).performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.3f), Offset(width * 0.8f, height * 0.3f), 400)
        }
        rule.waitForIdle()
        tap("Not ekle")
        tap("Yazı yaz")
        rule.onNode(hasSetTextAction()).performTextInput("Limit tekrarı")
        rule.onNode(hasText("Kaydet")).performClick()
        rule.waitForIdle()
        tap("Çalışma paneli")
        rule.mainClock.advanceTimeBy(600)
        tap("Sağa döndür")
        tap("Paneli kapat")
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        val noteBefore = rule.onNode(hasContentDescription("Not: Limit tekrarı")).fetchSemanticsNode().boundsInRoot

        store.clear()
        runBlocking { annotations.flush() }
        db.close()

        db = openDb(dbFile)
        annotations = LocalAnnotationRepository(db.strokes(), appScope)
        store = ViewModelStore()
        vm = viewModel(store, library, annotations)
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("Not: Limit tekrarı")).fetchSemanticsNodes().isNotEmpty() }

        // Same turn, same note in the same place on screen, same highlight.
        assertEquals(90, vm.rotation.value)
        assertEquals(noteBefore, rule.onNode(hasContentDescription("Not: Limit tekrarı")).fetchSemanticsNode().boundsInRoot)
        val strokes = runBlocking { annotations.strokesOnPage("doc-1", 0) }
        assertEquals(listOf(com.salihraphics.meow.domain.model.StrokeTool.HIGHLIGHTER), strokes.map { it.tool })
        assertEquals(listOf("Limit tekrarı"), runBlocking { annotations.notesOnPage("doc-1", 0) }.map { it.text })
        db.close()
    }

    private fun isRed(c: Int) = android.graphics.Color.red(c) > 170 && android.graphics.Color.green(c) < 120 && android.graphics.Color.blue(c) < 120
    private fun isWhite(c: Int) = android.graphics.Color.red(c) > 240 && android.graphics.Color.green(c) > 240 && android.graphics.Color.blue(c) > 240
}
