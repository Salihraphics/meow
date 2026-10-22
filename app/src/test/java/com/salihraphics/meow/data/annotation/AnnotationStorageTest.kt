package com.salihraphics.meow.data.annotation

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.domain.model.StickyNote
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.domain.model.StrokeTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnotationStorageTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MeowDatabase::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clock = 1_000L

    @After fun tearDown() = scope.cancel()

    private fun stroke(id: String, doc: String = "doc", page: Int = 0, points: FloatArray = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)) =
        Stroke(id, doc, page, StrokeTool.PEN, 0xFF1E5EFF.toInt(), 0.005f, points)

    private fun openDb(file: java.io.File) =
        Room.databaseBuilder(RuntimeEnvironment.getApplication(), MeowDatabase::class.java, file.path).build()

    @Test
    fun pointsRoundTripExactly() {
        val points = floatArrayOf(0f, 1f, 0.123456f, 0.999999f, 0.5f, 0.25f)
        assertArrayEquals(points, StrokeEntity.decodePoints(StrokeEntity.encodePoints(points)), 0f)
    }

    @Test
    fun strokesStayOnTheirOwnPageAndDocument() = runBlocking {
        val db = openDb(tmp.root.resolve("a.db"))
        val repo = LocalAnnotationRepository(db.strokes(), scope) { clock }
        repo.add(stroke("p0", page = 0))
        repo.add(stroke("p1", page = 1))
        repo.add(stroke("other", doc = "other-doc", page = 0))
        assertEquals(listOf("p0"), repo.strokesOnPage("doc", 0).map { it.id })
        assertEquals(listOf("p1"), repo.strokesOnPage("doc", 1).map { it.id })
        assertEquals(listOf("other"), repo.strokesOnPage("other-doc", 0).map { it.id })
        assertTrue(repo.strokesOnPage("doc", 2).isEmpty())
        db.close()
    }

    @Test
    fun eraseAndRestoreOnlyToggleVisibility() = runBlocking {
        val db = openDb(tmp.root.resolve("b.db"))
        val repo = LocalAnnotationRepository(db.strokes(), scope) { clock }
        val original = stroke("s", points = floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f, 0.5f, 0.2f))
        repo.add(original)
        repo.erase(listOf("s"))
        assertTrue(repo.strokesOnPage("doc", 0).isEmpty())
        repo.restore(listOf("s"))
        assertEquals(listOf(original), repo.strokesOnPage("doc", 0))
        db.close()
    }

    @Test
    fun strokesSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val file = tmp.root.resolve("c.db")
        val first = openDb(file)
        val writer = LocalAnnotationRepository(first.strokes(), scope) { clock }
        writer.add(stroke("kept", page = 3))
        writer.add(stroke("erased", page = 3))
        writer.erase(listOf("erased"))
        writer.flush()
        first.close()

        val reopened = openDb(file)
        val reader = LocalAnnotationRepository(reopened.strokes(), scope) { clock }
        assertEquals(listOf("kept"), reader.strokesOnPage("doc", 3).map { it.id })
        reopened.close()
    }

    @Test
    fun writesKeepTheirOrder() = runBlocking {
        val db = openDb(tmp.root.resolve("d.db"))
        val repo = LocalAnnotationRepository(db.strokes(), scope) { clock }
        // Add, erase, restore, erase: only the final state counts, and no step may overtake another.
        repeat(50) { i ->
            repo.add(stroke("s$i"))
            repo.erase(listOf("s$i"))
            if (i % 2 == 0) repo.restore(listOf("s$i"))
        }
        assertEquals((0 until 50 step 2).map { "s$it" }, repo.strokesOnPage("doc", 0).map { it.id })
        db.close()
    }

    @Test
    fun upgradingFromVersion1KeepsTheLibraryAndAddsAnnotations() {
        val name = "migration.db"
        migrations.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO pdf_documents (id, displayName, originalFileName, sizeBytes, pageCount, contentSha256, " +
                    "importedAt, lastOpenedAt, lastPageIndex, updatedAt, deletedAt) " +
                    "VALUES ('doc-1', 'TYT', 'TYT.pdf', 10, 3, 'abc', 1, NULL, 2, 1, NULL)",
            )
            close()
        }
        val db = migrations.runMigrationsAndValidate(name, 2, true)
        db.query("SELECT displayName, lastPageIndex FROM pdf_documents WHERE id = 'doc-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("TYT", it.getString(0))
            assertEquals(2, it.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM annotation_strokes").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    private fun note(id: String, page: Int = 0, text: String = "Formülü tekrar et") =
        StickyNote(id, "doc", page, 0.2f, 0.3f, 0.3f, 0.16f, text, StickyNote.YELLOW)

    @Test
    fun notesKeepTheirPagePositionSizeAndTextAcrossReopening() = runBlocking {
        val file = tmp.root.resolve("notes.db")
        val first = openDb(file)
        val writer = LocalAnnotationRepository(first.strokes(), scope) { clock }
        writer.putNote(note("n1"))
        writer.putNote(note("n2", page = 3))
        // Dragged, resized and edited later: the same row is updated, not duplicated.
        clock = 2_000L
        writer.putNote(note("n1").copy(x = 0.55f, y = 0.7f, width = 0.4f, height = 0.25f, text = "Türev kuralları"))
        writer.flush()
        first.close()

        val second = openDb(file)
        val reader = LocalAnnotationRepository(second.strokes(), scope) { clock }
        assertEquals(
            listOf(note("n1").copy(x = 0.55f, y = 0.7f, width = 0.4f, height = 0.25f, text = "Türev kuralları")),
            reader.notesOnPage("doc", 0),
        )
        assertEquals(listOf("n2"), reader.notesOnPage("doc", 3).map { it.id })
        second.close()
    }

    @Test
    fun deletingANoteIsATombstoneThatUndoCanLift() = runBlocking {
        val db = openDb(tmp.root.resolve("notes2.db"))
        val repo = LocalAnnotationRepository(db.strokes(), scope) { clock }
        repo.putNote(note("n"))
        repo.deleteNote("n")
        assertTrue(repo.notesOnPage("doc", 0).isEmpty())
        repo.restoreNote("n")
        assertEquals(listOf("n"), repo.notesOnPage("doc", 0).map { it.id })
        db.close()
    }

    @Test
    fun highlighterStrokesAreStoredWithTheirTool() = runBlocking {
        val db = openDb(tmp.root.resolve("hl.db"))
        val repo = LocalAnnotationRepository(db.strokes(), scope) { clock }
        repo.add(stroke("h").copy(tool = StrokeTool.HIGHLIGHTER, color = 0x66FFE14D, width = 0.025f))
        assertEquals(StrokeTool.HIGHLIGHTER, repo.strokesOnPage("doc", 0).single().tool)
        db.close()
    }

    @Test
    fun upgradingFromVersion5KeepsLibraryDrawingsAndAnswers() {
        val name = "upgrade5.db"
        migrations.createDatabase(name, 5).apply {
            execSQL(
                "INSERT INTO pdf_documents (id, displayName, originalFileName, sizeBytes, pageCount, contentSha256, importedAt, " +
                    "lastOpenedAt, lastPageIndex, updatedAt, deletedAt) VALUES ('doc', 'TYT Matematik', 'tyt.pdf', 10, 240, 'h', 1, 2, 57, 2, NULL)",
            )
            execSQL(
                "INSERT INTO annotation_strokes (id, documentId, pageIndex, tool, color, width, points, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('s1', 'doc', 57, 'PEN', -16777216, 0.005, x'0000803E0000003F', 1, 1, NULL)",
            )
            execSQL(
                "INSERT INTO answer_attempts (id, documentId, testId, questionNumber, pageIndex, selected, selectionOrigin, result, strokeId, " +
                    "createdAt, updatedAt, deletedAt) VALUES ('a1', 'doc', NULL, 4, 57, 'C', 'RECOGNIZED', 'PENDING', 's1', 1, 1, NULL)",
            )
            close()
        }
        val db = migrations.runMigrationsAndValidate(name, 6, true)
        db.query("SELECT lastPageIndex, viewRotation FROM pdf_documents WHERE id = 'doc'").use {
            assertTrue(it.moveToFirst())
            assertEquals(57, it.getInt(0))
            assertEquals(0, it.getInt(1))
        }
        db.query("SELECT points FROM annotation_strokes WHERE id = 's1'").use {
            assertTrue(it.moveToFirst())
            assertArrayEquals(floatArrayOf(0.25f, 0.5f), StrokeEntity.decodePoints(it.getBlob(0)), 0f)
        }
        db.query("SELECT selected FROM answer_attempts WHERE id = 'a1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("C", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM sticky_notes").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
    }
}
