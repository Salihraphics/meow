package com.salihraphics.meow.domain.model

/** How a question is answered. Only CHOICE can be checked from a circle on the page today. */
enum class AnswerType { CHOICE, NUMBER, TEXT }

enum class AnswerOrigin { DETECTED, MANUAL }

enum class AnswerConfidence { HIGH, LOW }

enum class KeyTestStatus { DETECTED, CONFIRMED }

enum class KeyTestKind { KEY_SECTION, PAGE_MARGIN, MANUAL }

enum class AnalysisStatus { PENDING, RUNNING, DONE, FAILED }

data class AnalysisInfo(
    val documentId: String,
    val status: AnalysisStatus,
    val keyFound: Boolean,
    val pageCount: Int,
    val textPages: Int,
    val ocrReads: Int,
    val finishedAt: Long?,
    val error: String?,
)

data class KeyAnswer(
    val id: String,
    val testId: String,
    val question: Int,
    val type: AnswerType,
    /** "C", "12.5", or a short text. */
    val value: String,
    /** For NUMBER answers: accepted absolute difference. */
    val tolerance: Double?,
    val origin: AnswerOrigin,
    val confidence: AnswerConfidence,
    /** Why it needs a look (e.g. "CONFLICT", "OCR_GUESS"); null when nothing is wrong. */
    val note: String?,
    val alternatives: List<String>,
    val sourcePage: Int?,
) {
    val needsReview: Boolean get() = confidence == AnswerConfidence.LOW || note != null
}

data class KeyTest(
    val id: String,
    val documentId: String,
    /** The document the answers came from; may differ from [documentId] (key in another PDF). */
    val sourceDocumentId: String,
    val label: String,
    val number: Int?,
    val kind: KeyTestKind,
    val status: KeyTestStatus,
    val sourcePages: List<Int>,
    /** Pages with this test's questions (0-based, inclusive), if known. */
    val questionPages: IntRange?,
    val answers: List<KeyAnswer>,
) {
    val isConfirmed get() = status == KeyTestStatus.CONFIRMED
    val unresolvedConflicts get() = answers.count { it.note == "CONFLICT" }
}

/** What the library shows about a document's answer key. */
sealed interface KeyStatus {
    data class Searching(val progress: Float) : KeyStatus
    data class Found(val tests: Int, val confirmed: Boolean) : KeyStatus
    data object NotFound : KeyStatus
    data object Failed : KeyStatus
}

enum class AttemptResult { CORRECT, INCORRECT, NEEDS_SELF_CHECK }

/** How the selected answer was decided. */
enum class SelectionOrigin { RECOGNIZED, CHOSEN_BY_STUDENT }

data class AnswerAttempt(
    val id: String,
    val documentId: String,
    val testId: String?,
    val question: Int,
    val pageIndex: Int,
    val selected: String,
    val origin: SelectionOrigin,
    val result: AttemptResult,
    val strokeId: String?,
    val createdAt: Long,
)
