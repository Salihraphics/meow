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

    // ---- Stylus ---------------------------------------------------------------------------

    @Test
    fun aStylusMakesFingersScrollUnlessTheStudentChoseOtherwise() {
        val s = session()
        assertTrue(s.tools.value.fingerDraws)
        s.onStylusSeen()
        assertFalse(s.tools.value.fingerDraws)
        assertTrue(s.tools.value.stylusSeen)
        s.setFingerDraws(true)
        s.onStylusSeen()
        assertTrue("the student's choice stays", s.tools.value.fingerDraws)

        val other = session("doc2")
        other.setFingerDraws(true)
        other.onStylusSeen()
        assertTrue("chosen before the stylus came", other.tools.value.fingerDraws)
    }

    @Test
    fun pressuresAreSavedWithTheStroke() {
        val s = session()
        s.addStroke(0, line(0.1f, 0.1f, 0.2f, 0.2f, 0.3f, 0.3f), pressures = floatArrayOf(0.2f, 0.5f, 0.9f))
        assertTrue(repo.all.values.single().pressures!!.contentEquals(floatArrayOf(0.2f, 0.5f, 0.9f)))
        // A list that doesn't match the points is dropped rather than misdrawn.
        s.addStroke(0, line(0.1f, 0.1f, 0.2f, 0.2f), pressures = floatArrayOf(0.2f))
        assertNull(repo.all.values.last().pressures)
    }

    // ---- Shape correction -----------------------------------------------------------------

    /** A wobbly hand-drawn horizontal line across a third of an A4 page. */
    private fun shakyLine(): FloatArray {
        val n = 40
        return FloatArray(n * 2) { i -> if (i % 2 == 0) 0.2f + 0.4f * (i / 2) / (n - 1f) else 0.5f + 0.002f * kotlin.math.sin(i * 0.7f) }
    }

    @Test
    fun withShapeCorrectionOffEveryStrokeStaysAsDrawn() {
        val s = session()
        val drawn = shakyLine()
        s.addStroke(0, drawn, aspect = 1.414f)
        assertTrue(repo.all.values.single().points.contentEquals(drawn))
    }

    @Test
    fun aCorrectedShapeUndoesToTheStrokeAsDrawnThenToNothing() {
        val s = session()
        s.setShapeCorrection(true)
        val drawn = shakyLine()
        s.addStroke(0, drawn, aspect = 1.414f)
        val clean = s.strokes(0).value!!.single()
        assertEquals(4, clean.points.size) // a straight line: two points
        assertEquals(clean.points[1], clean.points[3])
        assertEquals(listOf(clean.id), repo.all.keys.toList())

        assertEquals(0, s.undo())
        val back = s.strokes(0).value!!.single()
        assertTrue("the hand-drawn stroke is back", back.points.contentEquals(drawn))
        assertTrue("and saved", repo.all[back.id] != null && back.id !in repo.deleted)
        assertTrue(clean.id in repo.deleted)

        assertEquals(0, s.undo())
        assertTrue(s.strokes(0).value!!.isEmpty())
        assertFalse(s.tools.value.canUndo)

        s.redo()
        assertTrue(s.strokes(0).value!!.single().points.contentEquals(drawn))
        s.redo()
        assertEquals(clean.id, s.strokes(0).value!!.single().id)
        assertFalse(clean.id in repo.deleted)
        assertTrue(back.id in repo.deleted)
    }

    @Test
    fun aHighlighterOnlyEverBecomesAStraightBand() {
        val s = session()
        s.setShapeCorrection(true)
        s.selectTool(AnnotationTool.HIGHLIGHTER)
        val circle = FloatArray(80) { i ->
            val a = 2 * Math.PI * (i / 2) / 39.0 * 1.03
            if (i % 2 == 0) (0.5 + 0.1 * kotlin.math.cos(a)).toFloat() else ((0.5 + 0.1 * kotlin.math.sin(a)) / 1.414).toFloat()
        }
        s.addStroke(0, circle, AnnotationTool.HIGHLIGHTER, aspect = 1.414f)
        assertTrue("a highlighted circle stays freehand", repo.all.values.last().points.contentEquals(circle))
        s.addStroke(0, shakyLine(), AnnotationTool.HIGHLIGHTER, aspect = 1.414f)
        assertEquals(4, repo.all.values.last().points.size)
    }

    @Test
    fun aCorrectedStrokeLosesItsPressureSoTheShapeLooksEven() {
        val s = session()
        s.setShapeCorrection(true)
        s.addStroke(0, shakyLine(), pressures = FloatArray(40) { 0.2f + it / 60f }, aspect = 1.414f)
        assertNull(s.strokes(0).value!!.single().pressures)
    }

    @Test
    fun answerMarksSeeTheShapeThatStays() {
        val seen = mutableListOf<Stroke>()
        val s = AnnotationSession("doc", repo, CoroutineScope(Dispatchers.Unconfined), onStrokeAdded = { seen += it }) { "s${ids++}" }
        s.setShapeCorrection(true)
        s.addStroke(0, shakyLine(), aspect = 1.414f)
        assertEquals(1, seen.size)
        assertEquals(4, seen.single().points.size)
    }
}
