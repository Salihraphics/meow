package com.salihraphics.meow.testing

import com.salihraphics.meow.data.dashboard.CheckedTest
import com.salihraphics.meow.data.dashboard.DashboardRepository
import com.salihraphics.meow.data.dashboard.NoteSummary
import com.salihraphics.meow.data.dashboard.StudyLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDashboardRepository : DashboardRepository {
    val notes = MutableStateFlow<List<NoteSummary>>(emptyList())
    val study = MutableStateFlow<List<StudyLog>>(emptyList())
    val tests = MutableStateFlow<List<CheckedTest>>(emptyList())
    val studyQueries = mutableListOf<Long>()

    override fun recentNotes(limit: Int): Flow<List<NoteSummary>> = notes.map { it.take(limit) }
    override fun studySince(since: Long): Flow<List<StudyLog>> {
        studyQueries += since
        return study.map { list -> list.filter { it.endedAt >= since } }
    }
    override fun checkedTestsSince(since: Long): Flow<List<CheckedTest>> = tests.map { list -> list.filter { it.checkedAt >= since } }
}
