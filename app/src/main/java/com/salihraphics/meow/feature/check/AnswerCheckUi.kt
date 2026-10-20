package com.salihraphics.meow.feature.check

/** What the viewer shows after a mark that might be an answer. */
sealed interface AnswerCheckUi {
    data class Result(val outcome: CheckOutcome.Graded) : AnswerCheckUi

    /** Which option did the student mean? Never graded until they say. */
    data class Ask(
        val question: Int,
        val candidates: List<String>,
        val options: List<String>,
        val pageIndex: Int,
        val strokeId: String,
        /** Left, top, right, bottom of the mark in page fractions. */
        val region: List<Float>,
    ) : AnswerCheckUi

    data object KeyNotConfirmed : AnswerCheckUi
    data object PageTestUnknown : AnswerCheckUi
    data class NoAnswer(val question: Int) : AnswerCheckUi
}
