package com.salihraphics.meow.data.library

import androidx.room.Room
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.domain.model.PdfImportError
import com.salihraphics.meow.domain.model.PdfImportResult
import com.salihraphics.meow.testing.FakeDocumentSource
import com.salihraphics.meow.testing.FakeInspector
import com.salihraphics.meow.testing.pdfBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalPdfLibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: MeowDatabase
    private lateinit var store: PdfFileStore
    private val source = FakeDocumentSource()
    private var clock = 1_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PdfFileStore(tmp.newFolder("library"))
    }

    @After fun tearDown() = db.close()

    private fun repo(inspector: FakeInspector = FakeInspector(), database: MeowDatabase = db) =
        LocalPdfLibraryRepository(database.pdfDocuments(), store, source, inspector, Dispatchers.Unconfined, now = { clock })

    private fun incomingLeftovers() = tmp.root.resolve("library/incoming").listFiles().orEmpty().toList()

    @Test
    fun importCopiesFileAndStoresMetadata() = runBlocking {
        val bytes = pdfBytes()
        val uri = source.add("Deneme 3.pdf", bytes)
        val result = repo().import(uri, "Adsız")

        val doc = (result as PdfImportResult.Imported).document
        assertEquals("Deneme 3", doc.displayName)
        assertEquals("Deneme 3.pdf", doc.originalFileName)
        assertEquals(12, doc.pageCount)
        assertEquals(bytes.size.toLong(), doc.sizeBytes)
        assertEquals(1_000L, doc.importedAt)
        assertNull(doc.lastOpenedAt)
        assertArrayEquals(bytes, store.pdfFile(doc.id).readBytes())
        assertTrue(store.thumbnailFile(doc.id).exists())
        assertEquals(listOf(doc), repo().documents.first())
        assertTrue(incomingLeftovers().isEmpty())
    }

    @Test
    fun missingNameFallsBack() = runBlocking {
        val doc = (repo().import(source.add(null, pdfBytes()), "Adsız PDF") as PdfImportResult.Imported).document
        assertEquals("Adsız PDF", doc.displayName)
    }

    @Test
    fun sameContentTwiceIsNotAddedAgain() = runBlocking {
        val r = repo()
        val first = (r.import(source.add("a.pdf", pdfBytes("x")), "") as PdfImportResult.Imported).document
        val second = r.import(source.add("renamed copy.pdf", pdfBytes("x")), "")
        assertEquals(PdfImportResult.AlreadyInLibrary(first), second)
        assertEquals(1, r.documents.first().size)
        assertTrue(incomingLeftovers().isEmpty())
    }

    @Test
    fun differentFilesWithTheSameNameAreBothKept() = runBlocking {
        val r = repo()
        r.import(source.add("deneme.pdf", pdfBytes("1")), "")
        r.import(source.add("deneme.pdf", pdfBytes("2")), "")
        val docs = r.documents.first()
        assertEquals(2, docs.size)
        assertTrue(docs.all { store.pdfFile(it.id).exists() })
    }

    @Test
    fun rejectsFilesThatAreNotPdfs() = runBlocking {
        val result = repo().import(source.add("notes.pdf", "just text".toByteArray()), "")
        assertEquals(PdfImportResult.Failed(PdfImportError.NOT_A_PDF), result)
        assertTrue(repo().documents.first().isEmpty())
        assertTrue(incomingLeftovers().isEmpty())
    }

    @Test
    fun rejectsEmptyFilesAndZeroPagePdfs() = runBlocking {
        assertEquals(PdfImportResult.Failed(PdfImportError.EMPTY), repo().import(source.add("e.pdf", ByteArray(0)), ""))
        val noPages = FakeInspector().apply { pages = 0 }
        assertEquals(PdfImportResult.Failed(PdfImportError.EMPTY), repo(noPages).import(source.add("z.pdf", pdfBytes()), ""))
    }

    @Test
    fun reportsPasswordProtectedAndDamagedPdfs() = runBlocking {
        assertEquals(
            PdfImportResult.Failed(PdfImportError.PASSWORD_PROTECTED),
            repo(FakeInspector.passwordProtected()).import(source.add("p.pdf", pdfBytes("p")), ""),
        )
        assertEquals(
            PdfImportResult.Failed(PdfImportError.DAMAGED),
            repo(FakeInspector.damaged()).import(source.add("d.pdf", pdfBytes("d")), ""),
        )
        assertTrue(repo().documents.first().isEmpty())
        assertTrue(incomingLeftovers().isEmpty())
    }

    @Test
    fun reportsRevokedOrMissingFiles() = runBlocking {
        val uri = source.add("gone.pdf", pdfBytes())
        source.denied += uri
        assertEquals(PdfImportResult.Failed(PdfImportError.NO_ACCESS), repo().import(uri, ""))
        assertEquals(
            PdfImportResult.Failed(PdfImportError.NO_ACCESS),
            repo().import(android.net.Uri.parse("content://picker/unknown"), ""),
        )
    }

    @Test
    fun renameValidatesAndPersists() = runBlocking {
        val r = repo()
        val doc = (r.import(source.add("a.pdf", pdfBytes()), "") as PdfImportResult.Imported).document
        assertFalse(r.rename(doc.id, "   "))
        assertTrue(r.rename(doc.id, "  TYT   Matematik "))
        assertEquals("TYT Matematik", r.get(doc.id)?.displayName)
    }

    @Test
    fun removingDeletesOnlyMeowsCopy() = runBlocking {
        val r = repo()
        val bytes = pdfBytes()
        val uri = source.add("a.pdf", bytes)
        val doc = (r.import(uri, "") as PdfImportResult.Imported).document

        r.remove(doc.id)

        assertTrue(r.documents.first().isEmpty())
        assertNull(r.get(doc.id))
        assertFalse(store.pdfFile(doc.id).exists())
        assertFalse(store.thumbnailFile(doc.id).exists())
        // The picked original is still there and readable.
        assertArrayEquals(bytes, source.open(uri).readBytes())
        // And it can be imported again afterwards.
        assertTrue(r.import(uri, "") is PdfImportResult.Imported)
    }

    @Test
    fun readingPositionIsRecordedAndListIsMostRecentFirst() = runBlocking {
        val r = repo()
        val a = (r.import(source.add("a.pdf", pdfBytes("a")), "") as PdfImportResult.Imported).document
        clock = 2_000L
        val b = (r.import(source.add("b.pdf", pdfBytes("b")), "") as PdfImportResult.Imported).document
        assertEquals(listOf(b.id, a.id), r.documents.first().map { it.id })

        clock = 3_000L
        r.recordReading(a.id, 7)
        val updated = r.get(a.id)!!
        assertEquals(7, updated.lastPageIndex)
        assertEquals(3_000L, updated.lastOpenedAt)
        assertEquals(listOf(a.id, b.id), r.documents.first().map { it.id })
    }

    @Test
    fun librarySurvivesReopeningTheDatabase() = runBlocking {
        val dbFile = tmp.root.resolve("meow.db")
        val app = RuntimeEnvironment.getApplication()
        val first = Room.databaseBuilder(app, MeowDatabase::class.java, dbFile.path).allowMainThreadQueries().build()
        val doc = (repo(database = first).import(source.add("a.pdf", pdfBytes()), "") as PdfImportResult.Imported).document
        repo(database = first).recordReading(doc.id, 4)
        first.close()

        val reopened = Room.databaseBuilder(app, MeowDatabase::class.java, dbFile.path).allowMainThreadQueries().build()
        val restored = repo(database = reopened).get(doc.id)
        assertEquals(4, restored?.lastPageIndex)
        assertEquals("a", restored?.displayName)
        reopened.close()
    }

    @Test
    fun interruptedImportsAreCleanedUp() {
        store.newIncomingFile("stale").writeBytes(pdfBytes())
        store.clearIncoming()
        assertTrue(incomingLeftovers().isEmpty())
    }
}
