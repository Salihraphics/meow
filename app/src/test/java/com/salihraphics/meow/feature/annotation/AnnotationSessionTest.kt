package com.salihraphics.meow.feature.annotation

import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import com.salihraphics.meow.testing.FakeAnnotationRepository
import com.salihraphics.meow.testing.line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationSessionTest {
    private val repo = FakeAnnotationRepository()
    private var ids = 0
    private fun session(doc: String = "doc") =
        AnnotationSession(doc, repo, CoroutineScope(Dispatchers.Unconfined)) { "s${ids++}" }

    private fun AnnotationSession.idsOn(page: Int): List<String> {
        load(page)
        return strokes(page).value.orEmpty().map { it.id }
    }

    @Test
    fun drawingSavesWithTheChosenStyle() {
        val s = session()
        s.selectTool(AnnotationTool.PEN)
        s.selectColor(2)
        s.selectWidth(2)
        s.addStroke(0, line(0.1f, 0.1f, 0.2f, 0.2f))
        val saved = repo.all.values.single()
        assertEquals(AnnotationToolState.PEN_COLORS[2], saved.color)
        assertEquals(AnnotationToolState.PEN_WIDTHS[2], saved.width)
        assertEquals(listOf("s0"), s.idsOn(0))
    }

    @Test
    fun pagesAreIsolated() {
        val s = session()
        s.addStroke(0, line(0.1f, 0.1f))
        s.addStroke(1, line(0.5f, 0.5f))
        assertEquals(listOf("s0"), s.idsOn(0))
        assertEquals(listOf("s1"), s.idsOn(1))
        s.erase(0, setOf("s1")) // not on page 0: nothing happens
        assertEquals(listOf("s1"), s.idsOn(1))
        assertFalse("s1" in repo.deleted)
    }

    @Test
    fun undoAndRedoAreExactAndChronological() {
        val s = session()
        s.addStroke(0, line(0.1f, 0.1f))
        s.addStroke(1, line(0.2f, 0.2f))
        s.erase(0, setOf("s0"))
        assertTrue(s.idsOn(0).isEmpty())

        assertEquals(0, s.undo()) // erase undone
        assertEquals(listOf("s0"), s.idsOn(0))
        assertEquals(1, s.undo()) // second stroke undone
        assertTrue(s.idsOn(1).isEmpty())
        assertTrue("s1" in repo.deleted)

        assertEquals(1, s.redo())
        assertEquals(listOf("s1"), s.idsOn(1))
        assertFalse("s1" in repo.deleted)
        assertTrue(s.tools.value.canRedo)

        s.addStroke(2, line(0.3f, 0.3f)) // a new edit clears redo
        assertFalse(s.tools.value.canRedo)
        assertNull(s.redo())
    }

    @Test
    fun undoingEverythingThenNothingLeft() {
        val s = session()
        s.addStroke(0, line(0.1f, 0.1f))
        s.undo()
        assertFalse(s.tools.value.canUndo)
        assertNull(s.undo())
    }

    @Test
    fun reopeningTheDocumentLoadsWhatWasSaved() {
        session().apply {
            addStroke(4, line(0.1f, 0.1f, 0.3f, 0.3f))
            addStroke(4, line(0.5f, 0.5f))
            erase(4, setOf("s1"))
        }
        val reopened = session()
        assertEquals(listOf("s0"), reopened.idsOn(4))
        assertFalse(reopened.tools.value.canUndo) // history belongs to the session that made it
    }

    @Test
    fun historyIsBounded() {
        val history = AnnotationHistory(limit = 3)
        repeat(5) { history.record(AnnotationEdit.Added(Stroke("$it", "d", 0, StrokeTool.PEN, 0, 0f, floatArrayOf(0f, 0f)))) }
        var undone = 0
        while (history.undo() != null) undone++
        assertEquals(3, undone)
    }

    @Test
    fun highlightsAreStoredAsHighlighterStrokesAndNeverCheckedAsAnswers() {
        val checked = mutableListOf<Stroke>()
        val s = AnnotationSession("doc", repo, CoroutineScope(Dispatchers.Unconfined), onStrokeAdded = { checked += it }) { "s${ids++}" }
        s.selectHighlightColor(1)
        s.selectHighlightWidth(0)
        s.addStroke(0, line(0.1f, 0.5f, 0.9f, 0.5f), AnnotationTool.HIGHLIGHTER)
        s.addStroke(0, line(0.1f, 0.6f, 0.2f, 0.6f), AnnotationTool.PEN)
        val (highlight, pen) = repo.all.values.toList()
        assertEquals(StrokeTool.HIGHLIGHTER, highlight.tool)
        assertEquals(AnnotationToolState.HIGHLIGHT_COLORS[1], highlight.color)
        assertEquals(AnnotationToolState.HIGHLIGHT_WIDTHS[0], highlight.width)
        // Translucent, so the printed text stays readable.
        assertTrue((highlight.color ushr 24) in 0x30..0x90)
        assertEquals(listOf(pen), checked)
    }

    @Test
    fun notesAreAddedOnTheirPageEditedAndUndoneLikeStrokes() {
        val s = session()
        s.load(3)
        val note = s.addNote(3)
        assertEquals(3, note.pageIndex)
        assertEquals(note.id, s.selectedNote.value)
        assertNull(s.editingNote.value) // placed empty: write on it, or type when asked
        s.updateNote(note.copy(text = "Sınavdan önce bak", x = 0.6f, y = 0.7f))
        assertEquals("Sınavdan önce bak", repo.notes[note.id]!!.text)

        s.deleteNote(repo.notes[note.id]!!)
        assertTrue(s.notes(3).value!!.isEmpty())
        assertNull(s.selectedNote.value)
        s.undo() // the delete
        // Back as it was last: moved and typed.
        assertEquals(0.6f, s.notes(3).value!!.single().x, 0f)
        assertFalse(note.id in repo.deletedNotes)
        s.undo() // the add
        assertTrue(s.notes(3).value!!.isEmpty())
        assertTrue(note.id in repo.deletedNotes)
        s.redo()
        assertEquals("Sınavdan önce bak", s.notes(3).value!!.single().text)
    }

    @Test
    fun notesStayInsideTheirPage() {
        val s = session()
        s.load(0)
        val note = s.addNote(0)
        s.updateNote(note.copy(x = 0.95f, y = -0.2f, width = 0.01f, height = 2f))
        val kept = repo.notes[note.id]!!
        assertEquals(com.salihraphics.meow.domain.model.StickyNote.MIN_SIZE, kept.width, 0f)
        assertEquals(1f, kept.height, 0f)
        assertTrue(kept.x + kept.width <= 1f)
        assertEquals(0f, kept.y, 0f)
    }

    @Test
    fun aNoteAddedBeforeItsPageHasLoadedIsKeptWhenTheLoadArrives() = kotlinx.coroutines.runBlocking {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : com.salihraphics.meow.data.annotation.AnnotationRepository by repo {
            override suspend fun notesOnPage(documentId: String, pageIndex: Int): List<com.salihraphics.meow.domain.model.StickyNote> {
                gate.await()
                return repo.notesOnPage(documentId, pageIndex)
            }
        }
        repo.putNote(com.salihraphics.meow.domain.model.StickyNote("old", "doc", 0, 0.5f, 0.5f, 0.3f, 0.2f, "eski", 0))
        val s = AnnotationSession("doc", slow, CoroutineScope(Dispatchers.Unconfined)) { "n${ids++}" }
        s.load(0) // reading is slow…
        val added = s.addNote(0) // …and the student adds a note meanwhile.
        gate.complete(Unit)
        kotlinx.coroutines.yield()
        assertEquals(setOf(added.id, "old"), s.notes(0).value!!.map { it.id }.toSet())
    }

    @Test
    fun inkWrittenOnANoteBelongsToItAndIsNeverAnAnswerMark() {
        val checked = mutableListOf<Stroke>()
        val s = AnnotationSession("doc", repo, CoroutineScope(Dispatchers.Unconfined), onStrokeAdded = { checked += it }) { "s${ids++}" }
        s.load(0)
        val note = s.addNote(0)
        // On a note a third of the page wide, the pen is stored three times as wide (of the note).
        s.addStroke(0, line(0.1f, 0.5f, 0.9f, 0.5f), AnnotationTool.PEN, note.id, widthScale = 3f)
        val ink = repo.all.values.single()
        assertEquals(note.id, ink.noteId)
        assertEquals(AnnotationToolState.PEN_WIDTHS[1] * 3f, ink.width, 1e-6f)
        assertTrue(checked.isEmpty())
        s.undo()
        assertTrue(ink.id in repo.deleted)
    }
}
