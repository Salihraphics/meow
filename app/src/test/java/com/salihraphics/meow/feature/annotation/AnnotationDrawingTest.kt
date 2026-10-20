package com.salihraphics.meow.feature.annotation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWindow
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.feature.viewer.PdfPageLoader
import com.salihraphics.meow.feature.viewer.PdfViewerContent
import com.salihraphics.meow.feature.viewer.ViewerUiState
import com.salihraphics.meow.feature.viewer.ViewportSnapshot
import com.salihraphics.meow.testing.FakeAnnotationRepository
import com.salihraphics.meow.testing.sampleDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real viewer and toolbar with touch input and checks both what is stored and the
 * pixels on screen. Pages are blank white stand-ins (PdfRenderer does not run on the JVM).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1400dp-h1400dp-mdpi")
class AnnotationDrawingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private data class Screen(val width: Dp, val height: Dp)

    private val phonePortrait = Screen(393.dp, 852.dp)
    private val phoneLandscape = Screen(852.dp, 393.dp)

    private val repo = FakeAnnotationRepository()
    private val session = AnnotationSession("doc-1", repo, CoroutineScope(Dispatchers.Unconfined))
    private val ready = ViewerUiState.Ready(sampleDocument(pages = 10), BlankPages(10), initialPage = 0, annotations = session)

    private var viewport = ViewportSnapshot(0)
    private var screen by mutableStateOf(phonePortrait)
    private var generation by mutableIntStateOf(0)

    private fun start() {
        rule.setContent {
            key(generation) { Viewer(screen) }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun Viewer(current: Screen) {
        MeowTheme(darkTheme = false, mascotAnimationsEnabled = false) {
            CompositionLocalProvider(LocalMeowWindow provides MeowWindow.fromDp(current.width.value.toInt(), current.height.value.toInt())) {
                Box(Modifier.requiredSize(current.width, current.height)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        PdfViewerContent(ready, { viewport }, { viewport = it }, {}, {})
                    }
                }
            }
        }
    }

    private fun page(number: Int) = rule.onNode(hasContentDescription("Sayfa $number / 10"))

    private fun tap(label: String) {
        rule.onNode(hasContentDescription(label)).performClick()
        rule.waitForIdle()
    }

    /** Swipes across [page] between two points given as fractions of the page. */
    private fun drawOn(page: SemanticsNodeInteraction, from: Offset, to: Offset) {
        page.performTouchInput {
            swipe(Offset(width * from.x, height * from.y), Offset(width * to.x, height * to.y), 400)
        }
        rule.waitForIdle()
    }

    /** The colour on screen at a spot of [page] given in page fractions. */
    private fun pixelOnPage(page: SemanticsNodeInteraction, fx: Float, fy: Float): Int {
        val node = page.fetchSemanticsNode()
        val x = node.positionInRoot.x + node.size.width * fx
        val y = node.positionInRoot.y + node.size.height * fy
        return screenshot().getPixel(x.toInt(), y.toInt())
    }

    /** Draws the window into a bitmap; positions match the semantics coordinates used above. */
    private fun screenshot(): Bitmap {
        rule.waitForIdle()
        val root = rule.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        var bitmap: Bitmap? = null
        rule.runOnUiThread {
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(android.graphics.Canvas(it)) }
        }
        return bitmap!!
    }

    private fun isRed(color: Int) = android.graphics.Color.red(color) > 170 &&
        android.graphics.Color.green(color) < 120 && android.graphics.Color.blue(color) < 120

    private fun isWhite(color: Int) = android.graphics.Color.red(color) > 240 &&
        android.graphics.Color.green(color) > 240 && android.graphics.Color.blue(color) > 240

    private fun drawRedLineAcrossFirstPage() {
        tap("Kalem")
        tap("Kırmızı")
        tap("Kalın çizgi")
        drawOn(page(1), Offset(0.3f, 0.4f), Offset(0.7f, 0.4f))
    }

    @Test
    fun strokesAreStoredAsPageFractionsAndDrawnWhereTheFingerWent() {
        start()
        drawRedLineAcrossFirstPage()

        val stroke = repo.all.values.single()
        assertEquals(0, stroke.pageIndex)
        val xs = stroke.points.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = stroke.points.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(0.3f, xs.first(), 0.01f)
        assertEquals(0.7f, xs.last(), 0.01f)
        assertTrue(ys.all { kotlin.math.abs(it - 0.4f) < 0.01f })

        assertTrue(isRed(pixelOnPage(page(1), 0.5f, 0.4f)))
        assertTrue(isWhite(pixelOnPage(page(1), 0.5f, 0.6f)))
    }

    @Test
    fun strokesStayOnTheSameSpotOfThePageAfterZoomAndRotation() {
        start()
        drawRedLineAcrossFirstPage()

        // Rotate into the two-pane landscape layout, zoomed to 200% and panned to the middle.
        viewport = ViewportSnapshot(pageIndex = 0, pageOffsetFraction = 0.3f, zoom = 2f, horizontalFraction = 0.5f)
        screen = phoneLandscape
        generation++
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()

        // The page is now a different size on screen, but the line is still at 40% of its height.
        assertTrue(isRed(pixelOnPage(page(1), 0.5f, 0.4f)))
        // Just above the line (below it, the floating toolbar covers the page in short landscape).
        assertTrue(isWhite(pixelOnPage(page(1), 0.5f, 0.36f)))
        assertTrue(isRed(pixelOnPage(page(1), 0.35f, 0.4f)))
        assertEquals(1, repo.all.size)
    }

    @Test
    fun aStrokeCannotSpillOntoTheNextPage() {
        start()
        tap("Kalem")
        // Start near the bottom of page 1 and drag well past its edge.
        drawOn(page(1), Offset(0.5f, 0.9f), Offset(0.5f, 1.4f))
        val stroke = repo.all.values.single()
        assertEquals(0, stroke.pageIndex)
        assertTrue(stroke.points.all { it in 0f..1f })
        session.load(1)
        assertTrue(session.strokes(1).value.orEmpty().isEmpty())
    }

    @Test
    fun undoAndRedoFromTheToolbar() {
        start()
        drawRedLineAcrossFirstPage()
        tap("Geri al")
        assertTrue(isWhite(pixelOnPage(page(1), 0.5f, 0.4f)))
        assertTrue(repo.all.keys.single() in repo.deleted)
        tap("Yinele")
        assertTrue(isRed(pixelOnPage(page(1), 0.5f, 0.4f)))
        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun eraserRemovesTheStrokeItCrosses() {
        start()
        drawRedLineAcrossFirstPage()
        tap("Silgi")
        drawOn(page(1), Offset(0.5f, 0.3f), Offset(0.5f, 0.5f))
        assertTrue(repo.all.keys.single() in repo.deleted)
        assertTrue(isWhite(pixelOnPage(page(1), 0.5f, 0.4f)))
        tap("Geri al") // brings it back
        assertTrue(isRed(pixelOnPage(page(1), 0.5f, 0.4f)))
    }

    @Test
    fun inReadingModeASwipeScrollsAndDrawsNothing() {
        start()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipe(Offset(centerX, centerY + 200f), Offset(centerX, centerY - 200f), 300) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertTrue(repo.all.isEmpty())
        assertTrue("the swipe should have scrolled", viewport.pageIndex >= 2 && (viewport.pageIndex > 2 || viewport.pageOffsetFraction > 0f))
    }
}

/** White pages of A4 shape. */
class BlankPages(override val pageCount: Int) : PdfPageLoader {
    override fun aspectRatio(index: Int) = 595f / 842f
    override suspend fun load(index: Int, widthPx: Int): ImageBitmap =
        Bitmap.createBitmap(8, 11, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }.asImageBitmap()
}
