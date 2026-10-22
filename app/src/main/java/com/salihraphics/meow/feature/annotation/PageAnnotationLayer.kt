package com.salihraphics.meow.feature.annotation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool

/**
 * Decides which pointers draw. One place, so stylus-only drawing or palm rejection can be added
 * later without touching the gesture code.
 */
object DrawingInputPolicy {
    fun draws(type: PointerType, tool: AnnotationTool?): Boolean =
        tool != null && (type == PointerType.Touch || type == PointerType.Stylus || type == PointerType.Mouse || type == PointerType.Eraser)
}

/**
 * The strokes of one page, drawn in that page's bounds, plus drawing input while a tool is
 * active. Lives in the page's overlay slot, so it scrolls, zooms and rotates with the page.
 * A gesture belongs to the page it started on and is clamped to it. Ink written on a note is
 * the note's, drawn by [PageNotesLayer].
 */
@Composable
fun PageAnnotationLayer(
    pageIndex: Int,
    session: AnnotationSession,
    tools: AnnotationToolState,
    modifier: Modifier = Modifier,
) {
    val strokes by remember(pageIndex) { session.strokes(pageIndex) }.collectAsState()
    LaunchedEffect(pageIndex) { session.load(pageIndex) }
    InkCanvas(
        strokes = strokes?.filter { it.noteId == null },
        tools = tools,
        onDraw = { points, tool -> session.addStroke(pageIndex, points, tool) },
        onErase = { ids -> session.erase(pageIndex, ids) },
        modifier = modifier,
    )
}

/**
 * Ink in a box (a page, or a note): [strokes] in fractions of the box, drawn scaled to it, and
 * pen, highlighter and eraser input while one of them is selected. Null [strokes] (not loaded
 * yet) take no input. Line widths are fractions of the box's width times [widthScale] (a note is
 * narrower than its page, and its pen should look as thick as on the page).
 */
// `historical` gives the touch samples between frames, which keeps fast strokes smooth.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InkCanvas(
    strokes: List<Stroke>?,
    tools: AnnotationToolState,
    onDraw: (FloatArray, AnnotationTool) -> Unit,
    onErase: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    widthScale: Float = 1f,
) {
    val latestStrokes by rememberUpdatedState(strokes)
    val latestOnDraw by rememberUpdatedState(onDraw)
    val latestOnErase by rememberUpdatedState(onErase)

    // The stroke being drawn right now, in fractions of the box.
    val inProgress = remember { mutableStateListOf<Float>() }
    var erasing by remember { mutableStateOf(emptySet<String>()) }
    var eraserAt by remember { mutableStateOf<Offset?>(null) }
    val eraserRadius = with(LocalDensity.current) { 14.dp.toPx() }
    val tool = tools.tool
    // The pen's style is kept for the eraser so the gesture code below has one shape.
    val style = tools.styleFor(tool) ?: tools.penStyle

    val input = if (tool != null && strokes != null) {
        Modifier.pointerInput(tool, style) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!DrawingInputPolicy.draws(down.type, tool)) return@awaitEachGesture
                down.consume()
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val aspect = h / w

                fun touch(position: Offset) {
                    when (tool) {
                        AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> {
                            val (x, y) = StrokeGeometry.toFraction(position.x, position.y, w, h)
                            if (StrokeGeometry.shouldAppend(inProgress, x, y, aspect)) {
                                inProgress.add(x)
                                inProgress.add(y)
                            }
                        }
                        AnnotationTool.ERASER -> {
                            eraserAt = position
                            val hit = latestStrokes.orEmpty().filter {
                                it.id !in erasing && StrokeGeometry.hits(it, position.x, position.y, eraserRadius, w, h)
                            }
                            if (hit.isNotEmpty()) erasing = erasing + hit.map { it.id }
                        }
                    }
                }

                inProgress.clear()
                touch(down.position)
                var cancelled = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    // A second finger means pan/zoom; the viewer takes the gesture over.
                    if (change == null || event.changes.size > 1 || change.isConsumed) {
                        cancelled = true
                        break
                    }
                    if (!change.pressed) break
                    change.historical.forEach { touch(it.position) }
                    touch(change.position)
                    change.consume()
                }

                when (tool) {
                    AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER ->
                        if (!cancelled && inProgress.isNotEmpty()) latestOnDraw(inProgress.toFloatArray(), tool)
                    AnnotationTool.ERASER -> if (erasing.isNotEmpty()) latestOnErase(erasing)
                }
                inProgress.clear()
                erasing = emptySet()
                eraserAt = null
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier
            .fillMaxSize()
            // Own layer: scrolling moves it without redrawing every stroke.
            .graphicsLayer()
            .then(input),
    ) {
        // Highlights first, so pen ink drawn over them stays crisp.
        strokes?.sortedBy { if (it.tool == StrokeTool.HIGHLIGHTER) 0 else 1 }?.forEach {
            if (it.id !in erasing) drawStroke(it.points, Color(it.color), it.width, highlight = it.tool == StrokeTool.HIGHLIGHTER)
        }
        if (inProgress.isNotEmpty()) {
            drawStroke(inProgress.toFloatArray(), Color(style.color), style.width * widthScale, highlight = tool == AnnotationTool.HIGHLIGHTER)
        }
        eraserAt?.let {
            drawCircle(Color(0x33000000), eraserRadius, it)
            drawCircle(Color(0x99000000), eraserRadius, it, style = DrawStroke(1.dp.toPx()))
        }
    }
}

/**
 * Draws page-fraction [points] scaled to this canvas, smoothed through segment midpoints. A
 * [highlight] is one translucent path with flat ends, so where it crosses itself it doesn't
 * get darker and the text under it stays readable.
 */
private fun DrawScope.drawStroke(points: FloatArray, color: Color, widthFraction: Float, highlight: Boolean = false) {
    val w = size.width
    val h = size.height
    val strokeWidth = (widthFraction * w).coerceAtLeast(1f)
    if (points.size < 4) {
        if (points.size == 2) drawCircle(color, strokeWidth / 2f, Offset(points[0] * w, points[1] * h))
        return
    }
    val path = Path().apply {
        moveTo(points[0] * w, points[1] * h)
        var i = 2
        while (i + 3 < points.size) {
            val x = points[i] * w
            val y = points[i + 1] * h
            quadraticTo(x, y, (x + points[i + 2] * w) / 2f, (y + points[i + 3] * h) / 2f)
            i += 2
        }
        lineTo(points[points.size - 2] * w, points[points.size - 1] * h)
    }
    drawPath(path, color, style = DrawStroke(strokeWidth, cap = if (highlight) StrokeCap.Butt else StrokeCap.Round, join = StrokeJoin.Round))
}
