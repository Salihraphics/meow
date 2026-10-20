package com.salihraphics.meow.domain.model

enum class ExamType { YKS, LGS, SCHOOL, OTHER }

enum class Subject {
    MATH,
    GEOMETRY,
    PHYSICS,
    CHEMISTRY,
    BIOLOGY,
    SCIENCE,
    TURKISH,
    LITERATURE,
    HISTORY,
    REVOLUTION_HISTORY,
    GEOGRAPHY,
    PHILOSOPHY,
    RELIGION,
    ENGLISH,
}

/** Self-described starting point. Used for personalization only, never as a grade. */
enum class StudyLevel { BEGINNER, FOUNDATION, MIXED, REVIEW }

enum class SessionLength(val minutes: Int) {
    MIN_15(15),
    MIN_30(30),
    MIN_45(45),
    MIN_60(60),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * What the student chose to tell MEOW about themselves. Every field is optional:
 * a profile with nothing filled in is a perfectly valid profile.
 */
data class StudyProfile(
    val exam: ExamType? = null,
    val subjects: Set<Subject> = emptySet(),
    val level: StudyLevel? = null,
    val sessionLength: SessionLength? = null,
    val displayName: String? = null,
) {
    val isEmpty: Boolean
        get() = exam == null && subjects.isEmpty() && level == null &&
            sessionLength == null && displayName == null

    companion object {
        const val MAX_NAME_LENGTH = 30

        /** Trims and collapses whitespace; a blank name means "no name". */
        fun sanitizeName(raw: String?): String? =
            raw?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.take(MAX_NAME_LENGTH)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
    }
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val profile: StudyProfile = StudyProfile(),
    val mascotAnimationsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
