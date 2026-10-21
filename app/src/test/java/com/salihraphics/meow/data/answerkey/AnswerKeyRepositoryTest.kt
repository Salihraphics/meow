package com.salihraphics.meow.data.answerkey

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.salihraphics.meow.analysis.AnalysisOutcome
import com.salihraphics.meow.analysis.key.AnswerKeyParseResult
import com.salihraphics.meow.analysis.key.AnswerNote
import com.salihraphics.meow.analysis.key.AnswerProvenance
import com.salihraphics.meow.analysis.key.Confidence
import com.salihraphics.meow.analysis.key.DetectedAnswer
import com.salihraphics.meow.analysis.key.DetectedTest
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.analysis.key.TestIssue
import com.salihraphics.meow.analysis.key.TestRef
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.domain.model.AnalysisStatus
import com.salihraphics.meow.domain.model.AnswerConfidence
import com.salihraphics.meow.domain.model.AnswerOrigin
import com.salihraphics.meow.domain.model.AnswerSource
import com.salihraphics.meow.domain.model.KeyTestKind
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.KeyTestStatus
import com.salihraphics.meow.domain.model.SelectionOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnswerKeyRepositoryTest {
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MeowDatabase::class.java)

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowDatabase::class.java).allowMainThreadQueries().build()
    private var ids = 0
    private var idOverride: (() -> String)? = null
    private val repo = AnswerKeyRepository(db, now = { 1_000L }) { idOverride?.invoke() ?: "id${ids++}" }

    @After fun tearDown() = db.close()

    private fun answer(q: Int, a: String, c: Confidence = Confidence.HIGH, note: AnswerNote? = null, alts: List<String> = emptyList()) =
        DetectedAnswer(q, a, c, sourcePage = 9, note = note, alternatives = alts)

    private fun outcome(vararg tests: DetectedTest) = AnalysisOutcome(AnswerKeyParseResult(tests.toList()), pageCount = 10, textPages = 10, ocrReads = 0)

    private fun test(number: Int, vararg answers: DetectedAnswer, issues: List<TestIssue> = emptyList()) =
        DetectedTest(TestRef(number, "Test $number"), KeySourceKind.KEY_SECTION, listOf(9), listOf(0, 1, 2), answers.toList(), issues)

    @Test
    fun detectedKeyIsStoredWithItsSourceAndConfidence() = runBlocking {
        repo.markStarted("doc", "hash")
        repo.saveOutcome("doc", "hash", outcome(test(1, answer(1, "D"), answer(2, "B", Confidence.LOW, AnswerNote.OCR_GUESS))))
        val t = repo.tests("doc").single()
        assertEquals("Test 1", t.label)
        assertEquals(1, t.number)
        assertEquals(0..2, t.questionPages)
        assertEquals(listOf(9), t.sourcePages)
        assertEquals(KeyTestStatus.DETECTED, t.status)
        val second = t.answers[1]
        assertEquals(AnswerConfidence.LOW, second.confidence)
        assertEquals("OCR_GUESS", second.note)
        assertEquals(AnswerOrigin.DETECTED, second.origin)
        assertTrue(second.needsReview)
        val analysis = repo.analysis("doc")!!
        assertEquals(AnalysisStatus.DONE.name, analysis.status)
        assertEquals("hash", analysis.contentSha256)
        assertTrue(analysis.keyFound)
    }

    @Test
    fun aNewDetectionReplacesTheOldOneButNeverAConfirmedKey() = runBlocking {
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "A"), answer(2, "B"), answer(3, "C"))))
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "E"), answer(2, "E"), answer(3, "E"))))
        assertEquals(listOf("E", "E", "E"), repo.tests("doc").single().answers.map { it.value })

        repo.confirm("doc")
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "A"), answer(2, "A"), answer(3, "A"))))
        val kept = repo.tests("doc").single()
        assertEquals(KeyTestStatus.CONFIRMED, kept.status)
        assertEquals(listOf("E", "E", "E"), kept.answers.map { it.value })
    }

    @Test
    fun conflictsBlockConfirmationUntilTheStudentPicks() = runBlocking {
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "A"), answer(2, "B", Confidence.LOW, AnswerNote.CONFLICT, listOf("B", "D")), answer(3, "C"))))
        assertEquals(ConfirmResult.UnresolvedConflicts(1), repo.confirm("doc"))
        val conflicted = repo.tests("doc").single().answers.single { it.question == 2 }
        assertEquals(listOf("B", "D"), conflicted.alternatives)

        repo.correctAnswer(conflicted.id, AnswerType.CHOICE, "D")
        val fixed = repo.tests("doc").single().answers.single { it.question == 2 }
        assertEquals("D", fixed.value)
        assertEquals(AnswerOrigin.MANUAL, fixed.origin)
        assertNull(fixed.note)
        assertEquals(ConfirmResult.Confirmed, repo.confirm("doc"))
        assertTrue(repo.tests("doc").all { it.isConfirmed })
    }

    @Test
    fun nothingToConfirmWithoutAnswers() = runBlocking {
        assertEquals(ConfirmResult.NothingToConfirm, repo.confirm("doc"))
    }

    @Test
    fun manualKeysForChoiceNumberAndTextAnswers() = runBlocking {
        val testId = repo.addManualTest("doc", "Deneme 3", 3)
        repo.setAnswer(testId, 1, AnswerType.CHOICE, "c")
        repo.setAnswer(testId, 2, AnswerType.NUMBER, "12,5", tolerance = 0.1)
        repo.setAnswer(testId, 3, AnswerType.TEXT, "fotosentez")
        repo.setAnswer(testId, 1, AnswerType.CHOICE, "A") // replaces, doesn't duplicate
        val t = repo.tests("doc").single()
        assertEquals(listOf(1, 2, 3), t.answers.map { it.question })
        assertEquals("A", t.answers[0].value)
        assertEquals(AnswerType.NUMBER, t.answers[1].type)
        assertEquals(0.1, t.answers[1].tolerance!!, 0.0)
        assertTrue(t.answers.all { it.origin == AnswerOrigin.MANUAL })

        repo.updateTest(testId, "Deneme 3 (Matematik)", 10..14)
        assertEquals(10..14, repo.tests("doc").single().questionPages)
        repo.deleteAnswer(t.answers[2].id)
        assertEquals(2, repo.tests("doc").single().answers.size)
    }

    @Test
    fun aSaveThatFailsHalfWayLeavesThePreviousKeyIntact() = runBlocking {
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "A"), answer(2, "B"), answer(3, "C"))))
        // Next save: the second answer reuses an id, so the insert fails mid-transaction.
        val scripted = ArrayDeque(listOf("t-new", "dup", "dup", "x", "y"))
        idOverride = { scripted.removeFirst() }
        try {
            repo.saveOutcome("doc", "h2", outcome(test(1, answer(1, "E"), answer(2, "E"), answer(3, "E"))))
            fail("expected the insert to fail")
        } catch (e: Exception) {
            // expected
        }
        idOverride = null
        val t = repo.tests("doc").single()
        assertEquals(listOf("A", "B", "C"), t.answers.map { it.value })
        assertEquals("h", repo.analysis("doc")!!.contentSha256)
    }

    @Test
    fun interruptedRunsAreFoundAgain() = runBlocking {
        repo.markStarted("doc", "h")
        assertEquals(listOf("doc"), repo.interruptedRuns())
        repo.saveOutcome("doc", "h", outcome())
        assertTrue(repo.interruptedRuns().isEmpty())
        assertFalse(repo.analysis("doc")!!.keyFound)
    }

    @Test
    fun attemptsAreRecordedAndRemovedWithTheDocument() = runBlocking {
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "A"), answer(2, "B"), answer(3, "C"))))
        val testId = repo.tests("doc").single().id
        repo.recordSelection("doc", testId, 2, 0, "B", SelectionOrigin.RECOGNIZED, "stroke-1", floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))
        val attempt = repo.observeAttempts("doc").first().single()
        assertEquals(AttemptResult.PENDING, attempt.result)
        assertEquals("stroke-1", attempt.strokeId)

        repo.removeDocument("doc")
        assertTrue(repo.tests("doc").isEmpty())
        assertTrue(repo.observeAttempts("doc").first().isEmpty())
        assertNull(repo.analysis("doc"))
    }

    @Test
    fun upgradingFromVersion2KeepsLibraryAndAnnotations() {
        val name = "upgrade.db"
        migrations.createDatabase(name, 2).apply {
            execSQL(
                "INSERT INTO pdf_documents (id, displayName, originalFileName, sizeBytes, pageCount, contentSha256, " +
                    "importedAt, lastOpenedAt, lastPageIndex, updatedAt, deletedAt) VALUES ('d1', 'TYT', 'TYT.pdf', 10, 3, 'abc', 1, 5, 2, 1, NULL)",
            )
            execSQL(
                "INSERT INTO annotation_strokes (id, documentId, pageIndex, tool, color, width, points, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('s1', 'd1', 2, 'PEN', -65536, 0.005, X'0000803F', 1, 1, NULL)",
            )
            close()
        }
        val db = migrations.runMigrationsAndValidate(name, 3, true)
        db.query("SELECT lastPageIndex FROM pdf_documents WHERE id = 'd1'").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        db.query("SELECT pageIndex FROM annotation_strokes WHERE id = 's1'").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        for (table in listOf("document_analysis", "key_tests", "key_answers", "answer_attempts")) {
            db.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun upgradingFromVersion3KeepsKeysAndAttempts() {
        val name = "upgrade3.db"
        migrations.createDatabase(name, 3).apply {
            execSQL(
                "INSERT INTO key_tests (id, documentId, sourceDocumentId, label, testNumber, kind, status, sourcePages, firstQuestionPage, " +
                    "lastQuestionPage, orderIndex, createdAt, updatedAt, deletedAt) VALUES ('t1', 'd1', 'd1', 'Test 1', 1, 'KEY_SECTION', " +
                    "'CONFIRMED', '9', 0, 3, 0, 1, 1, NULL)",
            )
            execSQL(
                "INSERT INTO key_answers (id, testId, questionNumber, type, answer, tolerance, origin, confidence, note, alternatives, " +
                    "sourcePage, updatedAt, deletedAt) VALUES ('a1', 't1', 1, 'CHOICE', 'C', NULL, 'DETECTED', 'HIGH', NULL, NULL, 9, 1, NULL)",
            )
            execSQL(
                "INSERT INTO answer_attempts (id, documentId, testId, questionNumber, pageIndex, selected, selectionOrigin, result, " +
                    "strokeId, regionLeft, regionTop, regionRight, regionBottom, createdAt, updatedAt, deletedAt) VALUES ('x1', 'd1', 't1', " +
                    "1, 0, 'C', 'RECOGNIZED', 'CORRECT', 's1', 0.1, 0.1, 0.2, 0.2, 1, 1, NULL)",
            )
            close()
        }
        val db = migrations.runMigrationsAndValidate(name, 4, true)
        db.query("SELECT answer FROM key_answers WHERE id = 'a1'").use { assertTrue(it.moveToFirst()); assertEquals("C", it.getString(0)) }
        db.query("SELECT result, sessionId, strokeRemoved FROM answer_attempts WHERE id = 'x1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("CORRECT", it.getString(0))
            assertTrue(it.isNull(1)) // older answers belong to no session, so nothing is re-graded
            assertEquals(0, it.getInt(2))
        }
        db.query("SELECT COUNT(*) FROM solving_sessions").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
    }

    @Test
    fun answersCollectInOneSessionUntilChecked() = runBlocking {
        repo.recordSelection("doc", null, 1, 0, "B", SelectionOrigin.RECOGNIZED, "s1", null)
        repo.recordSelection("doc", null, 2, 0, "D", SelectionOrigin.RECOGNIZED, "s2", null)
        repo.recordSelection("doc", null, 1, 0, "A", SelectionOrigin.CHOSEN_BY_STUDENT, "s3", null)
        val session = repo.openSession("doc")!!
        assertEquals(3, session.answers.size)
        assertEquals(listOf(1 to "A", 2 to "D"), session.latest.map { it.question to it.selected })
        assertTrue(session.answers.all { it.result == AttemptResult.PENDING })
        assertEquals(session, repo.observeOpenSession("doc").first())
    }

    @Test
    fun theSameStrokeReportedTwiceIsOneAnswer() = runBlocking {
        val first = repo.recordSelection("doc", null, 1, 0, "B", SelectionOrigin.RECOGNIZED, "s1", null)
        val again = repo.recordSelection("doc", null, 1, 0, "B", SelectionOrigin.RECOGNIZED, "s1", null)
        assertEquals(first, again)
        assertEquals(1, repo.openSession("doc")!!.answers.size)
    }

    @Test
    fun undoingAnAnswerBringsBackTheEarlierOneAndClearingEmptiesTheQuestion() = runBlocking {
        repo.recordSelection("doc", null, 1, 0, "B", SelectionOrigin.RECOGNIZED, "s1", null)
        val second = repo.recordSelection("doc", null, 1, 0, "A", SelectionOrigin.RECOGNIZED, "s2", null)
        repo.recordSelection("doc", null, 2, 0, "D", SelectionOrigin.RECOGNIZED, "s3", null)
        repo.removeAnswer(second) // "Geri al" right after marking A
        assertEquals(listOf(1 to "B", 2 to "D"), repo.openSession("doc")!!.latest.map { it.question to it.selected })
        val session = repo.openSession("doc")!!
        repo.clearQuestion(session.id, session.latest.first())
        assertEquals(listOf(2 to "D"), repo.openSession("doc")!!.latest.map { it.question to it.selected })
    }

    @Test
    fun checkingStoresResultsAndTheNextAnswerStartsANewSession() = runBlocking {
        val first = repo.recordSelection("doc", "t", 1, 0, "B", SelectionOrigin.RECOGNIZED, "s1", null)
        val session = repo.openSession("doc")!!
        repo.finishSession(session.id, listOf(AnswerKeyRepository.GradedAttempt(first, "t", AttemptResult.INCORRECT)), 0, 1, 3)
        assertNull(repo.openSession("doc"))
        assertEquals(AttemptResult.INCORRECT, repo.observeAttempts("doc").first().single().result)
        repo.recordSelection("doc", "t", 1, 0, "C", SelectionOrigin.RECOGNIZED, "s9", null)
        val next = repo.openSession("doc")!!
        assertTrue(next.id != session.id)
        assertEquals(listOf("C"), next.latest.map { it.selected })
    }

    @Test
    fun aRescanAddsNewTestsButKeepsConfirmedAndTypedOnes() = runBlocking {
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "A"), answer(2, "B"), answer(3, "C"))))
        repo.confirm("doc")
        val typed = repo.addManualTest("doc", "Deneme", null)
        repo.setAnswer(typed, 1, AnswerType.CHOICE, "D")
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "E"), answer(2, "E"), answer(3, "E")), test(2, answer(1, "B"), answer(2, "C"), answer(3, "D"))))
        val tests = repo.tests("doc")
        assertEquals(listOf("Test 1", "Deneme", "Test 2"), tests.map { it.label })
        assertEquals(listOf("A", "B", "C"), tests[0].answers.map { it.value }) // confirmed: untouched, not duplicated
        assertEquals(listOf("D"), tests[1].answers.map { it.value })
        assertEquals(KeyTestStatus.DETECTED, tests[2].status)
        // And again: the unconfirmed detection is replaced, not piled up.
        repo.saveOutcome("doc", "h", outcome(test(2, answer(1, "B"), answer(2, "C"), answer(3, "D"))))
        assertEquals(3, repo.tests("doc").size)
    }

    @Test
    fun aKeyPdfIsStoredAsItsOwnSourceAndNamed() = runBlocking {
        repo.saveOutcome("doc", "h", outcome())
        val found = repo.saveKeySourceOutcome("doc", "key-1", "TYT Cevaplar", outcome(test(1, answer(1, "A"), answer(2, "B"), answer(3, "C"))))
        assertEquals(1, found)
        val test = repo.tests("doc").single()
        assertEquals("key-1", test.sourceDocumentId)
        assertNull(test.questionPages) // pages of another PDF say nothing about the book's pages
        assertEquals(listOf("TYT Cevaplar"), repo.observeKeySources("doc").first().map { it.displayName })
        // A rescan of the book doesn't remove what came from the key PDF.
        repo.saveOutcome("doc", "h", outcome())
        assertEquals(1, repo.tests("doc").size)
    }

    @Test
    fun keyPagesTheStudentPointedAtReplaceOnlyWhatCameFromThosePages() = runBlocking {
        val margin = DetectedTest(TestRef(5, "Test 5"), KeySourceKind.PAGE_MARGIN, listOf(40), listOf(40), listOf(answer(1, "A"), answer(2, "B")), emptyList())
        val section = DetectedTest(TestRef(1, "Test 1"), KeySourceKind.KEY_SECTION, listOf(90), emptyList(), listOf(answer(1, "E")), emptyList())
        repo.saveOutcome("doc", "h", outcome(margin, section))
        val pointed = DetectedTest(TestRef(1, "Test 1"), KeySourceKind.KEY_SECTION, listOf(90), emptyList(), listOf(answer(1, "C"), answer(2, "D"), answer(3, "A")), emptyList())
        repo.saveKeyPagesOutcome("doc", listOf(90, 91), outcome(pointed))
        val tests = repo.tests("doc")
        assertEquals(listOf("Test 5", "Test 1"), tests.map { it.label })
        assertEquals(listOf("C", "D", "A"), tests[1].answers.map { it.value })
    }

    @Test
    fun theSearchCluesAreKept() = runBlocking {
        val result = AnswerKeyParseResult(emptyList(), com.salihraphics.meow.analysis.key.KeyClues(headingPages = listOf(230, 231), answerLines = 0))
        repo.saveOutcome("doc", "h", AnalysisOutcome(result, 240, 200, 20, unreadablePages = listOf(5), ocrPages = listOf(239, 238)))
        val clues = repo.analysis("doc")!!.let { com.salihraphics.meow.domain.model.AnalysisClues.decode(it.clues) }
        assertEquals(listOf(230, 231), clues.headingPages)
        assertEquals(listOf(5), clues.unreadablePages)
        assertEquals(40, clues.scannedPages)
        assertEquals(listOf(239, 238), clues.ocrPages)
    }

    @Test
    fun upgradingFromVersion4KeepsAnswersAndAddsTheirSources() {
        val name = "upgrade4.db"
        migrations.createDatabase(name, 4).apply {
            execSQL(
                "INSERT INTO key_answers (id, testId, questionNumber, type, answer, tolerance, origin, confidence, note, alternatives, " +
                    "sourcePage, updatedAt, deletedAt) VALUES ('a1', 't1', 1, 'CHOICE', 'C', NULL, 'DETECTED', 'HIGH', NULL, NULL, 9, 1, NULL)",
            )
            close()
        }
        val db = migrations.runMigrationsAndValidate(name, 5, true)
        db.query("SELECT answer, sources FROM key_answers WHERE id = 'a1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("C", it.getString(0))
            assertTrue(it.isNull(1))
        }
    }

    @Test
    fun whereEachAnswerWasReadIsKept() = runBlocking {
        val conflict = DetectedAnswer(
            2, "A", Confidence.LOW, 40, AnswerNote.CONFLICT, listOf("A", "E"),
            listOf(AnswerProvenance(KeySourceKind.KEY_SECTION, 40, "A"), AnswerProvenance(KeySourceKind.LABELLED, 3, "E")),
        )
        repo.saveOutcome("doc", "h", outcome(test(1, answer(1, "C"), conflict, answer(3, "D"))))
        val saved = repo.tests("doc").single().answers.single { it.question == 2 }
        assertEquals(
            listOf(AnswerSource(KeyTestKind.KEY_SECTION, 40, "A"), AnswerSource(KeyTestKind.LABELLED, 3, "E")),
            saved.provenance,
        )
        assertEquals(listOf("A", "E"), saved.alternatives)
    }
}
