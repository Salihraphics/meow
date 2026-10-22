package com.salihraphics.meow.feature.annotation

import com.salihraphics.meow.data.annotation.AnnotationRepository
import com.salihraphics.meow.domain.model.StickyNote
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** What the student is doing with the PDF: reading (no tool), drawing, highlighting, or erasing. */
enum class AnnotationTool { PEN, HIGHLIGHTER, ERASER }

data class PenStyle(val color: Int, val width: Float)

data class AnnotationToolState(
    /** Null means reading: touches scroll and zoom as usual. */
    val tool: AnnotationTool? = null,
    val colorIndex: Int = 0,
    val widthIndex: Int = 1,
    val highlightColorIndex: Int = 0,
    val highlightWidthIndex: Int = 1,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val penStyle get() = PenStyle(PEN_COLORS[colorIndex], PEN_WIDTHS[widthIndex])

    val highlightStyle get() = PenStyle(HIGHLIGHT_COLORS[highlightColorIndex], HIGHLIGHT_WIDTHS[highlightWidthIndex])

    /** The style of the line [tool] draws; null for tools that don't draw. */
    fun styleFor(tool: AnnotationTool?): PenStyle? = when (tool) {
        AnnotationTool.PEN -> penStyle
        AnnotationTool.HIGHLIGHTER -> highlightStyle
        else -> null
    }

    companion object {
        /** Black, blue, red, MEOW purple, green. */
        val PEN_COLORS = listOf(0xFF1B1A1F.toInt(), 0xFF1E5EFF.toInt(), 0xFFE0383E.toInt(), 0xFF7652F2.toInt(), 0xFF16935A.toInt())

        /** Thin, medium, thick, as fractions of page width. */
        val PEN_WIDTHS = listOf(0.0025f, 0.005f, 0.01f)

        /** Yellow and green, translucent so the printed text stays readable under them. */
        val HIGHLIGHT_COLORS = listOf(0x66FFD60A, 0x5534C759)

        /** Narrow, medium, wide bands, as fractions of page width (a line of text is about 0.02). */
        val HIGHLIGHT_WIDTHS = listOf(0.014f, 0.022f, 0.034f)
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
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val pages = HashMap<Int, MutableStateFlow<List<Stroke>?>>()
    private val notePages = HashMap<Int, MutableStateFlow<List<StickyNote>?>>()
    private val loading = HashSet<Int>()
    /** Notes hidden by delete or undo, in their last state, for bringing back. */
    private val hiddenNotes = HashMap<String, StickyNote>()
    private val history = AnnotationHistory()

    private val _tools = MutableStateFlow(AnnotationToolState())
    val tools: StateFlow<AnnotationToolState> = _tools.asStateFlow()

    private val _selectedNote = MutableStateFlow<String?>(null)

    /** The note showing its handles (move, resize, delete); null when none is. */
    val selectedNote: StateFlow<String?> = _selectedNote.asStateFlow()

    private val _editingNote = MutableStateFlow<StickyNote?>(null)

    /** The note whose text is being edited. */
    val editingNote: StateFlow<StickyNote?> = _editingNote.asStateFlow()

    /** Strokes on [pageIndex]; null until [load] has finished. */
    fun strokes(pageIndex: Int): StateFlow<List<Stroke>?> = page(pageIndex).asStateFlow()

    /** Notes on [pageIndex]; null until [load] has finished. */
    fun notes(pageIndex: Int): StateFlow<List<StickyNote>?> = notePage(pageIndex).asStateFlow()

    /** Reads a page's strokes and notes the first time it appears; later calls do nothing. */
    fun load(pageIndex: Int) {
        if (!loading.add(pageIndex)) return
        val flow = page(pageIndex)
        val notes = notePage(pageIndex)
        scope.launch {
            flow.value = repository.strokesOnPage(documentId, pageIndex)
            val saved = repository.notesOnPage(documentId, pageIndex)
            // A note can be added (from the panel) before its page has loaded; keep it, and keep
            // anything changed here meanwhile over what was read.
            notes.update { here ->
                val current = here.orEmpty()
                current + saved.filter { s -> current.none { it.id == s.id } && s.id !in hiddenNotes }
            }
        }
    }

    fun selectTool(tool: AnnotationTool?) = _tools.update { it.copy(tool = tool) }

    fun selectColor(index: Int) = _tools.update { it.copy(colorIndex = index.coerceIn(0, AnnotationToolState.PEN_COLORS.lastIndex)) }

    fun selectWidth(index: Int) = _tools.update { it.copy(widthIndex = index.coerceIn(0, AnnotationToolState.PEN_WIDTHS.lastIndex)) }

    fun selectHighlightColor(index: Int) =
        _tools.update { it.copy(highlightColorIndex = index.coerceIn(0, AnnotationToolState.HIGHLIGHT_COLORS.lastIndex)) }

    fun selectHighlightWidth(index: Int) =
        _tools.update { it.copy(highlightWidthIndex = index.coerceIn(0, AnnotationToolState.HIGHLIGHT_WIDTHS.lastIndex)) }

    /**
     * A finished line drawn with the pen or the highlighter; [points] are page fractions. Only
     * pen strokes are checked as answer marks; highlighting never records an answer.
     */
    fun addStroke(pageIndex: Int, points: FloatArray, tool: AnnotationTool = AnnotationTool.PEN) {
        if (points.size < 2) return
        val style = _tools.value.styleFor(tool) ?: return
        val kind = if (tool == AnnotationTool.HIGHLIGHTER) StrokeTool.HIGHLIGHTER else StrokeTool.PEN
        val stroke = Stroke(newId(), documentId, pageIndex, kind, style.color, style.width, points)
        page(pageIndex).update { it.orEmpty() + stroke }
        repository.add(stroke)
        record(AnnotationEdit.Added(stroke))
        if (kind == StrokeTool.PEN) onStrokeAdded(stroke)
    }

    /** A new note on [pageIndex], placed so several new ones don't stack exactly; selected and opened for typing. */
    fun addNote(pageIndex: Int): StickyNote {
        val existing = notePage(pageIndex).value.orEmpty().size
        val offset = (existing % 6) * 0.04f
        val note = StickyNote(
            newId(), documentId, pageIndex, x = 0.1f + offset, y = 0.1f + offset,
            width = StickyNote.DEFAULT_WIDTH, height = StickyNote.DEFAULT_HEIGHT, text = "", color = StickyNote.YELLOW,
        ).clamped()
        notePage(pageIndex).update { it.orEmpty() + note }
        repository.putNote(note)
        record(AnnotationEdit.NoteAdded(note))
        _selectedNote.value = note.id
        _editingNote.value = note
        return note
    }

    /** A note moved, resized or retyped; saved at once, kept inside its page. */
    fun updateNote(note: StickyNote) {
        val fixed = note.clamped()
        notePage(note.pageIndex).update { list -> list?.map { if (it.id == fixed.id) fixed else it } }
        repository.putNote(fixed)
    }

    fun deleteNote(note: StickyNote) {
        val present = notePage(note.pageIndex).value.orEmpty().firstOrNull { it.id == note.id } ?: return
        hideNote(present)
        if (_selectedNote.value == note.id) _selectedNote.value = null
        record(AnnotationEdit.NoteRemoved(present))
    }

    fun selectNote(noteId: String?) {
        _selectedNote.value = noteId
    }

    fun editNote(note: StickyNote?) {
        _editingNote.value = note
    }

    /** The strokes one eraser gesture touched. */
    fun erase(pageIndex: Int, strokeIds: Set<String>) {
        val current = page(pageIndex).value.orEmpty()
        val removed = current.filter { it.id in strokeIds }
        if (removed.isEmpty()) return
        page(pageIndex).value = current - removed.toSet()
        repository.erase(removed.map { it.id })
        record(AnnotationEdit.Erased(pageIndex, removed))
    }

    /** @return the page that changed, so the viewer can bring it into view. */
    fun undo(): Int? {
        val edit = history.undo() ?: return null
        when (edit) {
            is AnnotationEdit.Added -> hide(edit.pageIndex, listOf(edit.stroke))
            is AnnotationEdit.Erased -> show(edit.pageIndex, edit.strokes)
            is AnnotationEdit.NoteAdded -> hideNote(edit.note)
            is AnnotationEdit.NoteRemoved -> showNote(edit.note)
        }
        refreshHistoryFlags()
        return edit.pageIndex
    }

    fun redo(): Int? {
        val edit = history.redo() ?: return null
        when (edit) {
            is AnnotationEdit.Added -> show(edit.pageIndex, listOf(edit.stroke))
            is AnnotationEdit.Erased -> hide(edit.pageIndex, edit.strokes)
            is AnnotationEdit.NoteAdded -> showNote(edit.note)
            is AnnotationEdit.NoteRemoved -> hideNote(edit.note)
        }
        refreshHistoryFlags()
        return edit.pageIndex
    }

    private fun hide(pageIndex: Int, strokes: List<Stroke>) {
        val ids = strokes.map { it.id }.toSet()
        page(pageIndex).update { list -> list?.filterNot { it.id in ids } }
        repository.erase(ids.toList())
    }

    private fun show(pageIndex: Int, strokes: List<Stroke>) {
        page(pageIndex).update { list ->
            val present = list.orEmpty().map { it.id }.toSet()
            list.orEmpty() + strokes.filterNot { it.id in present }
        }
        repository.restore(strokes.map { it.id })
    }

    private fun hideNote(note: StickyNote) {
        notePage(note.pageIndex).value.orEmpty().firstOrNull { it.id == note.id }?.let { hiddenNotes[it.id] = it }
        notePage(note.pageIndex).update { list -> list?.filterNot { it.id == note.id } }
        repository.deleteNote(note.id)
    }

    /** Back as it was last seen: a note moved after being added comes back where it was moved to. */
    private fun showNote(note: StickyNote) {
        val latest = hiddenNotes.remove(note.id) ?: note
        notePage(note.pageIndex).update { list -> if (list.orEmpty().any { it.id == note.id }) list else list.orEmpty() + latest }
        repository.restoreNote(note.id)
    }

    private fun record(edit: AnnotationEdit) {
        history.record(edit)
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() = _tools.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }

    private fun page(pageIndex: Int): MutableStateFlow<List<Stroke>?> =
        pages.getOrPut(pageIndex) { MutableStateFlow(null) }

    private fun notePage(pageIndex: Int): MutableStateFlow<List<StickyNote>?> =
        notePages.getOrPut(pageIndex) { MutableStateFlow(null) }
}
