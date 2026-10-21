package com.salihraphics.meow.analysis

import androidx.room.Room
import com.salihraphics.meow.analysis.FixturePdf.Companion.questionPage
import com.salihraphics.meow.analysis.FixturePdf.Text
import com.salihraphics.meow.analysis.text.PdfBoxTextSource
import com.salihraphics.meow.data.answerkey.AnswerKeyRepository
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.domain.model.AnalysisStatus
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.testing.FakePdfLibraryRepository
import com.salihraphics.meow.testing.sampleDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class AnswerKeyAnalysisSchedulerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowDatabase::class.java).build()
    private val keys = AnswerKeyRepository(db)
    private lateinit var library: FakePdfLibraryRepository
    private var analyses = 0

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun scheduler(): AnswerKeyAnalysisScheduler {
        library = FakePdfLibraryRepository(tmp.root)
        val counting = object : com.salihraphics.meow.analysis.text.PdfTextSource {
            val real = PdfBoxTextSource(RuntimeEnvironment.getApplication())
            override fun open(file: java.io.File) = real.open(file).also { analyses++ }
        }
        return AnswerKeyAnalysisScheduler(scope, library, keys, AnswerKeyAnalyzer(counting), ocrFor = { null })
    }

    private fun writeBook(id: String, pages: Int = 3) {
        FixturePdf().apply {
            repeat(pages) { i -> page { questionPage(if (i == 0) "TEST 1" else null, 1 + i * 2, i + 1) } }
            page {
                add(Text(0.3f, 0.05f, "CEVAP ANAHTARI", 16f))
                add(Text(0.08f, 0.12f, "Test 1  1.A 2.B 3.C 4.D 5.E 6.A"))
            }
        }.write(library.pdfFile(id))
    }

    private suspend fun awaitStatus(id: String, predicate: (KeyStatus?) -> Boolean, scheduler: AnswerKeyAnalysisScheduler) =
        withTimeout(20_000) { keys.observeStatuses(scheduler.progress).first { predicate(it[id]) } }

    @Test
    fun aNewDocumentIsAnalysedAndItsKeyStored() = runBlocking<Unit> {
        val s = scheduler()
        writeBook("doc-1")
        s.start()
        library.add(sampleDocument("doc-1"), withFile = false)
        awaitStatus("doc-1", { it is KeyStatus.Found }, s)
        val test = keys.tests("doc-1").single()
        assertEquals(listOf("A", "B", "C", "D", "E", "A"), test.answers.map { it.value })
        assertEquals("hash-doc-1", keys.analysis("doc-1")!!.contentSha256)
    }

    @Test
    fun analysedFilesAreNotAnalysedAgainUnlessAsked() = runBlocking<Unit> {
        val s = scheduler()
        writeBook("doc-1")
        s.start()
        library.add(sampleDocument("doc-1"), withFile = false)
        awaitStatus("doc-1", { it is KeyStatus.Found }, s)
        // Another library change (e.g. a second import) doesn't redo doc-1.
        writeBook("doc-2")
        library.add(sampleDocument("doc-2"), withFile = false)
        awaitStatus("doc-2", { it is KeyStatus.Found }, s)
        assertEquals(2, analyses)

        s.enqueue("doc-1", force = true)
        withTimeout(20_000) { while (analyses < 3) kotlinx.coroutines.delay(50) }
        awaitStatus("doc-1", { it is KeyStatus.Found }, s)
    }

    @Test
    fun interruptedRunsStartAgain() = runBlocking<Unit> {
        val s = scheduler()
        writeBook("doc-1")
        keys.markStarted("doc-1", "hash-doc-1") // as if the app was killed mid-analysis
        library.add(sampleDocument("doc-1"), withFile = false)
        s.start()
        awaitStatus("doc-1", { it is KeyStatus.Found }, s)
        assertEquals(AnalysisStatus.DONE.name, keys.analysis("doc-1")!!.status)
    }

    @Test
    fun removingADocumentStopsItsAnalysis() = runBlocking<Unit> {
        val s = scheduler()
        writeBook("big", pages = 300)
        s.start()
        library.add(sampleDocument("big"), withFile = false)
        withTimeout(20_000) { s.progress.first { (it["big"] ?: 0f) > 0.05f } }
        library.remove("big")
        withTimeout(20_000) { s.progress.first { "big" !in it } }
        assertTrue(keys.tests("big").isEmpty())
        assertNull(keys.analysis("big")?.takeIf { it.status == AnalysisStatus.DONE.name })
    }

    @Test
    fun keyPagesTheStudentPointsAtAreReadAndReported() = runBlocking<Unit> {
        val s = scheduler()
        FixturePdf().apply {
            page { questionPage("TEST 1", 1, 1) }
            // No heading and only two short lines: not found on its own.
            page { add(Text(0.1f, 0.2f, "1 C   2 A   3 D")); add(Text(0.1f, 0.25f, "4 B   5 E   6 C")) }
        }.write(library.pdfFile("doc-1"))
        library.add(sampleDocument("doc-1"), withFile = false)
        s.readKeyPages("doc-1", listOf(1))
        val done = withTimeout(20_000) { s.requests.first { it["doc-1"] is AnswerKeyAnalysisScheduler.KeyRequest.Done } }
        assertEquals(AnswerKeyAnalysisScheduler.KeyRequest.Done(1), done["doc-1"])
        assertEquals(listOf("C", "A", "D", "B", "E", "C"), keys.tests("doc-1").single().answers.map { it.value })
        s.clearRequest("doc-1")
        assertNull(s.requests.value["doc-1"])
    }

    @Test
    fun aKeyPdfThatIsNotAPdfIsReportedNotStored() = runBlocking<Unit> {
        library = FakePdfLibraryRepository(tmp.root)
        val picked = tmp.newFile("cevaplar.pdf").apply { writeText("hello") }
        val source = object : com.salihraphics.meow.data.library.DocumentSource {
            override fun displayName(uri: android.net.Uri) = "cevaplar.pdf"
            override fun open(uri: android.net.Uri) = picked.inputStream()
        }
        val files = com.salihraphics.meow.data.answerkey.KeySourceFiles(tmp.newFolder("keys"), source)
        val s = AnswerKeyAnalysisScheduler(scope, library, keys, AnswerKeyAnalyzer(PdfBoxTextSource(RuntimeEnvironment.getApplication())), { null }, files)
        s.readKeyPdf("doc-1", android.net.Uri.parse("content://picked/1"))
        val failed = withTimeout(20_000) { s.requests.first { it["doc-1"] is AnswerKeyAnalysisScheduler.KeyRequest.Failed } }
        assertEquals(AnswerKeyAnalysisScheduler.KeyRequest.Failed(AnswerKeyAnalysisScheduler.KeyRequest.Reason.NOT_A_PDF), failed["doc-1"])
        assertTrue(keys.tests("doc-1").isEmpty())
    }

    @Test
    fun aKeyPdfIsReadIntoTheBooksKey() = runBlocking<Unit> {
        library = FakePdfLibraryRepository(tmp.root)
        val picked = FixturePdf().apply {
            page {
                add(Text(0.3f, 0.05f, "CEVAP ANAHTARI", 16f))
                add(Text(0.08f, 0.12f, "Test 1  1.A 2.B 3.C 4.D"))
                add(Text(0.08f, 0.18f, "Test 2  1.E 2.D 3.C 4.B"))
            }
        }.write(tmp.newFile("TYT Cevaplar.pdf"))
        val source = object : com.salihraphics.meow.data.library.DocumentSource {
            override fun displayName(uri: android.net.Uri) = "TYT Cevaplar.pdf"
            override fun open(uri: android.net.Uri) = picked.inputStream()
        }
        val files = com.salihraphics.meow.data.answerkey.KeySourceFiles(tmp.newFolder("keys"), source)
        val s = AnswerKeyAnalysisScheduler(scope, library, keys, AnswerKeyAnalyzer(PdfBoxTextSource(RuntimeEnvironment.getApplication())), { null }, files)
        s.readKeyPdf("doc-1", android.net.Uri.parse("content://picked/2"))
        val done = withTimeout(20_000) { s.requests.first { it["doc-1"] is AnswerKeyAnalysisScheduler.KeyRequest.Done } }
        assertEquals(AnswerKeyAnalysisScheduler.KeyRequest.Done(2), done["doc-1"])
        val tests = keys.tests("doc-1")
        assertEquals(listOf("Test 1", "Test 2"), tests.map { it.label })
        assertTrue(files.file(tests.first().sourceDocumentId).exists())
        assertEquals(listOf("TYT Cevaplar"), keys.observeKeySources("doc-1").first().map { it.displayName })
    }
}
