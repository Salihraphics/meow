package com.salihraphics.meow.feature.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.data.pdf.PdfPageSize
import com.salihraphics.meow.data.pdf.PdfRenderSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A 2000-page PDF: opening, jumping, flinging and zooming stay bounded (only pages near the
 * screen are ever rendered) and returning to a page doesn't render it again. Times are JVM
 * numbers for comparison, not device measurements.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class LargeDocumentTest {
    @get:Rule val rule = createComposeRule()

    private class CountingSession(pages: Int) : PdfRenderSession {
        override val pageSizes = List(pages) { if (it % 7 == 3) PdfPageSize(842, 595) else PdfPageSize(595, 842) }
        val renders = mutableListOf<Pair<Int, Int>>()
        override suspend fun render(pageIndex: Int, widthPx: Int): Bitmap {
            renders += pageIndex to widthPx
            return Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        }
        override fun close() = Unit
    }

    @Test
    fun aLongDocumentOnlyEverRendersPagesNearTheScreen() {
        val session = CountingSession(2000)
        val loader = SessionPageLoader(session)
        lateinit var state: PdfViewportState
        lateinit var scope: CoroutineScope
        val opened = System.nanoTime()
        rule.setContent {
            scope = rememberCoroutineScope()
            state = rememberPdfViewportState("big", { ViewportSnapshot(0) }, {})
            Box(Modifier.size(1280.dp, 800.dp)) { PdfPagesView(loader, state, Modifier.size(1280.dp, 800.dp)) }
        }
        rule.waitForIdle()
        val openMs = (System.nanoTime() - opened) / 1e6
        val pages = rule.onNode(hasScrollToIndexAction())
        val started = System.nanoTime()
        for (target in listOf(500, 1500, 1999, 3, 1000)) {
            pages.performScrollToIndex(target)
            repeat(3) { rule.onRoot().performTouchInput { swipeUp(durationMillis = 60) } }
            rule.waitForIdle()
        }
        scope.launch { state.zoomTo(3f) }
        rule.waitForIdle()
        scope.launch { state.zoomTo(1f) }
        rule.waitForIdle()
        val moveMs = (System.nanoTime() - started) / 1e6
        println("LARGE open=${"%.0f".format(openMs)}ms jumps+flings+zooms=${"%.0f".format(moveMs)}ms renders=${session.renders.size} distinctPages=${session.renders.map { it.first }.toSet().size}")
        assertTrue("rendered ${session.renders.size} times for 2000 pages", session.renders.size < 200)
    }

    @Test
    fun returningToAPageUsesTheCachedRendering() = runBlocking {
        val session = CountingSession(10)
        val loader = SessionPageLoader(session, maxCacheBytes = 10 * 1024 * 1024)
        loader.load(3, 1536)
        loader.load(4, 1536)
        loader.load(3, 1536)
        assertEquals(listOf(3 to 1536, 4 to 1536), session.renders)
    }
}
