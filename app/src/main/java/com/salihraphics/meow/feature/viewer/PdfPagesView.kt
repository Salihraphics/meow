package com.salihraphics.meow.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where the reader is, in layout-independent terms, so it survives rotation and window resizes
 * (pixel offsets would not: pages change size when the width changes).
 */
@Immutable
data class ViewportSnapshot(
    /** First page at least partly visible at the top of the screen. */
    val pageIndex: Int,
    /** How far that page is scrolled past the top, as a fraction of its height (0..1). */
    val pageOffsetFraction: Float = 0f,
    val zoom: Float = 1f,
    /** Sideways pan when zoomed, as a fraction of the scrollable range (0..1). */
    val horizontalFraction: Float = 0f,
)

/** Scroll position, zoom and current page of a PDF being read. */
@Stable
class PdfViewportState(initial: ViewportSnapshot) {
    val list = LazyListState(firstVisibleItemIndex = initial.pageIndex.coerceAtLeast(0))
    val horizontal = ScrollState(0)

    /** Committed zoom; pages are laid out and rendered at this scale. */
    var zoom by mutableFloatStateOf(initial.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set

    /** Applied by [restore] once the first layout has measured the target page. */
    private var pendingRestore: ViewportSnapshot? = initial

    /** False until [restore] has finished; snapshots taken before then would be half-restored. */
    var isRestored by mutableStateOf(false)
        private set

    /**
     * The live pinch, shown as a transform of the whole viewport (screen = scale × point +
     * translation) until the fingers lift and [commitPreview] lays the pages out at the new zoom.
     */
    internal var previewScale by mutableFloatStateOf(1f)
    internal var previewTranslation by mutableStateOf(Offset.Zero)

    /** Where the fingers are: the spot under them is the one kept in place when the pinch is laid out. */
    private var previewFocus = Offset.Zero
    internal var viewportSize by mutableStateOf(IntSize.Zero)

    /**
     * A scroll position asked for but not on screen yet (content pixels at the current zoom).
     * Until the list and the sideways scroll have caught up, it is the truth: the sideways
     * part is shown through a layer offset in the very same frame, so a zoom never shows one
     * frame at the old horizontal position.
     */
    internal var pending by mutableStateOf<ScrollPosition?>(null)

    /** Uptime until which a second tap is not a double-tap (the first one stopped a fling). */
    internal var doubleTapBlockedUntil = 0L
    internal var lastDownUptime = 0L

    /** How pages are measured right now; set by [PdfPagesView] on every layout. */
    internal var geometry: Geometry? = null

    internal data class Geometry(val viewportWidth: Int, val maxPageWidth: Int, val margin: Int, val pageCount: Int, val shownAspect: (Int) -> Float)

    /** The page under the middle of the screen. */
    val currentPage: Int by derivedStateOf {
        val info = list.layoutInfo
        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.firstOrNull { it.offset <= middle && it.offset + it.size > middle }?.index
            ?: list.firstVisibleItemIndex
    }

    internal fun layoutAt(zoom: Float): PdfLayout? = geometry?.let {
        PdfLayout(it.viewportWidth, it.maxPageWidth, it.margin, zoom, it.pageCount, it.shownAspect)
    }

    /** Where the list is now, in content pixels of the current zoom. */
    internal fun scrollPosition(layout: PdfLayout): ScrollPosition =
        pending ?: ScrollPosition(layout.scrollY(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset).toFloat(), horizontal.value.toFloat())

    /** The current position, or null while the list has not been laid out yet. */
    fun snapshot(): ViewportSnapshot? {
        if (pending != null) return null
        val info = list.layoutInfo
        val index = list.firstVisibleItemIndex
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        if (item.size <= 0) return null
        val maxH = horizontal.maxValue
        return ViewportSnapshot(
            pageIndex = index,
            pageOffsetFraction = (list.firstVisibleItemScrollOffset.toFloat() / item.size).coerceIn(0f, 1f),
            zoom = zoom,
            horizontalFraction = if (maxH > 0) horizontal.value.toFloat() / maxH else 0f,
        )
    }

    /**
     * Re-applies the position this state was created with, in the current layout: waits for the
     * target page to be measured, then converts the fractions back to pixels.
     */
    suspend fun restore() {
        val target = pendingRestore
        if (target != null) {
            val page = target.pageIndex.coerceAtLeast(0)
            val size = snapshotFlow {
                list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == page }?.size
                    ?: if (list.layoutInfo.totalItemsCount > 0 && page >= list.layoutInfo.totalItemsCount) -1 else 0
            }.first { it != 0 }
            if (size > 0) {
                list.scrollToItem(page, (target.pageOffsetFraction * size).roundToInt())
                val layout = layoutAt(zoom)
                if (target.horizontalFraction > 0f && layout != null) {
                    val y = layout.scrollY(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset).toFloat()
                    pending = ScrollPosition(y, target.horizontalFraction * layout.maxScrollX)
                    snapshotFlow { pending }.first { it == null }
                }
            }
            pendingRestore = null
        }
        isRestored = true
    }

