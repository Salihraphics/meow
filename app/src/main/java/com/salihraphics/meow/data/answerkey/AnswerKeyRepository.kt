package com.salihraphics.meow.data.answerkey

import androidx.room.withTransaction
import com.salihraphics.meow.analysis.AnalysisOutcome
import com.salihraphics.meow.analysis.key.Confidence
import com.salihraphics.meow.analysis.key.KeySourceKind
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.domain.model.AnalysisInfo
import com.salihraphics.meow.domain.model.AnalysisStatus
import com.salihraphics.meow.domain.model.AnswerAttempt
import com.salihraphics.meow.domain.model.AnswerConfidence
import com.salihraphics.meow.domain.model.AnswerOrigin
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.KeyAnswer
import com.salihraphics.meow.domain.model.KeySource
import com.salihraphics.meow.domain.model.AnalysisClues
import com.salihraphics.meow.domain.model.KeyTest
import com.salihraphics.meow.domain.model.KeyTestKind
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.domain.model.KeyTestStatus
import com.salihraphics.meow.domain.model.SelectionOrigin
import com.salihraphics.meow.domain.model.SolvingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

sealed interface ConfirmResult {
    data object Confirmed : ConfirmResult
    data object NothingToConfirm : ConfirmResult

    /** Questions listed with two different answers must be resolved first. */
    data class UnresolvedConflicts(val count: Int) : ConfirmResult
}

