package com.salihraphics.meow.feature.check

/** What the viewer shows after a mark that might be an answer. */
sealed interface AnswerCheckUi {
    /** Saved for checking later; correctness isn't shown (the default way of solving). */
    data class Recorded(val question: Int, val letter: String, val attemptId: String) : AnswerCheckUi

    /** Immediate feedback (optional setting): graded right away. */
    data class Result(val outcome: CheckOutcome.Graded) : AnswerCheckUi

    /**
     * Which option did the student mean, and for which question if [question] is null? Nothing
     * is saved until they say.
     */
    data class Ask(
        val question: Int?,
        val candidates: List<String>,
        val options: List<String>,
        /** Question numbers to offer when [question] is unknown, likeliest first. */
        val questionChoices: List<Int>,
        val pageIndex: Int,
        val strokeId: String,
        /** Left, top, right, bottom of the mark in page fractions. */
        val region: List<Float>,
    ) : AnswerCheckUi

    // Immediate feedback only: why an answer couldn't be graded on the spot (it is still saved).
    data object KeyNotConfirmed : AnswerCheckUi
    data object PageTestUnknown : AnswerCheckUi
    data class NoAnswer(val question: Int) : AnswerCheckUi
}
