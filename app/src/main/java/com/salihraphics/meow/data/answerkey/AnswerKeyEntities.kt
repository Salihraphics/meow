package com.salihraphics.meow.data.answerkey

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One analysis per document content. [contentSha256] ties results to the exact file, so a
 * replaced or re-imported document never inherits another file's answers.
 */
@Entity(tableName = "document_analysis")
data class DocumentAnalysisEntity(
    @PrimaryKey val documentId: String,
    val contentSha256: String,
    val analyzerVersion: Int,
    val status: String,
    val keyFound: Boolean,
    val pageCount: Int,
    val textPages: Int,
    val ocrReads: Int,
    val startedAt: Long,
    val finishedAt: Long?,
    val error: String?,
    /** What the search saw, for explaining a miss: see [AnalysisClues]. */
    @ColumnInfo(defaultValue = "NULL") val clues: String? = null,
)

/** A separate answer-key PDF the student added for a book; kept privately, not in the library. */
@Entity(tableName = "key_sources", indices = [Index("documentId")])
data class KeySourceEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val displayName: String,
    val pageCount: Int,
    val addedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "key_tests", indices = [Index("documentId")])
data class KeyTestEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val sourceDocumentId: String,
    val label: String,
    val testNumber: Int?,
    val kind: String,
    val status: String,
    /** Comma-separated 0-based page indexes where the answers were printed. */
    val sourcePages: String,
    val firstQuestionPage: Int?,
    val lastQuestionPage: Int?,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "key_answers", indices = [Index("testId")])
data class KeyAnswerEntity(
    @PrimaryKey val id: String,
    val testId: String,
    val questionNumber: Int,
    val type: String,
    val answer: String,
    val tolerance: Double?,
    val origin: String,
    val confidence: String,
    val note: String?,
    /** Comma-separated alternatives for conflicts. */
    val alternatives: String?,
    val sourcePage: Int?,
    val updatedAt: Long,
    val deletedAt: Long?,
    /** Where the answer was read, see [com.salihraphics.meow.domain.model.AnswerSource.encode]. */
    @ColumnInfo(defaultValue = "NULL") val sources: String? = null,
)

/**
 * A student's answer to one question. Stroke and region link it to the mark on the page, for
 * the wrong-answer notebook and statistics later. [result] stays PENDING until the test is
 * checked. Several attempts at one question in a session are kept; the latest counts.
 */
@Entity(tableName = "answer_attempts", indices = [Index("documentId"), Index("testId"), Index("sessionId")])
data class AnswerAttemptEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val testId: String?,
    val questionNumber: Int,
    val pageIndex: Int,
    val selected: String,
    val selectionOrigin: String,
    val result: String,
    val strokeId: String?,
    val regionLeft: Float?,
    val regionTop: Float?,
    val regionRight: Float?,
    val regionBottom: Float?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    /** The solving session it was recorded in; null for attempts saved before sessions existed. */
    @ColumnInfo(defaultValue = "NULL") val sessionId: String? = null,
    /**
     * Unused since answers were made independent of drawings (erasing a circle keeps its answer);
     * kept so the v4 schema stays as installed.
     */
    @ColumnInfo(defaultValue = "0") val strokeRemoved: Boolean = false,
)

/**
 * One sitting of solving in a document: answers are collected while open and graded together
 * when the student checks. The counts are filled in then.
 */
@Entity(tableName = "solving_sessions", indices = [Index("documentId")])
data class SolvingSessionEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val startedAt: Long,
    val checkedAt: Long?,
    val correct: Int?,
    val incorrect: Int?,
    val unanswered: Int?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class KeySummaryRow(val documentId: String, val total: Int, val confirmed: Int)

data class KeyTestWithAnswers(
    @Embedded val test: KeyTestEntity,
    @Relation(parentColumn = "id", entityColumn = "testId") val answers: List<KeyAnswerEntity>,
)