    /**
     * Lays the pages out at [target] zoom (within the limits) so that the document spot shown at
     * viewport point [from] ends up at [to]. Everything changes in one go, before the next frame:
     * the zoom, the list position (applied when the list is next measured, i.e. with the new page
     * sizes) and the sideways scroll (shown through [pending] until the scroll catches up).
     */
    fun commitZoom(target: Float, from: Offset, to: Offset = from) {
        val newZoom = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val old = layoutAt(zoom)
        val new = layoutAt(newZoom)
        if (old == null || new == null || viewportSize.height <= 0) {
            zoom = newZoom
            return
        }
        val scroll = ZoomMath.anchor(old, scrollPosition(old), from, new, to, viewportSize.height)
        moveTo(new, newZoom, scroll)
    }

    private fun moveTo(layout: PdfLayout, newZoom: Float, scroll: ScrollPosition) {
        val (index, offset) = layout.listPosition(scroll.y.roundToInt())
        Snapshot.withMutableSnapshot {
            previewScale = 1f
            previewTranslation = Offset.Zero
            zoom = newZoom
            pending = scroll
            list.requestScrollToItem(index, offset)
        }
    }

    /** Zooms keeping the content under [pivot] (viewport coordinates) in place. */
    suspend fun zoomTo(target: Float, pivot: Offset = viewportCentre()) = commitZoom(target, pivot)

    fun isPageVisible(index: Int): Boolean = list.layoutInfo.visibleItemsInfo.any { it.index == index }

    suspend fun zoomIn() = zoomTo(zoom * ZOOM_STEP)

    suspend fun zoomOut() = zoomTo(zoom / ZOOM_STEP)

    suspend fun goToPage(index: Int) {
        val target = index.coerceAtLeast(0)
        // Animating across many pages renders every page in between; jump instead.
        if (abs(target - currentPage) > 3) list.scrollToItem(target) else list.animateScrollToItem(target)
    }

    /** Folds one step of a pinch/pan into the preview, keeping the result inside the zoom limits. */
    internal fun previewBy(step: TouchAction.Transform) {
        val wanted = previewScale * step.zoom
        val scale = (zoom * wanted).coerceIn(MIN_ZOOM, MAX_ZOOM) / zoom
        val factor = scale / previewScale
        // screen' = factor × (screen − from) + to
        previewTranslation = (previewTranslation - step.from) * factor + step.to
        previewScale = scale
        // Where the scaling happened; a pan or a finger leaving doesn't move it.
        if (step.zoom != 1f) previewFocus = step.to
    }

    /** The fingers lifted: lay out at the previewed zoom with the same spot under the same place. */
    internal fun commitPreview() {
        val s = previewScale
        val t = previewTranslation
        if (abs(s - 1f) < 0.001f && t.getDistance() < 0.5f) {
            previewScale = 1f
            previewTranslation = Offset.Zero
            return
        }
        // Anchored at the fingers: pages and gaps scale differently once laid out, so only the
        // spot under the fingers is guaranteed to stay exactly where the preview showed it.
        val focus = if (abs(s - 1f) < 0.001f) viewportCentre() else previewFocus
        commitZoom(zoom * s, (focus - t) / s, focus)
    }

    private fun viewportCentre() = Offset(viewportSize.width / 2f, viewportSize.height / 2f)

    companion object {
        /** Down to half size, to see a whole page (or two) at once on a small screen. */
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 4f
        const val ZOOM_STEP = 1.25f
    }
}

/**
 * A viewport that starts at [initial] and reports every settled change to [onChanged], so the
 * owner (the ViewModel) can hand it back after the activity is recreated.
 */
