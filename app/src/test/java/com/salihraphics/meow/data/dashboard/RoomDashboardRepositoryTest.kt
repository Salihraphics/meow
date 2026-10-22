package com.salihraphics.meow.data.dashboard

import androidx.room.Room
import com.salihraphics.meow.data.annotation.StickyNoteEntity
import com.salihraphics.meow.data.annotation.StrokeEntity
import com.salihraphics.meow.data.answerkey.SolvingSessionEntity
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.data.library.db.PdfDocumentEntity
import com.salihraphics.meow.data.study.StudySessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDashboardRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = RoomDashboardRepository(db.strokes(), db.study(), db.answerKeys())

    @After fun tearDown() = db.close()

    private suspend fun doc(id: String, deleted: Boolean = false) = db.pdfDocuments().insert(
        PdfDocumentEntity(id, id, "$id.pdf", 1, 10, "sha-$id", 1, null, 0, 1, if (deleted) 5 else null),
    )

    private fun note(id: String, doc: String, page: Int, text: String, updated: Long, deleted: Long? = null) =
        StickyNoteEntity(id, doc, page, 0.1f, 0.1f, 0.2f, 0.1f, text, 0, updated, updated, deleted)

    private fun ink(id: String, noteId: String, doc: String, updated: Long, deleted: Long? = null) = StrokeEntity(
        id, doc, 0, "PEN", 0, 0.01f, StrokeEntity.encodePoints(floatArrayOf(0f, 0f, 1f, 1f)), updated, updated, deleted, noteId,
    )

    @Test
    fun recentNotesSkipEmptyDeletedAndRemovedPdfNotesNewestFirst() = runBlocking {
        doc("a"); doc("gone", deleted = true)
        val strokes = db.strokes()
        strokes.putNote(note("typed", "a", 3, "Zincir kuralı", updated = 10))
        strokes.putNote(note("inked", "a", 0, "", updated = 5))
        strokes.insert(ink("s1", "inked", "a", updated = 20)) // writing on it makes it the newest
        strokes.putNote(note("blank", "a", 1, "   ", updated = 30)) // placed and never used
        strokes.putNote(note("erasedInk", "a", 2, "", updated = 31))
        strokes.insert(ink("s2", "erasedInk", "a", updated = 32, deleted = 33))
        strokes.putNote(note("deleted", "a", 4, "Silindi", updated = 40, deleted = 41))
        strokes.putNote(note("otherPdf", "gone", 0, "Kaldırılan PDF", updated = 50))

        val notes = repo.recentNotes(10).first()
        assertEquals(listOf("inked", "typed"), notes.map { it.noteId })
        assertEquals(NoteSummary("inked", "a", 0, "", hasInk = true, editedAt = 20), notes[0])
        assertEquals("Zincir kuralı", notes[1].text)
        assertEquals(1, repo.recentNotes(1).first().size)
    }

    @Test
    fun studyAndCheckedTestsComeFromTheirTables() = runBlocking {
        doc("a"); doc("gone", deleted = true)
        db.study().insertSession(StudySessionEntity("s1", "a", 1_500_000, 1_500_000, true, 100, 1_000))
        db.study().insertSession(StudySessionEntity("s0", "a", 1_500_000, 600_000, false, 10, 50))
        val keys = db.answerKeys()
        keys.insertSession(SolvingSessionEntity("t1", "a", 100, 2_000, 30, 8, 2, 2_000, null))
        keys.insertSession(SolvingSessionEntity("open", "a", 100, null, null, null, null, 100, null))
        keys.insertSession(SolvingSessionEntity("old", "a", 1, 60, 1, 1, 1, 60, null))
        keys.insertSession(SolvingSessionEntity("removed", "gone", 100, 3_000, 5, 5, 5, 3_000, null))

        assertEquals(listOf(StudyLog(1_500_000, true, 1_000)), repo.studySince(100).first())
        assertEquals(listOf(CheckedTest(2_000, 30, 8, 2)), repo.checkedTestsSince(100).first())
    }
}
