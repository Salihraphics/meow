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
import com.salihraphics.meow.domain.model.KeyTest
import com.salihraphics.meow.domain.model.KeyTestKind
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.domain.model.KeyTestStatus
import com.salihraphics.meow.domain.model.SelectionOrigin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
     * half-written key. A confirmed key is never replaced by a new detection.
     */
    suspend fun saveOutcome(documentId: String, contentSha256: String, outcome: AnalysisOutcome) {
        db.withTransaction {
            val time = now()
            if (dao.confirmedCount(documentId) == 0) {
                dao.deleteDetectedTests(documentId, time)
                outcome.result.tests.forEachIndexed { index, detected ->
                    val testId = newId()
                    dao.insertTest(
                        KeyTestEntity(
                            id = testId,
                            documentId = documentId,
                            sourceDocumentId = documentId,
                            label = detected.ref?.label ?: "",
                            testNumber = detected.ref?.number,
                            kind = when (detected.kind) {
                                KeySourceKind.KEY_SECTION -> KeyTestKind.KEY_SECTION
                                KeySourceKind.PAGE_MARGIN -> KeyTestKind.PAGE_MARGIN
                            }.name,
                            status = KeyTestStatus.DETECTED.name,
                            sourcePages = detected.sourcePages.joinToString(","),
                            firstQuestionPage = detected.questionPages.minOrNull(),
                            lastQuestionPage = detected.questionPages.maxOrNull(),
                            orderIndex = index,
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
                }
            }
            val started = dao.analysis(documentId)?.startedAt ?: time
            dao.putAnalysis(
                DocumentAnalysisEntity(
                    documentId, contentSha256, ANALYZER_VERSION, AnalysisStatus.DONE.name, outcome.result.found,
                    outcome.pageCount, outcome.textPages, outcome.ocrReads, started, time, null,
                ),
            )
        }
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

    suspend fun recordAttempt(
        documentId: String,
        testId: String?,
        question: Int,
        pageIndex: Int,
        selected: String,
        origin: SelectionOrigin,
        result: AttemptResult,
        strokeId: String?,
        region: FloatArray?,
    ): String {
        val id = newId()
        val time = now()
        dao.insertAttempt(
            AnswerAttemptEntity(id, documentId, testId, question, pageIndex, selected, origin.name, result.name, strokeId,
                region?.get(0), region?.get(1), region?.get(2), region?.get(3), time, time, null),
        )
        return id
    }

    fun observeAttempts(documentId: String): Flow<List<AnswerAttempt>> = dao.observeAttempts(documentId).map { rows ->
        rows.map {
            AnswerAttempt(it.id, it.documentId, it.testId, it.questionNumber, it.pageIndex, it.selected,
                SelectionOrigin.valueOf(it.selectionOrigin), AttemptResult.valueOf(it.result), it.strokeId, it.createdAt)
        }
    }

    /** A document leaving the library takes its keys and attempts with it. */
    suspend fun removeDocument(documentId: String) {
        db.withTransaction {
            val time = now()
            dao.deleteAllTests(documentId, time)
            dao.deleteAttempts(documentId, time)
            dao.deleteAnalysis(documentId)
        }
    }

    companion object {
        /** Bump when detection improves enough to be worth re-running on old documents. */
        const val ANALYZER_VERSION = 1
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
    documentId, AnalysisStatus.valueOf(status), keyFound, pageCount, textPages, ocrReads, finishedAt, error,
)
