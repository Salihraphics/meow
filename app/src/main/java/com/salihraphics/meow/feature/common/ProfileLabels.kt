package com.salihraphics.meow.feature.common

import androidx.annotation.StringRes
import com.salihraphics.meow.R
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.ThemeMode

@get:StringRes
val ExamType.labelRes: Int
    get() = when (this) {
        ExamType.YKS -> R.string.exam_yks
        ExamType.LGS -> R.string.exam_lgs
        ExamType.SCHOOL -> R.string.exam_school
        ExamType.OTHER -> R.string.exam_other
    }

@get:StringRes
val ExamType.descriptionRes: Int
    get() = when (this) {
        ExamType.YKS -> R.string.exam_yks_desc
        ExamType.LGS -> R.string.exam_lgs_desc
        ExamType.SCHOOL -> R.string.exam_school_desc
        ExamType.OTHER -> R.string.exam_other_desc
    }

@get:StringRes
val Subject.labelRes: Int
    get() = when (this) {
        Subject.MATH -> R.string.subject_math
        Subject.GEOMETRY -> R.string.subject_geometry
        Subject.PHYSICS -> R.string.subject_physics
        Subject.CHEMISTRY -> R.string.subject_chemistry
        Subject.BIOLOGY -> R.string.subject_biology
        Subject.SCIENCE -> R.string.subject_science
        Subject.TURKISH -> R.string.subject_turkish
        Subject.LITERATURE -> R.string.subject_literature
        Subject.HISTORY -> R.string.subject_history
        Subject.REVOLUTION_HISTORY -> R.string.subject_revolution_history
        Subject.GEOGRAPHY -> R.string.subject_geography
        Subject.PHILOSOPHY -> R.string.subject_philosophy
        Subject.RELIGION -> R.string.subject_religion
        Subject.ENGLISH -> R.string.subject_english
    }

@get:StringRes
val StudyLevel.labelRes: Int
    get() = when (this) {
        StudyLevel.BEGINNER -> R.string.level_beginner
        StudyLevel.FOUNDATION -> R.string.level_foundation
        StudyLevel.MIXED -> R.string.level_mixed
        StudyLevel.REVIEW -> R.string.level_review
    }

@get:StringRes
val SessionLength.descriptionRes: Int
    get() = when (this) {
        SessionLength.MIN_15 -> R.string.session_15_desc
        SessionLength.MIN_30 -> R.string.session_30_desc
        SessionLength.MIN_45 -> R.string.session_45_desc
        SessionLength.MIN_60 -> R.string.session_60_desc
    }

@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
