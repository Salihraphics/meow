package com.salihraphics.meow.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Documents and saved states that must never crash the viewer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class ViewerRobustnessTest {
    @get:Rule val rule = createComposeRule()

    /** Real-world oddities: a zero-width page, a receipt 60× taller than wide, a banner, NaN. */
    private class OddLoader(override val pageCount: Int) : PdfPageLoader {
        val requests = mutableListOf<Pair<Int, Int>>()
        override fun aspectRatio(index: Int) = when (index % 6) {
            1 -> 0f
            2 -> 1f / 60f
            3 -> 40f
            4 -> Float.NaN
            else -> 0.707f
        }
        override suspend fun load(index: Int, widthPx: Int): ImageBitmap {
            requests += index to widthPx
            return ImageBitmap(4, 4)
        }
    }

    private lateinit var state: PdfViewportState
    private lateinit var scope: CoroutineScope

    private fun show(loader: PdfPageLoader, start: ViewportSnapshot) {
        rule.setContent {
            scope = rememberCoroutineScope()
            state = rememberPdfViewportState("doc", { start }, {})
            Box(Modifier.size(1280.dp, 800.dp)) { PdfPagesView(loader, state, Modifier.size(1280.dp, 800.dp)) }
        }
        rule.waitForIdle()
    }

    @Test
    fun oddPagesAtEveryZoomDoNotCrash() {
        val loader = OddLoader(60)
        show(loader, ViewportSnapshot(0))
        for (zoom in listOf(4f, 0.5f, 2f, 4f)) {
            scope.launch { state.zoomTo(zoom) }
            rule.waitForIdle()
            rule.onNode(hasScrollToIndexAction()).performScrollToIndex(13)
            rule.onRoot().performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        assertEquals(4f, state.zoom)
    }

    @Test
    fun aCorruptSavedPositionOpensAtASaneOne() {
        show(OddLoader(10), ViewportSnapshot(3, pageOffsetFraction = Float.NaN, zoom = Float.NaN, horizontalFraction = Float.POSITIVE_INFINITY))
        assertEquals(1f, state.zoom)
        scope.launch { state.zoomIn() }
        rule.waitForIdle()
        assertTrue(state.zoom.isFinite())
    }

    @Test
    fun aVeryTallPageIsRenderedWithinThePixelBudget() {
        // A receipt-like page shown 2000px wide would be 120,000px tall: about 1 GB as one bitmap.
        val width = renderWidthFor(2000f, pageAspect = 1f / 60f)
        val height = width * 60f
        assertTrue("$width × $height px", width * height <= MAX_RENDER_PIXELS)
        // Ordinary pages are unaffected.
        assertEquals(1536, renderWidthFor(1500f, pageAspect = 0.707f))
    }
}
