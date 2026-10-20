package com.salihraphics.meow.feature.onboarding

enum class OnboardingStep {
    WELCOME,
    MEET,
    EXAM,
    SUBJECTS,
    LEVEL,
    SESSION,
    NAME,
    READY,
    ;

    /** 1-based position among the question steps, or null for intro/outro screens. */
    val questionNumber: Int?
        get() = if (this in QUESTIONS) QUESTIONS.indexOf(this) + 1 else null

    fun next(): OnboardingStep = entries.getOrElse(ordinal + 1) { READY }

    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)

    companion object {
        val QUESTIONS = listOf(EXAM, SUBJECTS, LEVEL, SESSION, NAME)
        val QUESTION_COUNT = QUESTIONS.size
    }
}
