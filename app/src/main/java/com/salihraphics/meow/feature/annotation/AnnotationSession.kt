package com.salihraphics.meow.feature.annotation

import com.salihraphics.meow.data.annotation.AnnotationRepository
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** What the student is doing with the PDF: reading (no tool), drawing, or erasing. */
enum class AnnotationTool { PEN, ERASER }

data class PenStyle(val color: Int, val width: Float)

data class AnnotationToolState(
    /** Null means reading: touches scroll and zoom as usual. */
    val tool: AnnotationTool? = null,
    val colorIndex: Int = 0,
    val widthIndex: Int = 1,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val penStyle get() = PenStyle(PEN_COLORS[colorIndex], PEN_WIDTHS[widthIndex])

    companion object {
        /** Black, blue, red, MEOW purple, green. */
        val PEN_COLORS = listOf(0xFF1B1A1F.toInt(), 0xFF1E5EFF.toInt(), 0xFFE0383E.toInt(), 0xFF7652F2.toInt(), 0xFF16935A.toInt())

        /** Thin, medium, thick, as fractions of page width. */
        val PEN_WIDTHS = listOf(0.0025f, 0.005f, 0.01f)
    }
}

/**
 * The annotations of one open document: strokes per page (loaded when a page first appears),
 * the current tool, and undo/redo. Lives in the viewer's ViewModel, so rotation keeps all of it.
 * Every change is written through [repository] immediately.
 */
class AnnotationSession(
    private val documentId: String,
    private val repository: AnnotationRepository,
    private val scope: CoroutineScope,
    /** Told about each finished pen stroke, e.g. to check whether it circles an answer. */
    private val onStrokeAdded: (Stroke) -> Unit = {},
    /** Strokes taken off the page (erased or undone), and put back (undo of an erase, redo). */
    private val onStrokesRemoved: (Collection<String>) -> Unit = {},
    private val onStrokesRestored: (Collection<String>) -> Unit = {},
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val pages = HashMap<Int, MutableStateFlow<List<Stroke>?>>()
    private val loading = HashSet<Int>()
    private val history = AnnotationHistory()

    private val _tools = MutableStateFlow(AnnotationToolState())
    val tools: StateFlow<AnnotationToolState> = _tools.asStateFlow()

    /** Strokes on [pageIndex]; null until [load] has finished. */
    fun strokes(pageIndex: Int): StateFlow<List<Stroke>?> = page(pageIndex).asStateFlow()

    /** Reads a page's strokes the first time it appears; later calls do nothing. */
    fun load(pageIndex: Int) {
        if (!loading.add(pageIndex)) return
        val flow = page(pageIndex)
        scope.launch { flow.value = repository.strokesOnPage(documentId, pageIndex) }
    }

    fun selectTool(tool: AnnotationTool?) = _tools.update { it.copy(tool = tool) }

    fun selectColor(index: Int) = _tools.update { it.copy(colorIndex = index.coerceIn(0, AnnotationToolState.PEN_COLORS.lastIndex)) }

    fun selectWidth(index: Int) = _tools.update { it.copy(widthIndex = index.coerceIn(0, AnnotationToolState.PEN_WIDTHS.lastIndex)) }

    /** A finished pen stroke; [points] are page fractions. */
    fun addStroke(pageIndex: Int, points: FloatArray) {
        if (points.size < 2) return
        val style = _tools.value.penStyle
        val stroke = Stroke(newId(), documentId, pageIndex, StrokeTool.PEN, style.color, style.width, points)
        page(pageIndex).update { it.orEmpty() + stroke }
        repository.add(stroke)
        record(AnnotationEdit.Added(stroke))
        onStrokeAdded(stroke)
    }

    /** The strokes one eraser gesture touched. */
    fun erase(pageIndex: Int, strokeIds: Set<String>) {
        val current = page(pageIndex).value.orEmpty()
        val removed = current.filter { it.id in strokeIds }
        if (removed.isEmpty()) return
        page(pageIndex).value = current - removed.toSet()
        repository.erase(removed.map { it.id })
        onStrokesRemoved(removed.map { it.id })
        record(AnnotationEdit.Erased(pageIndex, removed))
    }

    /** @return the page that changed, so the viewer can bring it into view. */
    fun undo(): Int? {
        val edit = history.undo() ?: return null
        when (edit) {
            is AnnotationEdit.Added -> hide(edit.pageIndex, listOf(edit.stroke))
            is AnnotationEdit.Erased -> show(edit.pageIndex, edit.strokes)
        }
        refreshHistoryFlags()
        return edit.pageIndex
    }

    fun redo(): Int? {
        val edit = history.redo() ?: return null
        when (edit) {
            is AnnotationEdit.Added -> show(edit.pageIndex, listOf(edit.stroke))
            is AnnotationEdit.Erased -> hide(edit.pageIndex, edit.strokes)
        }
        refreshHistoryFlags()
        return edit.pageIndex
    }

    private fun hide(pageIndex: Int, strokes: List<Stroke>) {
        val ids = strokes.map { it.id }.toSet()
        page(pageIndex).update { list -> list?.filterNot { it.id in ids } }
        repository.erase(ids.toList())
        onStrokesRemoved(ids)
    }

    private fun show(pageIndex: Int, strokes: List<Stroke>) {
        page(pageIndex).update { list ->
            val present = list.orEmpty().map { it.id }.toSet()
            list.orEmpty() + strokes.filterNot { it.id in present }
        }
        repository.restore(strokes.map { it.id })
        onStrokesRestored(strokes.map { it.id })
    }

    private fun record(edit: AnnotationEdit) {
        history.record(edit)
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() = _tools.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }

    private fun page(pageIndex: Int): MutableStateFlow<List<Stroke>?> =
        pages.getOrPut(pageIndex) { MutableStateFlow(null) }
}
