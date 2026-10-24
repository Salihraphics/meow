package com.salihraphics.meow.feature.annotation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool

/**
 * Decides which pointers draw. A stylus (and its eraser end) always does; a finger only while
 * [fingerDraws], which turns off once a stylus has been used, so a resting palm or a scrolling
 * finger never leaves a mark.
 */
object DrawingInputPolicy {
    fun draws(type: PointerType, tool: AnnotationTool?, fingerDraws: Boolean = true): Boolean = when {
        tool == null -> false
        type == PointerType.Stylus || type == PointerType.Eraser -> true
        type == PointerType.Touch || type == PointerType.Mouse -> fingerDraws
        else -> false
    }

    fun isStylus(type: PointerType) = type == PointerType.Stylus || type == PointerType.Eraser
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
        onDraw = { ink -> session.addStroke(pageIndex, ink.points, ink.tool, pressures = ink.pressures, aspect = ink.aspect) },
        onErase = { ids -> session.erase(pageIndex, ids) },
        onStylus = session::onStylusSeen,
        modifier = modifier,
    )
}

/** A finished line from [InkCanvas]: points as fractions of the box, and its shape. */
class DrawnInk(val points: FloatArray, val tool: AnnotationTool, val pressures: FloatArray?, val aspect: Float)

/**
 * Ink in a box (a page, or a note): [strokes] in fractions of the box, drawn scaled to it, and
 * pen, highlighter and eraser input while one of them is selected. Null [strokes] (not loaded
 * yet) take no input. Line widths are fractions of the box's width times [widthScale] (a note is
 * narrower than its page, and its pen should look as thick as on the page).
 *
 * Finished strokes are drawn in their own cached layer; each frame of drawing only redraws the
 * line in progress, so a page full of handwriting doesn't slow the pen down.
 */
