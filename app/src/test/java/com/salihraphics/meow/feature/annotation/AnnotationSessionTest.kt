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
}
