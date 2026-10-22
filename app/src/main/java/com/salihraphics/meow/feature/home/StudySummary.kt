package com.salihraphics.meow.feature.home

import com.salihraphics.meow.data.dashboard.CheckedTest
import com.salihraphics.meow.data.dashboard.StudyLog
import java.util.Calendar
import java.util.TimeZone

/** This week's real study activity. All zeros means nothing has been recorded yet. */
data class StudySummary(
    val todayMs: Long = 0,
    val weekMs: Long = 0,
    val weekSessions: Int = 0,
    val weekCompleted: Int = 0,
    val weekTests: Int = 0,
    val weekCorrect: Int = 0,
    val weekIncorrect: Int = 0,
) {
    val isEmpty: Boolean get() = weekSessions == 0 && weekTests == 0

    companion object {
        /** Today and this week (from Monday) by the device's calendar. */
        fun of(study: List<StudyLog>, tests: List<CheckedTest>, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): StudySummary {
            val today = startOfDay(nowMillis, zone)
            val week = startOfWeek(nowMillis, zone)
            val weekStudy = study.filter { it.endedAt in week..nowMillis }
            val weekTests = tests.filter { it.checkedAt in week..nowMillis }
            return StudySummary(
                todayMs = weekStudy.filter { it.endedAt >= today }.sumOf { it.elapsedMs },
                weekMs = weekStudy.sumOf { it.elapsedMs },
                weekSessions = weekStudy.size,
                weekCompleted = weekStudy.count { it.completed },
                weekTests = weekTests.size,
                weekCorrect = weekTests.sumOf { it.correct },
                weekIncorrect = weekTests.sumOf { it.incorrect },
            )
        }

        fun startOfDay(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
            Calendar.getInstance(zone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        /** Monday 00:00, the start of the week in Türkiye. */
        fun startOfWeek(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
            val c = Calendar.getInstance(zone).apply { timeInMillis = startOfDay(nowMillis, zone) }
            val sinceMonday = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            c.add(Calendar.DAY_OF_MONTH, -sinceMonday)
            return c.timeInMillis
        }
    }
}