@Composable
fun rememberPdfViewportState(
    key: Any,
    initial: () -> ViewportSnapshot,
    onChanged: (ViewportSnapshot) -> Unit,
): PdfViewportState {
    val state = remember(key) { PdfViewportState(initial()) }
    val latestOnChanged by rememberUpdatedState(onChanged)
    LaunchedEffect(state) {
        state.restore()
        snapshotFlow { state.snapshot() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { latestOnChanged(it) }
    }
    return state
}

/**
 * The PDF pages in a vertically scrolling list. Only pages near the screen are rendered.
 * [pageOverlay] is drawn on top of each page, in that page's bounds; drawing and answer marks
 * plug in there.
 *
 * Who handles a touch is decided by [TouchArbiter]: one finger scrolls (or draws, when
 * [fingerDraws]); two fingers pinch and pan; a resting thumb is ignored; with a stylus writing,
 * the palm does nothing.
 */
@Composable
fun PdfPagesView(
    loader: PdfPageLoader,
    state: PdfViewportState,
    modifier: Modifier = Modifier,
    /** At 100% pages fit the width, up to this; wide tablets would otherwise show one giant page. */
    maxPageWidth: Dp = 760.dp,
    /**
     * While true, [pageOverlay] draws: a stylus always, a finger when [fingerDraws]. Two fingers
     * pan and zoom whatever the tool.
     */
    drawingActive: Boolean = false,
    /** With a drawing tool, whether one finger draws (no stylus) or scrolls (stylus in use). */
    fingerDraws: Boolean = true,
    /** How the pages are turned, degrees clockwise; the overlay turns with them. */
    rotation: Int = 0,
    /** A single tap on the pages that nothing on them took (e.g. not on a note). */
    onTap: () -> Unit = {},
    pageOverlay: @Composable BoxScope.(pageIndex: Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val drawing by rememberUpdatedState(drawingActive)
    val latestOnTap by rememberUpdatedState(onTap)
    val density = LocalDensity.current
    val fling = ScrollableDefaults.flingBehavior()
    val latestFling by rememberUpdatedState(fling)
    // One finger scrolls unless it is the pen.
    val fingerScrolls = !drawingActive || !fingerDraws

    // Settle the sideways part of a committed zoom or restore once the wider layout exists.
    LaunchedEffect(state) {
        snapshotFlow { state.pending }.collectLatest { target ->
            if (target == null) return@collectLatest
            // Two frames: the first recomposes and measures at the new zoom, the second sees it.
            withFrameNanos { }
            withFrameNanos { }
            state.horizontal.scrollTo(target.x.roundToInt().coerceIn(0, state.horizontal.maxValue))
            state.pending = null
        }
    }
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .onSizeChanged { state.viewportSize = it }
            .pointerInput(state) {
                val arbiter = TouchArbiter(viewConfiguration.touchSlop, palmReach = 220.dp.toPx())
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.changedToDown() }?.let { down ->
                            if (state.list.isScrollInProgress || state.horizontal.isScrollInProgress) {
                                state.doubleTapBlockedUntil = down.uptimeMillis + DOUBLE_TAP_BLOCK_MS
                            }
                            state.lastDownUptime = down.uptimeMillis
                        }
                        val samples = event.changes.map {
                            TouchSample(it.id.value, it.position, it.pressed, it.type == PointerType.Stylus || it.type == PointerType.Eraser, it.uptimeMillis)
                        }
                        when (val action = arbiter.onEvent(samples, drawing)) {
                            TouchAction.Pass -> Unit
                            TouchAction.Block -> event.changes.forEach { if (it.type == PointerType.Touch) it.consume() }
                            is TouchAction.Transform -> {
                                state.previewBy(action)
                                event.changes.forEach { it.consume() }
                            }
                            TouchAction.EndTransform -> {
                                event.changes.forEach { it.consume() }
                                state.commitPreview()
                            }
                            is TouchAction.Pan -> {
                                event.changes.forEach { it.consume() }
                                state.list.dispatchRawDelta(-action.delta.y)
                                if (state.zoom > 1f) state.horizontal.dispatchRawDelta(-action.delta.x)
                            }
                            is TouchAction.EndPan -> {
                                event.changes.forEach { it.consume() }
                                scope.launch { state.list.scroll { with(latestFling) { performFling(-action.velocity.y) } } }
                            }
                        }
                    }
                }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onTap = { latestOnTap() },
                    onDoubleTap = { at ->
                        // While drawing, two quick taps are two dots, not a zoom.
                        if (!drawing && state.lastDownUptime >= state.doubleTapBlockedUntil) {
                            state.commitZoom(if (state.zoom > 1.2f) 1f else 2f, at)
                        }
                    },
                )
            },
    ) {
        val marginPx = with(density) { 12.dp.roundToPx() }
        val margin = with(density) { marginPx.toDp() }
        val turn = PageRotation.normalize(rotation)
        val geometry = PdfViewportState.Geometry(
            viewportWidth = constraints.maxWidth,
            maxPageWidth = with(density) { maxPageWidth.roundToPx() },
            margin = marginPx,
            pageCount = loader.pageCount,
            shownAspect = { PageRotation.shownAspect(loader.aspectRatio(it), turn) },
        )
        state.geometry = geometry
        val layout = remember(geometry.viewportWidth, geometry.maxPageWidth, marginPx, loader, turn, state.zoom) {
            state.layoutAt(state.zoom)!!
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The live pinch: the whole viewport scaled and moved with the fingers.
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = state.previewScale
                    scaleY = state.previewScale
                    translationX = state.previewTranslation.x
                    translationY = state.previewTranslation.y
                }
                .horizontalScroll(state.horizontal, enabled = state.zoom > 1f && fingerScrolls),
        ) {
            LazyColumn(
                state = state.list,
                userScrollEnabled = fingerScrolls,
                contentPadding = PaddingValues(horizontal = margin, vertical = margin),
                verticalArrangement = Arrangement.spacedBy(margin),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .exactWidth(layout.listWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // Shows a just-committed sideways position before the scroll reaches it.
                        val target = state.pending
                        translationX = if (target != null) state.horizontal.value - target.x else 0f
                    },
            ) {
                items(count = loader.pageCount, key = { it }) { index ->
                    // A quarter-turned page is laid out on its side: its own width is the shown height.
                    val ownWidth = if (PageRotation.isQuarterTurn(turn)) layout.pageHeight(index).toFloat() else layout.pageWidth.toFloat()
                    PdfPage(index, loader, renderWidthFor(ownWidth), turn, Modifier.exactSize(layout.pageWidth, layout.pageHeight(index)), pageOverlay)
                }
            }
        }
    }
}

