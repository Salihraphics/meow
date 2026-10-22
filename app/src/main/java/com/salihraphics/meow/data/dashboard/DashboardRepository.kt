package com.salihraphics.meow.data.dashboard

import com.salihraphics.meow.data.annotation.StrokeDao
import com.salihraphics.meow.data.answerkey.AnswerKeyDao
import com.salihraphics.meow.data.study.StudyDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A note on some page, for the dashboard's "Notlarım". */
data class NoteSummary(
    val noteId: String,
    val documentId: String,
    val pageIndex: Int,
    /** Typed text; empty for a handwritten-only note. */
    val text: String,
    val hasInk: Boolean,
    val editedAt: Long,
)

/** One study-timer session that ended. */
data class StudyLog(val elapsedMs: Long, val completed: Boolean, val endedAt: Long)

/** One test the student checked ("Testi kontrol et"). */
data class CheckedTest(val checkedAt: Long, val correct: Int, val incorrect: Int, val unanswered: Int)

/**
 * Read-only views of what the student has actually done, for the home screen. Everything here
 * is already stored by the viewer, the timer and answer checking; nothing is estimated.
 */
interface DashboardRepository {
    fun recentNotes(limit: Int): Flow<List<NoteSummary>>
    fun studySince(since: Long): Flow<List<StudyLog>>
    fun checkedTestsSince(since: Long): Flow<List<CheckedTest>>
}

class RoomDashboardRepository(
    private val strokes: StrokeDao,
    private val study: StudyDao,
    private val keys: AnswerKeyDao,
) : DashboardRepository {
    override fun recentNotes(limit: Int) = strokes.observeRecentNotes(limit).map { rows ->
        rows.map { NoteSummary(it.id, it.documentId, it.pageIndex, it.text.trim(), it.inkCount > 0, it.editedAt) }
    }

    override fun studySince(since: Long) = study.observeSessionsSince(since).map { rows ->
        rows.map { StudyLog(it.elapsedMs, it.completed, it.endedAt) }
    }

    override fun checkedTestsSince(since: Long) = keys.observeCheckedSince(since).map { rows ->
        rows.mapNotNull { s ->
            s.checkedAt?.let { CheckedTest(it, s.correct ?: 0, s.incorrect ?: 0, s.unanswered ?: 0) }
        }
    }
}
