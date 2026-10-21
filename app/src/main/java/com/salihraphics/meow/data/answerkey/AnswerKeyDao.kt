package com.salihraphics.meow.data.answerkey

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AnswerKeyDao {
    @Query("SELECT * FROM document_analysis WHERE documentId = :documentId")
    suspend fun analysis(documentId: String): DocumentAnalysisEntity?

    @Query("SELECT * FROM document_analysis")
    fun observeAnalyses(): Flow<List<DocumentAnalysisEntity>>

    @Query("SELECT * FROM document_analysis WHERE status IN ('PENDING', 'RUNNING')")
    suspend fun unfinishedAnalyses(): List<DocumentAnalysisEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAnalysis(entity: DocumentAnalysisEntity)

    @Transaction
    @Query("SELECT * FROM key_tests WHERE documentId = :documentId AND deletedAt IS NULL ORDER BY orderIndex")
    fun observeTests(documentId: String): Flow<List<KeyTestWithAnswers>>

    @Transaction
    @Query("SELECT * FROM key_tests WHERE documentId = :documentId AND deletedAt IS NULL ORDER BY orderIndex")
    suspend fun tests(documentId: String): List<KeyTestWithAnswers>

    @Transaction
    @Query("SELECT * FROM key_tests WHERE id = :testId")
    suspend fun testsContaining(testId: String): List<KeyTestWithAnswers>

    @Query("SELECT COUNT(*) FROM key_tests WHERE documentId = :documentId AND status = 'CONFIRMED' AND deletedAt IS NULL")
    suspend fun confirmedCount(documentId: String): Int

    @Query(
        "SELECT documentId, COUNT(*) AS total, SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed " +
            "FROM key_tests WHERE deletedAt IS NULL GROUP BY documentId",
    )
    fun observeSummaries(): Flow<List<KeySummaryRow>>

    @Insert
    suspend fun insertTest(test: KeyTestEntity)

    @Insert
    suspend fun insertAnswers(answers: List<KeyAnswerEntity>)

    @Insert
    suspend fun insertAnswer(answer: KeyAnswerEntity)

    /** Unconfirmed detections from one source (the book itself, or one key PDF); confirmed tests stay. */
    @Query(
        "UPDATE key_tests SET deletedAt = :now, updatedAt = :now WHERE documentId = :documentId AND sourceDocumentId = :sourceId " +
            "AND status = 'DETECTED' AND kind != 'MANUAL' AND deletedAt IS NULL",
    )
    suspend fun deleteDetectedTests(documentId: String, sourceId: String, now: Long)

    @Insert
    suspend fun insertKeySource(source: KeySourceEntity)

    @Query("SELECT * FROM key_sources WHERE documentId = :documentId AND deletedAt IS NULL ORDER BY addedAt")
    fun observeKeySources(documentId: String): Flow<List<KeySourceEntity>>

    @Query("UPDATE key_sources SET deletedAt = :now WHERE id = :id")
    suspend fun deleteKeySource(id: String, now: Long)

    @Query("UPDATE key_sources SET deletedAt = :now WHERE documentId = :documentId AND deletedAt IS NULL")
    suspend fun deleteKeySources(documentId: String, now: Long)

    @Query("UPDATE key_tests SET deletedAt = :now, updatedAt = :now WHERE documentId = :documentId AND deletedAt IS NULL")
    suspend fun deleteAllTests(documentId: String, now: Long)

    @Query("UPDATE key_tests SET deletedAt = :now, updatedAt = :now WHERE id = :testId")
    suspend fun deleteTest(testId: String, now: Long)

    @Query("UPDATE key_tests SET label = :label, firstQuestionPage = :first, lastQuestionPage = :last, updatedAt = :now WHERE id = :testId")
    suspend fun updateTest(testId: String, label: String, first: Int?, last: Int?, now: Long)

    @Query("UPDATE key_tests SET status = 'CONFIRMED', updatedAt = :now WHERE documentId = :documentId AND deletedAt IS NULL")
    suspend fun confirmAll(documentId: String, now: Long)

    @Query(
        "UPDATE key_answers SET type = :type, answer = :answer, tolerance = :tolerance, origin = 'MANUAL', " +
            "confidence = 'HIGH', note = NULL, alternatives = NULL, updatedAt = :now WHERE id = :answerId",
    )
    suspend fun correctAnswer(answerId: String, type: String, answer: String, tolerance: Double?, now: Long)

    @Query("UPDATE key_answers SET deletedAt = :now, updatedAt = :now WHERE id = :answerId")
    suspend fun deleteAnswer(answerId: String, now: Long)

    @Insert
    suspend fun insertAttempt(attempt: AnswerAttemptEntity)

    @Query("SELECT * FROM answer_attempts WHERE documentId = :documentId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeAttempts(documentId: String): Flow<List<AnswerAttemptEntity>>

    @Query("UPDATE answer_attempts SET deletedAt = :now, updatedAt = :now WHERE documentId = :documentId AND deletedAt IS NULL")
    suspend fun deleteAttempts(documentId: String, now: Long)

    @Query("SELECT * FROM solving_sessions WHERE documentId = :documentId AND checkedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSession(documentId: String): SolvingSessionEntity?

    @Query("SELECT * FROM solving_sessions WHERE documentId = :documentId AND checkedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpenSession(documentId: String): Flow<SolvingSessionEntity?>

    @Insert
    suspend fun insertSession(session: SolvingSessionEntity)

    @Query(
        "UPDATE solving_sessions SET checkedAt = :now, correct = :correct, incorrect = :incorrect, unanswered = :unanswered, " +
            "updatedAt = :now WHERE id = :sessionId",
    )
    suspend fun finishSession(sessionId: String, correct: Int, incorrect: Int, unanswered: Int, now: Long)

    @Query("UPDATE solving_sessions SET deletedAt = :now, updatedAt = :now WHERE documentId = :documentId AND deletedAt IS NULL")
    suspend fun deleteSessions(documentId: String, now: Long)

    @Query("SELECT * FROM answer_attempts WHERE sessionId = :sessionId AND deletedAt IS NULL AND strokeRemoved = 0 ORDER BY createdAt, rowid")
    suspend fun sessionAttempts(sessionId: String): List<AnswerAttemptEntity>

    @Query("SELECT * FROM answer_attempts WHERE sessionId = :sessionId AND deletedAt IS NULL AND strokeRemoved = 0 ORDER BY createdAt, rowid")
    fun observeSessionAttempts(sessionId: String): Flow<List<AnswerAttemptEntity>>

    @Query("UPDATE answer_attempts SET deletedAt = :now, updatedAt = :now WHERE id = :attemptId")
    suspend fun deleteAttempt(attemptId: String, now: Long)

    @Query("UPDATE answer_attempts SET result = :result, testId = :testId, updatedAt = :now WHERE id = :attemptId")
    suspend fun setResult(attemptId: String, testId: String?, result: String, now: Long)

    @Query("DELETE FROM document_analysis WHERE documentId = :documentId")
    suspend fun deleteAnalysis(documentId: String)
}
