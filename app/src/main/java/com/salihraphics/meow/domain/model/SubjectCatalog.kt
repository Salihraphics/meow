package com.salihraphics.meow.domain.model

/** Which subjects make sense to offer for a given exam. */
object SubjectCatalog {

    private val yks = listOf(
        Subject.MATH,
        Subject.GEOMETRY,
        Subject.PHYSICS,
        Subject.CHEMISTRY,
        Subject.BIOLOGY,
        Subject.TURKISH,
        Subject.LITERATURE,
        Subject.HISTORY,
        Subject.GEOGRAPHY,
        Subject.PHILOSOPHY,
        Subject.RELIGION,
        Subject.ENGLISH,
    )

    private val lgs = listOf(
        Subject.MATH,
        Subject.SCIENCE,
        Subject.TURKISH,
        Subject.REVOLUTION_HISTORY,
        Subject.RELIGION,
        Subject.ENGLISH,
    )

    /** School exams, "other" and "not specified" get the full list. */
    private val general = Subject.entries.toList()

    fun subjectsFor(exam: ExamType?): List<Subject> = when (exam) {
        ExamType.YKS -> yks
        ExamType.LGS -> lgs
        ExamType.SCHOOL, ExamType.OTHER, null -> general
    }

    /** Drops selections that no longer apply after the exam changes. */
    fun retainAvailable(selected: Set<Subject>, exam: ExamType?): Set<Subject> {
        val available = subjectsFor(exam).toSet()
        return selected.filterTo(LinkedHashSet()) { it in available }
    }
}
