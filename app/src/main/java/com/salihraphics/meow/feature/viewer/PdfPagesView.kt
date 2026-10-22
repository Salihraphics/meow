package com.salihraphics.meow.feature.viewer

import androidx.compose.foundation.Image
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

    internal var pinchScale by mutableFloatStateOf(1f)
    internal var pinchPivot by mutableStateOf(Offset.Zero)
    internal var viewportSize by mutableStateOf(IntSize.Zero)

    /** The page under the middle of the screen. */
    val currentPage: Int by derivedStateOf {
        val info = list.layoutInfo
        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.firstOrNull { it.offset <= middle && it.offset + it.size > middle }?.index
            ?: list.firstVisibleItemIndex
    }

    /** The current position, or null while the list has not been laid out yet. */
    fun snapshot(): ViewportSnapshot? {
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
                if (target.horizontalFraction > 0f) {
                    // One frame for the zoomed width to be laid out before panning into it.
                    withFrameNanos { }
                    horizontal.scrollTo((target.horizontalFraction * horizontal.maxValue).roundToInt())
                }
            }
            pendingRestore = null
        }
        isRestored = true
    }

    /** Zooms keeping the content under [pivot] (viewport coordinates) in place. */
    suspend fun zoomTo(target: Float, pivot: Offset = viewportCentre()) {
        val newZoom = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val factor = newZoom / zoom
        if (abs(factor - 1f) < 0.01f) return
        val firstIndex = list.firstVisibleItemIndex
        val firstOffset = list.firstVisibleItemScrollOffset
        val horizontalTarget = ((horizontal.value + pivot.x) * factor - pivot.x).roundToInt()
        zoom = newZoom
        list.scrollToItem(firstIndex, ((firstOffset + pivot.y) * factor - pivot.y).roundToInt().coerceAtLeast(0))
        // Wait one frame so the wider layout exists before scrolling sideways into it.
        withFrameNanos { }
        horizontal.scrollTo(horizontalTarget.coerceIn(0, horizontal.maxValue))
    }

    fun isPageVisible(index: Int): Boolean = list.layoutInfo.visibleItemsInfo.any { it.index == index }

    suspend fun zoomIn() = zoomTo(zoom * ZOOM_STEP)

    suspend fun zoomOut() = zoomTo(zoom / ZOOM_STEP)

    suspend fun goToPage(index: Int) {
        val target = index.coerceAtLeast(0)
        // Animating across many pages renders every page in between; jump instead.
        if (abs(target - currentPage) > 3) list.scrollToItem(target) else list.animateScrollToItem(target)
    }

    internal fun previewPinch(scale: Float, pivot: Offset) {
        pinchScale = (zoom * scale).coerceIn(MIN_ZOOM, MAX_ZOOM) / zoom
        pinchPivot = pivot
    }

    internal suspend fun commitPinch() {
        val target = zoom * pinchScale
        val pivot = pinchPivot
        pinchScale = 1f
        zoomTo(target, pivot)
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
 * will plug in there.
 */
@Composable
fun PdfPagesView(
    loader: PdfPageLoader,
    state: PdfViewportState,
    modifier: Modifier = Modifier,
    /** At 100% pages fit the width, up to this; wide tablets would otherwise show one giant page. */
    maxPageWidth: Dp = 760.dp,
    /**
     * While true, one finger belongs to [pageOverlay] (drawing), and two fingers pan as well as
     * zoom. While false, everything behaves as a plain reader.
     */
    drawingActive: Boolean = false,
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
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .onSizeChanged { state.viewportSize = it }
            .pointerInput(state) {
                // Two fingers zoom (and pan while drawing); one finger is left to the list or the pen.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var scale = 1f
                    var pinching = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinching = true
                            scale *= event.calculateZoom()
                            state.previewPinch(scale, event.calculateCentroid(useCurrent = true))
                            if (drawing) {
                                val pan = event.calculatePan()
                                state.list.dispatchRawDelta(-pan.y)
                                state.horizontal.dispatchRawDelta(-pan.x)
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pinching) {
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinching) scope.launch { state.commitPinch() }
                }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onTap = { latestOnTap() },
                    onDoubleTap = { at ->
                        // While drawing, two quick taps are two dots, not a zoom.
                        if (!drawing) scope.launch { state.zoomTo(if (state.zoom > 1.2f) 1f else 2f, at) }
                    },
                )
            },
    ) {
        val margin = 12.dp
        val marginPx = with(density) { margin.toPx() }
        val viewportPx = constraints.maxWidth.toFloat()
        val baseWidthPx = minOf(viewportPx, with(density) { maxPageWidth.toPx() } + marginPx * 2)
        val contentWidthPx = baseWidthPx * state.zoom
        val pageWidthPx = contentWidthPx - marginPx * 2
        // A quarter-turned page is laid out on its side: its own width is the shown height.
        val turn = PageRotation.normalize(rotation)
        Box(Modifier.fillMaxSize().horizontalScroll(state.horizontal, enabled = state.zoom > 1f && !drawingActive)) {
            LazyColumn(
                state = state.list,
                userScrollEnabled = !drawingActive,
                contentPadding = PaddingValues(horizontal = margin, vertical = margin),
                verticalArrangement = Arrangement.spacedBy(margin),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    // Never narrower than the screen, so small pages sit centred.
                    .width(with(density) { maxOf(contentWidthPx, viewportPx).toDp() })
                    .fillMaxHeight()
                    .graphicsLayer {
                        val s = state.pinchScale
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(
                            ((state.horizontal.value + state.pinchPivot.x) / size.width).coerceIn(0f, 1f),
                            (state.pinchPivot.y / size.height).coerceIn(0f, 1f),
                        )
                    },
            ) {
                items(count = loader.pageCount, key = { it }) { index ->
                    val ownWidth = if (PageRotation.isQuarterTurn(turn)) pageWidthPx * loader.aspectRatio(index) else pageWidthPx
                    PdfPage(index, loader, renderWidthFor(ownWidth), turn, Modifier.width(with(density) { pageWidthPx.toDp() }), pageOverlay)
                }
            }
        }
    }
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
            bitmap = loader.load(index, renderWidth)
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
            .aspectRatio(PageRotation.shownAspect(loader.aspectRatio(index), rotation))
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