class AnswerKeyRepository(
    private val db: MeowDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.answerKeys()

    fun observeTests(documentId: String): Flow<List<KeyTest>> = dao.observeTests(documentId).map { rows -> rows.map { it.toDomain() } }

    suspend fun tests(documentId: String): List<KeyTest> = dao.tests(documentId).map { it.toDomain() }

    fun observeAnalyses(): Flow<Map<String, AnalysisInfo>> =
        dao.observeAnalyses().map { rows -> rows.associate { it.documentId to it.toDomain() } }

    /** Per-document key status for the library, merged with live analysis [progress]. */
    fun observeStatuses(progress: Flow<Map<String, Float>>): Flow<Map<String, KeyStatus>> =
        combine(dao.observeAnalyses(), dao.observeSummaries(), progress) { analyses, summaries, running ->
            val byDoc = summaries.associateBy { it.documentId }
            (analyses.map { it.documentId } + summaries.map { it.documentId } + running.keys).toSet().associateWith { id ->
                val summary = byDoc[id]
                val analysis = analyses.firstOrNull { it.documentId == id }
                when {
                    id in running -> KeyStatus.Searching(running.getValue(id))
                    summary != null && summary.total > 0 -> KeyStatus.Found(summary.total, summary.confirmed == summary.total)
                    analysis?.status == AnalysisStatus.FAILED.name -> KeyStatus.Failed
                    analysis?.status == AnalysisStatus.DONE.name -> KeyStatus.NotFound
                    else -> KeyStatus.Searching(0f)
                }
            }
        }

    suspend fun analysis(documentId: String): DocumentAnalysisEntity? = dao.analysis(documentId)

    suspend fun markStarted(documentId: String, contentSha256: String) {
        dao.putAnalysis(
            DocumentAnalysisEntity(documentId, contentSha256, ANALYZER_VERSION, AnalysisStatus.RUNNING.name, false, 0, 0, 0, now(), null, null),
        )
    }

    /**
     * Stores what was detected, all at once: a crash mid-way leaves the previous state, never a
     * half-written key. Tests the student confirmed or typed are never replaced, and a detection
     * repeating a confirmed test isn't added again.
     */
    suspend fun saveOutcome(documentId: String, contentSha256: String, outcome: AnalysisOutcome) {
        db.withTransaction {
            val time = now()
            dao.deleteDetectedTests(documentId, documentId, time)
            insertDetected(documentId, documentId, outcome, time)
            val started = dao.analysis(documentId)?.startedAt ?: time
            dao.putAnalysis(
                DocumentAnalysisEntity(
                    documentId, contentSha256, ANALYZER_VERSION, AnalysisStatus.DONE.name, outcome.result.found,
                    outcome.pageCount, outcome.textPages, outcome.ocrReads, started, time, null, outcome.clues.encode(),
                ),
            )
        }
    }

    /**
     * Stores the key read from pages the student pointed at. Earlier unconfirmed detections from
     * those pages are replaced; the rest of the key stays. Returns how many tests were found.
     */
    suspend fun saveKeyPagesOutcome(documentId: String, pages: Collection<Int>, outcome: AnalysisOutcome): Int = db.withTransaction {
        val time = now()
        dao.tests(documentId).map { it.test }
            .filter { it.status == KeyTestStatus.DETECTED.name && it.kind != KeyTestKind.MANUAL.name && it.sourceDocumentId == documentId }
            .filter { t -> t.sourcePages.split(',').mapNotNull { it.toIntOrNull() }.any { it in pages } }
            .forEach { dao.deleteTest(it.id, time) }
        insertDetected(documentId, documentId, outcome, time)
    }

    /** Stores the key read from a separate answer-key PDF added for [documentId]. Returns how many tests were found. */
    suspend fun saveKeySourceOutcome(documentId: String, sourceId: String, displayName: String, outcome: AnalysisOutcome): Int =
        db.withTransaction {
            val time = now()
            dao.deleteDetectedTests(documentId, sourceId, time)
            dao.insertKeySource(KeySourceEntity(sourceId, documentId, displayName, outcome.pageCount, time, null))
            insertDetected(documentId, sourceId, outcome, time)
        }

    fun observeKeySources(documentId: String): Flow<List<KeySource>> =
        dao.observeKeySources(documentId).map { rows -> rows.map { KeySource(it.id, it.displayName, it.pageCount) } }

    private suspend fun insertDetected(documentId: String, sourceId: String, outcome: AnalysisOutcome, time: Long): Int {
        val existing = dao.tests(documentId).map { it.test }
        val confirmed = existing.filter { it.status == KeyTestStatus.CONFIRMED.name }
        var added = 0
        outcome.result.tests.forEach { detected ->
            val label = detected.ref?.label ?: ""
            if (confirmed.any { it.label == label && it.testNumber == detected.ref?.number && label.isNotEmpty() }) return@forEach
            val testId = newId()
            dao.insertTest(
                KeyTestEntity(
                    id = testId,
                    documentId = documentId,
                    sourceDocumentId = sourceId,
                    label = label,
                    testNumber = detected.ref?.number,
                    kind = when (detected.kind) {
                        KeySourceKind.KEY_SECTION -> KeyTestKind.KEY_SECTION
                        KeySourceKind.PAGE_MARGIN -> KeyTestKind.PAGE_MARGIN
                        KeySourceKind.IN_TEST -> KeyTestKind.IN_TEST
                    }.name,
                    status = KeyTestStatus.DETECTED.name,
                    sourcePages = detected.sourcePages.joinToString(","),
                    // Question pages only make sense in the book itself.
                    firstQuestionPage = detected.questionPages.minOrNull().takeIf { sourceId == documentId },
                    lastQuestionPage = detected.questionPages.maxOrNull().takeIf { sourceId == documentId },
                    orderIndex = existing.size + added,
                    createdAt = time,
                    updatedAt = time,
                    deletedAt = null,
                ),
            )
            dao.insertAnswers(
                detected.answers.map { answer ->
                    KeyAnswerEntity(
                        id = newId(),
                        testId = testId,
                        questionNumber = answer.question,
                        type = AnswerType.CHOICE.name,
                        answer = answer.answer,
                        tolerance = null,
                        origin = AnswerOrigin.DETECTED.name,
                        confidence = if (answer.confidence == Confidence.HIGH) AnswerConfidence.HIGH.name else AnswerConfidence.LOW.name,
                        note = answer.note?.name,
                        alternatives = answer.alternatives.takeIf { it.isNotEmpty() }?.joinToString(","),
                        sourcePage = answer.sourcePage,
                        updatedAt = time,
                        deletedAt = null,
                    )
                },
            )
            added++
        }
        return added
    }

    suspend fun markFailed(documentId: String, contentSha256: String, error: String) {
        val started = dao.analysis(documentId)?.startedAt ?: now()
        dao.putAnalysis(DocumentAnalysisEntity(documentId, contentSha256, ANALYZER_VERSION, AnalysisStatus.FAILED.name, false, 0, 0, 0, started, now(), error))
    }

    /** Runs left RUNNING by a killed process; they are started again. */
    suspend fun interruptedRuns(): List<String> = dao.unfinishedAnalyses().map { it.documentId }

    suspend fun correctAnswer(answerId: String, type: AnswerType, value: String, tolerance: Double? = null) =
        dao.correctAnswer(answerId, type.name, value.trim(), tolerance, now())

    /** Adds (or replaces) the answer to [question] in [testId], as a manual entry. */
    suspend fun setAnswer(testId: String, question: Int, type: AnswerType, value: String, tolerance: Double? = null) {
        db.withTransaction {
            val existing = dao.testsContaining(testId).firstOrNull()?.answers?.firstOrNull { it.questionNumber == question && it.deletedAt == null }
            if (existing != null) {
                dao.correctAnswer(existing.id, type.name, value.trim(), tolerance, now())
            } else {
                dao.insertAnswer(
                    KeyAnswerEntity(newId(), testId, question, type.name, value.trim(), tolerance, AnswerOrigin.MANUAL.name,
                        AnswerConfidence.HIGH.name, null, null, null, now(), null),
                )
            }
        }
    }

    suspend fun deleteAnswer(answerId: String) = dao.deleteAnswer(answerId, now())

    suspend fun addManualTest(documentId: String, label: String, number: Int?): String {
        val id = newId()
        val time = now()
        val order = dao.tests(documentId).size
        dao.insertTest(
            KeyTestEntity(id, documentId, documentId, label.trim(), number, KeyTestKind.MANUAL.name, KeyTestStatus.DETECTED.name, "",
                null, null, order, time, time, null),
        )
        return id
    }

    suspend fun updateTest(testId: String, label: String, questionPages: IntRange?) =
        dao.updateTest(testId, label.trim(), questionPages?.first, questionPages?.last, now())

    suspend fun deleteTest(testId: String) = dao.deleteTest(testId, now())

    /** The student's go-ahead: from now on these answers are used for checking. */
    suspend fun confirm(documentId: String): ConfirmResult = db.withTransaction {
        val tests = dao.tests(documentId).map { it.toDomain() }
        when {
            tests.none { it.answers.isNotEmpty() } -> ConfirmResult.NothingToConfirm
            tests.sumOf { it.unresolvedConflicts } > 0 -> ConfirmResult.UnresolvedConflicts(tests.sumOf { it.unresolvedConflicts })
            else -> {
                dao.confirmAll(documentId, now())
                ConfirmResult.Confirmed
            }
        }
    }

    fun observeAttempts(documentId: String): Flow<List<AnswerAttempt>> = dao.observeAttempts(documentId).map { rows -> rows.map { it.toDomain() } }

    /**
     * Records the student's answer in the document's open solving session (starting one if
     * needed). [result] is PENDING unless immediate feedback graded it already.
     */
    suspend fun recordSelection(
        documentId: String,
        testId: String?,
        question: Int,
        pageIndex: Int,
        selected: String,
        origin: SelectionOrigin,
        strokeId: String?,
        region: FloatArray?,
        result: AttemptResult = AttemptResult.PENDING,
    ): String = db.withTransaction {
        val time = now()
        val session = dao.openSession(documentId)
            ?: SolvingSessionEntity(newId(), documentId, time, null, null, null, null, time, null).also { dao.insertSession(it) }
        // The same stroke reported twice is one answer, not two.
        if (strokeId != null) dao.sessionAttempts(session.id).firstOrNull { it.strokeId == strokeId }?.let { return@withTransaction it.id }
        val id = newId()
        dao.insertAttempt(
            AnswerAttemptEntity(id, documentId, testId, question, pageIndex, selected, origin.name, result.name, strokeId,
                region?.get(0), region?.get(1), region?.get(2), region?.get(3), time, time, null, sessionId = session.id),
        )
        id
    }

    suspend fun openSession(documentId: String): SolvingSession? {
        val session = dao.openSession(documentId) ?: return null
        return SolvingSession(session.id, documentId, session.startedAt, dao.sessionAttempts(session.id).map { it.toDomain() })
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeOpenSession(documentId: String): Flow<SolvingSession?> = dao.observeOpenSession(documentId).flatMapLatest { session ->
        if (session == null) flowOf(null)
        else dao.observeSessionAttempts(session.id).map { rows -> SolvingSession(session.id, documentId, session.startedAt, rows.map { it.toDomain() }) }
    }

    /** Drops one recorded answer ("Geri al" right after marking); an earlier answer to the question counts again. */
    suspend fun removeAnswer(attemptId: String) = dao.deleteAttempt(attemptId, now())

    /**
     * Makes a question unanswered again in an open session: every answer recorded for it there
     * (same test, or same page when the test isn't known) is dropped.
     */
    suspend fun clearQuestion(sessionId: String, answer: AnswerAttempt) = db.withTransaction {
        val time = now()
        dao.sessionAttempts(sessionId)
            .filter { it.questionNumber == answer.question && it.testId == answer.testId && (it.testId != null || it.pageIndex == answer.pageIndex) }
            .forEach { dao.deleteAttempt(it.id, time) }
    }

    /** Stores each answer's result and closes the session; the next answer starts a new one. */
    suspend fun finishSession(sessionId: String, graded: List<GradedAttempt>, correct: Int, incorrect: Int, unanswered: Int) {
        db.withTransaction {
            val time = now()
            graded.forEach { dao.setResult(it.attemptId, it.testId, it.result.name, time) }
            dao.finishSession(sessionId, correct, incorrect, unanswered, time)
        }
    }

    data class GradedAttempt(val attemptId: String, val testId: String, val result: AttemptResult)

    /** A document leaving the library takes its keys and attempts with it. */
    suspend fun removeDocument(documentId: String) {
        db.withTransaction {
            val time = now()
            dao.deleteAllTests(documentId, time)
            dao.deleteAttempts(documentId, time)
            dao.deleteSessions(documentId, time)
            dao.deleteKeySources(documentId, time)
            dao.deleteAnalysis(documentId)
        }
    }

    companion object {
        /** Bump when detection improves enough to be worth re-running on old documents. */
        const val ANALYZER_VERSION = 3
    }
}

private fun KeyTestWithAnswers.toDomain() = KeyTest(
    id = test.id,
    documentId = test.documentId,
    sourceDocumentId = test.sourceDocumentId,
    label = test.label,
    number = test.testNumber,
    kind = KeyTestKind.valueOf(test.kind),
    status = KeyTestStatus.valueOf(test.status),
    sourcePages = test.sourcePages.split(',').mapNotNull { it.trim().toIntOrNull() },
    questionPages = if (test.firstQuestionPage != null && test.lastQuestionPage != null) test.firstQuestionPage..test.lastQuestionPage else null,
    answers = answers.filter { it.deletedAt == null }.sortedBy { it.questionNumber }.map { a ->
        KeyAnswer(
            id = a.id,
            testId = a.testId,
            question = a.questionNumber,
            type = AnswerType.valueOf(a.type),
            value = a.answer,
            tolerance = a.tolerance,
            origin = AnswerOrigin.valueOf(a.origin),
            confidence = AnswerConfidence.valueOf(a.confidence),
            note = a.note,
            alternatives = a.alternatives?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            sourcePage = a.sourcePage,
        )
    },
)

private fun DocumentAnalysisEntity.toDomain() = AnalysisInfo(
    documentId, AnalysisStatus.valueOf(status), keyFound, pageCount, textPages, ocrReads, finishedAt, error, AnalysisClues.decode(clues),
)

private fun AnswerAttemptEntity.toDomain() = AnswerAttempt(
    id, documentId, testId, questionNumber, pageIndex, selected,
    SelectionOrigin.valueOf(selectionOrigin), AttemptResult.valueOf(result), strokeId, createdAt,
)
