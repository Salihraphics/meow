package com.salihraphics.meow.data.answerkey

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.salihraphics.meow.analysis.AnalysisOutcome
import com.salihraphics.meow.analysis.key.AnswerKeyParseResult
import com.salihraphics.meow.analysis.key.AnswerNote
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
        repo.recordAttempt("doc", testId, 2, 0, "B", SelectionOrigin.RECOGNIZED, AttemptResult.CORRECT, "stroke-1", floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))
        val attempt = repo.observeAttempts("doc").first().single()
        assertEquals(AttemptResult.CORRECT, attempt.result)
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
}
