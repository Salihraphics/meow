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

    @Query("UPDATE key_tests SET deletedAt = :now, updatedAt = :now WHERE documentId = :documentId AND status = 'DETECTED' AND deletedAt IS NULL")
    suspend fun deleteDetectedTests(documentId: String, now: Long)

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

    @Query("DELETE FROM document_analysis WHERE documentId = :documentId")
    suspend fun deleteAnalysis(documentId: String)
}
