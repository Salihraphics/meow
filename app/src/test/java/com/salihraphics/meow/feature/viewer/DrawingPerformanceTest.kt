package com.salihraphics.meow.feature.viewer

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import com.salihraphics.meow.feature.annotation.AnnotationSession
import com.salihraphics.meow.feature.annotation.AnnotationTool
import com.salihraphics.meow.feature.annotation.InkRenderer
import com.salihraphics.meow.feature.annotation.PageAnnotationLayer
import com.salihraphics.meow.testing.FakeAnnotationRepository
import com.salihraphics.meow.testing.RawTouch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/**
 * How much drawing work one new stroke costs on a page already full of handwriting. Timings
 * are JVM (Robolectric) numbers, only good for before/after comparison; the path counts are
 * exact.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1200dp-mdpi")
class DrawingPerformanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Loader : PdfPageLoader {
        override val pageCount = 3
        override fun aspectRatio(index: Int) = 0.707f
        override suspend fun load(index: Int, widthPx: Int) = ImageBitmap(8, 8)
    }

    @Test
    fun aNewStrokeOnAFullPageCostsOneStrokeNotThePage() {
        val repo = FakeAnnotationRepository()
        // 400 strokes of 150 points on page 0, a third of them with stylus pressure.
        repeat(400) { i ->
            val pts = FloatArray(300) { k -> if (k % 2 == 0) 0.05f + 0.9f * (k / 2) / 150f else 0.02f + 0.0024f * i + 0.003f * sin(k * 0.3f) }
            val pressures = if (i % 3 == 0) FloatArray(150) { 0.3f + 0.4f * sin(it * 0.1f).let { s -> s * s } } else null
            repo.add(Stroke("s$i", "doc", 0, StrokeTool.PEN, 0xFF000000.toInt(), 0.004f, pts, pressures = pressures))
        }
        val session = AnnotationSession("doc", repo, CoroutineScope(Dispatchers.Unconfined))
        lateinit var state: PdfViewportState
        rule.setContent {
            state = rememberPdfViewportState("doc", { ViewportSnapshot(0) }, {})
            val tools by session.tools.collectAsState()
            Box(Modifier.size(800.dp, 1200.dp)) {
                PdfPagesView(Loader(), state, Modifier.size(800.dp, 1200.dp), drawingActive = tools.tool != null, fingerDraws = tools.fingerDraws) { page ->
                    PageAnnotationLayer(page, session, tools)
                }
            }
        }
        session.selectTool(AnnotationTool.PEN)
        rule.waitForIdle()
        val view = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val frame = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        fun draw() = view.draw(android.graphics.Canvas(frame))
        draw() // the first full draw of the page, not measured
        val raw = RawTouch(view)
        val startCount = InkRenderer.preparedCount
        val started = System.nanoTime()
        repeat(10) { i -> raw.stylusLine(150f, 300f + i * 20f, 600f, 310f + i * 20f, start = 1_000L + i * 400); rule.waitForIdle(); draw() }
        val ms = (System.nanoTime() - started) / 1_000_000.0
        val prepared = InkRenderer.preparedCount - startCount
        println("PERF strokes=10 pathsPrepared=$prepared totalMs=$ms perStrokeMs=${ms / 10}")
        // Each new stroke is drawn live (one path per frame while drawing, a handful of frames
        // here) and prepared once when saved; nothing else on the page is rebuilt.
        assertTrue("prepared $prepared paths for 10 strokes", prepared < 10 * 40)
    }
}
