package com.salihraphics.meow.domain.model

/** How a question is answered. Only CHOICE can be checked from a circle on the page today. */
enum class AnswerType { CHOICE, NUMBER, TEXT }

enum class AnswerOrigin { DETECTED, MANUAL }

enum class AnswerConfidence { HIGH, LOW }

enum class KeyTestStatus { DETECTED, CONFIRMED }

enum class KeyTestKind { KEY_SECTION, PAGE_MARGIN, IN_TEST, MANUAL }

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
    val clues: AnalysisClues = AnalysisClues(),
)

/**
 * What the key search saw, so a miss can be explained and acted on. Page indexes are 0-based.
 */
data class AnalysisClues(
    /** Pages with a heading like "Cevap Anahtarı". */
    val headingPages: List<Int> = emptyList(),
    /** Pages read as answer-key pages. */
    val keyPages: List<Int> = emptyList(),
    /** Lines that looked like answers anywhere. */
    val answerLines: Int = 0,
    /** Pages whose embedded text was unreadable (fonts without a character map). */
    val unreadablePages: List<Int> = emptyList(),
    /** Pages with no usable text at all (scans), counting unreadable ones. */
    val scannedPages: Int = 0,
    /** Pages read in full with OCR. */
    val ocrPages: List<Int> = emptyList(),
) {
    fun encode(): String = listOf(
        "h=" + headingPages.joinToString(","),
        "k=" + keyPages.joinToString(","),
        "l=$answerLines",
        "u=" + unreadablePages.joinToString(","),
        "s=$scannedPages",
        "o=" + ocrPages.joinToString(","),
    ).joinToString(";")

    companion object {
        fun decode(text: String?): AnalysisClues {
            if (text.isNullOrBlank()) return AnalysisClues()
            val parts = text.split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun pages(key: String) = parts[key].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
            return AnalysisClues(pages("h"), pages("k"), parts["l"]?.toIntOrNull() ?: 0, pages("u"), parts["s"]?.toIntOrNull() ?: 0, pages("o"))
        }
    }
}

/** A separate answer-key PDF added for a book. */
data class KeySource(val id: String, val displayName: String, val pageCount: Int)

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

/** PENDING: recorded while solving, graded when the student checks the test. */
enum class AttemptResult { CORRECT, INCORRECT, NEEDS_SELF_CHECK, PENDING }

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

/** Answers being collected in a document, not yet checked. [answers] are in the order given. */
data class SolvingSession(val id: String, val documentId: String, val startedAt: Long, val answers: List<AnswerAttempt>) {
    /**
     * The latest answer per question (a later circle replaces an earlier one). While a page's
     * test isn't known, the page tells the same number in two tests apart.
     */
    val latest: List<AnswerAttempt>
        get() = answers.groupBy { Triple(it.testId, if (it.testId == null) it.pageIndex else null, it.question) }
            .map { (_, list) -> list.last() }.sortedBy { it.question }
}
