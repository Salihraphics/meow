package com.salihraphics.meow.feature.annotation

import com.salihraphics.meow.domain.model.StickyNote
import com.salihraphics.meow.domain.model.Stroke

/** One undoable step. Every kind only toggles visibility, so undo/redo are exact. */
sealed interface AnnotationEdit {
    val pageIndex: Int

    data class Added(val stroke: Stroke) : AnnotationEdit {
        override val pageIndex get() = stroke.pageIndex
    }

    /** One eraser gesture, which may remove several strokes on one page. */
    data class Erased(override val pageIndex: Int, val strokes: List<Stroke>) : AnnotationEdit

    data class NoteAdded(val note: StickyNote) : AnnotationEdit {
        override val pageIndex get() = note.pageIndex
    }

    data class NoteRemoved(val note: StickyNote) : AnnotationEdit {
        override val pageIndex get() = note.pageIndex
    }
}

/**
 * Chronological undo/redo for one open document. Bounded so a long session can't grow without
 * limit; the oldest steps fall off first.
 */
class AnnotationHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undoStack = ArrayDeque<AnnotationEdit>()
    private val redoStack = ArrayDeque<AnnotationEdit>()

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** A new edit invalidates anything that was undone. */
    fun record(edit: AnnotationEdit) {
        undoStack.addLast(edit)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(): AnnotationEdit? = undoStack.removeLastOrNull()?.also { redoStack.addLast(it) }

    fun redo(): AnnotationEdit? = redoStack.removeLastOrNull()?.also { undoStack.addLast(it) }

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