// `historical` gives the touch samples between frames, which keeps fast strokes smooth.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InkCanvas(
    strokes: List<Stroke>?,
    tools: AnnotationToolState,
    onDraw: (DrawnInk) -> Unit,
    onErase: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    widthScale: Float = 1f,
    onStylus: () -> Unit = {},
) {
    val latestStrokes by rememberUpdatedState(strokes)
    val latestOnDraw by rememberUpdatedState(onDraw)
    val latestOnErase by rememberUpdatedState(onErase)
    val latestOnStylus by rememberUpdatedState(onStylus)

    // The stroke being drawn right now, in fractions of the box, and its pressures.
    val inProgress = remember { mutableStateListOf<Float>() }
    val inProgressPressure = remember { ArrayList<Float>() }
    var liveTool by remember { mutableStateOf<AnnotationTool?>(null) }
    var erasing by remember { mutableStateOf(emptySet<String>()) }
    var eraserAt by remember { mutableStateOf<Offset?>(null) }
    val eraserRadius = with(LocalDensity.current) { 14.dp.toPx() }
    val tool = tools.tool

    val latestFingerDraws by rememberUpdatedState(tools.fingerDraws)

    // Keyed on the tool only: "finger draws" turning off when the stylus is first seen must not
    // restart the gesture and lose that first stroke.
    val input = if (tool != null && strokes != null) {
        Modifier.pointerInput(tool) {
            awaitEachGesture {
                // The pointer drawing right now; a gesture can hold several strokes (a palm
                // resting on the page keeps the gesture going between stylus strokes).
                var owner: PointerId? = null
                var ownerIsStylus = false
                var mode: AnnotationTool = tool
                var lastPressure = 1f
                var noMoreFingerInk = false
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val aspect = h / w
                // Keep a point about every screen pixel and a half: fine enough for smooth
                // curves at any zoom, without thousands of points for a long line.
                val spacing = (1.5f / w).coerceAtMost(StrokeGeometry.MIN_POINT_SPACING)

                fun touch(position: Offset, pressure: Float) {
                    when (mode) {
                        AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> {
                            val (x, y) = StrokeGeometry.toFraction(position.x, position.y, w, h)
                            if (StrokeGeometry.shouldAppend(inProgress, x, y, aspect, spacing)) {
                                inProgress.add(x)
                                inProgress.add(y)
                                inProgressPressure.add(pressure)
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

                fun clear() {
                    inProgress.clear()
                    inProgressPressure.clear()
                    erasing = emptySet()
                    eraserAt = null
                    liveTool = null
                    owner = null
                }

                fun finish() {
                    when (mode) {
                        AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> if (inProgress.isNotEmpty()) {
                            val pressures = if (ownerIsStylus && mode == AnnotationTool.PEN) InkPressure.meaningful(inProgressPressure) else null
                            latestOnDraw(DrawnInk(inProgress.toFloatArray(), mode, pressures, aspect))
                        }
                        AnnotationTool.ERASER -> if (erasing.isNotEmpty()) latestOnErase(erasing)
                    }
                    clear()
                }

                fun start(change: PointerInputChange) {
                    clear()
                    owner = change.id
                    ownerIsStylus = DrawingInputPolicy.isStylus(change.type)
                    // The eraser end of a stylus erases whatever tool is selected.
                    mode = if (change.type == PointerType.Eraser) AnnotationTool.ERASER else tool
                    liveTool = mode
                    lastPressure = change.pressure
                    touch(change.position, change.pressure)
                    change.consume()
                }

                do {
                    val event = awaitPointerEvent()
                    val newStylus = event.changes.firstOrNull { it.changedToDown() && DrawingInputPolicy.isStylus(it.type) }
                    if (newStylus != null) latestOnStylus()
                    val current = owner?.let { id -> event.changes.firstOrNull { it.id == id } }
                    when {
                        // The stylus arriving takes over from a finger: that finger was the hand.
                        newStylus != null && (owner == null || !ownerIsStylus) && !newStylus.isConsumed -> {
                            if (owner != null) clear()
                            start(newStylus)
                        }
                        owner == null -> {
                            val down = event.changes.firstOrNull {
                                it.changedToDown() && !it.isConsumed &&
                                    DrawingInputPolicy.draws(it.type, tool, latestFingerDraws) && (DrawingInputPolicy.isStylus(it.type) || !noMoreFingerInk)
                            }
                            // A finger landing beside another one is a pinch, not a line.
                            if (down != null && (DrawingInputPolicy.isStylus(down.type) || event.changes.count { it.pressed } == 1)) start(down)
                        }
                        current == null || current.isConsumed -> {
                            // Taken over (a pinch began): the line is dropped, never half-saved.
                            clear()
                            if (!ownerIsStylus) noMoreFingerInk = true
                        }
                        // A second finger means pan/zoom; the viewer takes the gesture over. A
                        // stylus keeps writing: other touches then are a palm, and are ignored.
                        !ownerIsStylus && event.changes.count { it.pressed } > 1 -> {
                            clear()
                            noMoreFingerInk = true
                        }
                        !current.pressed -> finish()
                        else -> {
                            // Samples between frames have no pressure of their own: blend towards the new one.
                            val samples = current.historical
                            samples.forEachIndexed { i, sample ->
                                val f = (i + 1f) / (samples.size + 1f)
                                touch(sample.position, lastPressure + (current.pressure - lastPressure) * f)
                            }
                            touch(current.position, current.pressure)
                            lastPressure = current.pressure
                            current.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
                clear()
            }
        }
    } else {
        Modifier
    }

    Box(modifier.fillMaxSize().then(input)) {
        // Finished ink: cached, redrawn only when the strokes or the size change.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val hidden = erasing
                    val prepared = strokes.orEmpty()
                        .filter { it.id !in hidden }
                        .sortedBy { if (it.tool == StrokeTool.HIGHLIGHTER) 0 else 1 } // highlights under the ink
                        .map { InkRenderer.prepare(it.points, it.pressures, Color(it.color), it.width, size, it.tool == StrokeTool.HIGHLIGHTER) }
                    onDrawBehind { prepared.forEach { with(InkRenderer) { draw(it) } } }
                },
        )
        // The line being drawn, and the eraser ring.
        Canvas(Modifier.fillMaxSize()) {
            if (inProgress.isNotEmpty()) {
                val drawingTool = liveTool ?: tool
                val style = tools.styleFor(drawingTool) ?: tools.penStyle
                val live = InkRenderer.prepare(
                    inProgress.toFloatArray(), null, Color(style.color), style.width * widthScale, size, drawingTool == AnnotationTool.HIGHLIGHTER,
                )
                with(InkRenderer) { draw(live) }
            }
            eraserAt?.let {
                drawCircle(Color(0x33000000), eraserRadius, it)
                drawCircle(Color(0x99000000), eraserRadius, it, style = DrawStroke(1.dp.toPx()))
            }
        }
    }
}

/** Stylus pressure kept only when it carries information. */
object InkPressure {
    /** The per-point pressures, or null when they barely change (no real pressure sensor). */
    fun meaningful(pressures: List<Float>): FloatArray? {
        if (pressures.size < 2) return null
        val min = pressures.min()
        val max = pressures.max()
        if (max - min < 0.05f || max <= 0f) return null
        return pressures.map { it.coerceIn(0f, 1f) }.toFloatArray()
    }

    /** Line width factor for a pressure: 1 at a medium press, thinner when light, thicker when firm. */
    fun widthFactor(pressure: Float): Float = (0.55f + 0.9f * pressure.coerceIn(0f, 1f)).coerceIn(0.5f, 1.45f)
}

/**
 * Builds and draws a stroke: page fractions scaled to the box, smoothed through segment
 * midpoints. The smoothing only rounds within the last few pixels at each sample, so the line
 * keeps the shape the hand drew. A [highlight] is one translucent path with flat ends, so where
 * it crosses itself it doesn't get darker and the text under it stays readable.
 */
object InkRenderer {
    class Prepared(val paths: List<Pair<Path, Float>>, val dot: Pair<Offset, Float>?, val color: Color, val cap: StrokeCap)

    fun prepare(points: FloatArray, pressures: FloatArray?, color: Color, widthFraction: Float, size: Size, highlight: Boolean): Prepared {
        val w = size.width
        val h = size.height
        val strokeWidth = (widthFraction * w).coerceAtLeast(1f)
        val cap = if (highlight) StrokeCap.Butt else StrokeCap.Round
        if (points.size < 4) {
            val dot = if (points.size == 2) Offset(points[0] * w, points[1] * h) to strokeWidth / 2f * (pressures?.firstOrNull()?.let(InkPressure::widthFactor) ?: 1f) else null
            return Prepared(emptyList(), dot, color, cap)
        }
        val n = points.size / 2
        fun px(i: Int) = points[2 * i] * w
        fun py(i: Int) = points[2 * i + 1] * h
        if (pressures == null || pressures.size != n || highlight) {
            val path = Path().apply {
                moveTo(px(0), py(0))
                for (i in 1 until n - 1) quadraticTo(px(i), py(i), (px(i) + px(i + 1)) / 2f, (py(i) + py(i + 1)) / 2f)
                lineTo(px(n - 1), py(n - 1))
            }
            return Prepared(listOf(path to strokeWidth), null, color, cap)
        }
        // With pressure: the same smoothed curve, cut at each sample, each piece as wide as
        // pressed there. Round caps overlap and hide the joins.
        val pieces = ArrayList<Pair<Path, Float>>(n)
        var startX = px(0)
        var startY = py(0)
        for (i in 1 until n - 1) {
            val endX = (px(i) + px(i + 1)) / 2f
            val endY = (py(i) + py(i + 1)) / 2f
            val piece = Path().apply { moveTo(startX, startY); quadraticTo(px(i), py(i), endX, endY) }
            pieces += piece to strokeWidth * InkPressure.widthFactor(pressures[i])
            startX = endX
            startY = endY
        }
        pieces += Path().apply { moveTo(startX, startY); lineTo(px(n - 1), py(n - 1)) } to strokeWidth * InkPressure.widthFactor(pressures[n - 1])
        return Prepared(pieces, null, color, cap)
    }

    fun DrawScope.draw(p: Prepared) {
        p.dot?.let { (at, r) -> drawCircle(p.color, r, at) }
        p.paths.forEach { (path, width) -> drawPath(path, p.color, style = DrawStroke(width, cap = p.cap, join = StrokeJoin.Round)) }
    }
}
