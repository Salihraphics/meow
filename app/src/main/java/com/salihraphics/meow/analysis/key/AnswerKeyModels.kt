package com.salihraphics.meow.analysis.key

/** A test as the book names it: "Test 5", "5. Deneme", or unnamed. */
data class TestRef(val number: Int?, val label: String)

enum class Confidence { HIGH, LOW }

/** Where a key was found. */
enum class KeySourceKind {
    /** A dedicated answer-key section ("Cevap Anahtarı", answer grids), often at the end. */
    KEY_SECTION,

    /** Compact answers printed in a page's header or footer for that page's questions. */
    PAGE_MARGIN,
}

enum class AnswerNote {
    /** OCR-looking characters were read as an answer ("5.8" → B); needs a look. */
    OCR_GUESS,

    /** The same question was listed with different answers; [DetectedAnswer.alternatives] has them. */
    CONFLICT,
}

data class DetectedAnswer(
    val question: Int,
    /** "A".."E" for choices. Stored as text so numeric and written answers fit the same model. */
    val answer: String,
    val confidence: Confidence,
    val sourcePage: Int,
    val note: AnswerNote? = null,
    val alternatives: List<String> = emptyList(),
)

sealed interface TestIssue {
    /** Numbers inside the test's range with no answer found. They are never filled in. */
    data class Missing(val questions: List<Int>) : TestIssue
    data class Conflict(val question: Int, val answers: List<String>) : TestIssue

    /** Fewer than three answers: possibly a coincidence rather than a key. */
    data object TooFew : TestIssue

    /** Question numbers went backwards inside one test. */
    data object OutOfOrder : TestIssue
}

data class DetectedTest(
    val ref: TestRef?,
    val kind: KeySourceKind,
    /** Pages where the answers were printed. */
    val sourcePages: List<Int>,
    /** Pages holding this test's questions, when they could be identified. */
    val questionPages: List<Int>,
    val answers: List<DetectedAnswer>,
    val issues: List<TestIssue>,
) {
    val needsReview: Boolean get() = issues.isNotEmpty() || answers.any { it.confidence == Confidence.LOW }
}

data class AnswerKeyParseResult(val tests: List<DetectedTest>) {
    val found: Boolean get() = tests.isNotEmpty()
}
