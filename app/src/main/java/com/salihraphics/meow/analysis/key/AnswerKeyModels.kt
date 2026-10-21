package com.salihraphics.meow.analysis.key

/**
 * A test as the book names it: "Test 5", "5. Deneme". Books split into days or units repeat test
 * numbers ("1. GÜN · TEST 2", "2. GÜN · TEST 2"), so [section] ("gun 1") keeps them apart; a
 * section with no tests under it is itself the test (number null).
 */
data class TestRef(val number: Int?, val label: String, val section: String? = null)

enum class Confidence { HIGH, LOW }

/** Where a key was found. */
enum class KeySourceKind {
    /** A dedicated answer-key section ("Cevap Anahtarı", answer grids), often at the end. */
    KEY_SECTION,

    /** Compact answers printed in a page's header or footer for that page's questions. */
    PAGE_MARGIN,

    /** An answer table printed among the questions, usually at the end of each test. */
    IN_TEST,
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

data class AnswerKeyParseResult(val tests: List<DetectedTest>, val clues: KeyClues = KeyClues()) {
    val found: Boolean get() = tests.isNotEmpty()
}

/** What the parser saw, so "no key found" can say why. Page indexes are 0-based. */
data class KeyClues(
    /** Pages with a heading like "Cevap Anahtarı". */
    val headingPages: List<Int> = emptyList(),
    /** Pages read as answer-key pages. */
    val keyPages: List<Int> = emptyList(),
    /** Lines that looked like answers ("5. C 6. A …") anywhere in the book. */
    val answerLines: Int = 0,
)