/** A tap within this long after one that stopped a fling never completes a double-tap. */
private const val DOUBLE_TAP_BLOCK_MS = 600L

/** Exactly [width] pixels wide, whatever the parent offers (a horizontal scroll offers infinity). */
private fun Modifier.exactWidth(width: Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(width, placeable.height) { placeable.place(0, 0) }
}

/** The size [PdfLayout] computed, to the pixel, so zoom maths and the screen always agree. */
private fun Modifier.exactSize(width: Int, height: Int) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

@Composable
private fun PdfPage(
    index: Int,
    loader: PdfPageLoader,
    renderWidth: Int,
    rotation: Int,
    modifier: Modifier,
    overlay: @Composable BoxScope.(Int) -> Unit,
) {
    // Keeps the previous bitmap on screen while a sharper one renders after zooming.
    var bitmap by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index, renderWidth) {
        try {
            bitmap = loader.load(index, renderWidth).also { it.prepareToDraw() }
            failed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (bitmap == null) failed = true
        } catch (e: OutOfMemoryError) {
            if (bitmap == null) failed = true
        }
    }
    val description = stringResource(R.string.viewer_page_cd, index + 1, loader.pageCount)
    Box(
        modifier
            .shadow(1.dp)
            .background(Color.White)
            .semantics { contentDescription = description },
    ) {
        // The page as printed, turned as a whole: picture, ink and notes together.
        Box(Modifier.fillMaxSize().turned(rotation), contentAlignment = Alignment.Center) {
            PageContent(index, bitmap, failed, overlay)
        }
    }
}

@Composable
private fun BoxScope.PageContent(index: Int, bitmap: ImageBitmap?, failed: Boolean, overlay: @Composable BoxScope.(Int) -> Unit) {
    val current = bitmap
    when {
        current != null -> Image(current, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        failed -> Text(
            stringResource(R.string.viewer_page_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5C5862),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
        else -> CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFF7652F2), strokeWidth = 3.dp)
    }
    overlay(index)
}
