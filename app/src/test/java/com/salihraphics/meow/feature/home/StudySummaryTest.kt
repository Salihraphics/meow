package com.salihraphics.meow.feature.home

import com.salihraphics.meow.data.dashboard.CheckedTest
import com.salihraphics.meow.data.dashboard.StudyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StudySummaryTest {
    private val istanbul = TimeZone.getTimeZone("Europe/Istanbul")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) =
        Calendar.getInstance(istanbul).apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis
    private val min = 60_000L

    @Test
    fun weeksStartOnMondayAtMidnight() {
        // Thursday 22 Oct 2026 → Monday 19 Oct 2026.
        assertEquals(at(2026, 10, 19), StudySummary.startOfWeek(at(2026, 10, 22, 21, 8), istanbul))
        // Monday itself, and Sunday belongs to the week before.
        assertEquals(at(2026, 10, 19), StudySummary.startOfWeek(at(2026, 10, 19, 0, 0), istanbul))
        assertEquals(at(2026, 10, 12), StudySummary.startOfWeek(at(2026, 10, 18, 23, 59), istanbul))
        // Across a month and year boundary.
        assertEquals(at(2026, 12, 28), StudySummary.startOfWeek(at(2027, 1, 1, 10), istanbul))
    }

    @Test
    fun onlyRecordedTimeIsCounted() {
        val now = at(2026, 10, 22, 21, 0)
        val study = listOf(
            StudyLog(25 * min, completed = true, endedAt = at(2026, 10, 22, 9)),    // today
            StudyLog(10 * min, completed = false, endedAt = at(2026, 10, 20, 18)),  // this week
            StudyLog(50 * min, completed = true, endedAt = at(2026, 10, 18, 18)),   // last week
        )
        val tests = listOf(
            CheckedTest(at(2026, 10, 21, 17), correct = 30, incorrect = 8, unanswered = 2),
            CheckedTest(at(2026, 10, 10, 17), correct = 40, incorrect = 0, unanswered = 0),
        )
        val s = StudySummary.of(study, tests, now, istanbul)
        assertEquals(25 * min, s.todayMs)
        assertEquals(35 * min, s.weekMs)
        assertEquals(2, s.weekSessions)
        assertEquals(1, s.weekCompleted)
        assertEquals(1, s.weekTests)
        assertEquals(30, s.weekCorrect)
        assertEquals(8, s.weekIncorrect)
    }

    @Test
    fun nothingRecordedIsAnHonestEmptyState() {
        assertTrue(StudySummary.of(emptyList(), emptyList(), at(2026, 10, 22), istanbul).isEmpty)
        assertEquals(StudySummary(), StudySummary.of(emptyList(), emptyList(), at(2026, 10, 22), istanbul))
    }

    @Test
    fun relativeDaysCountCalendarDays() {
        val now = at(2026, 10, 22, 0, 30)
        assertEquals(0, calendarDaysBetween(at(2026, 10, 22, 0, 5), now, istanbul))
        assertEquals(1, calendarDaysBetween(at(2026, 10, 21, 23, 50), now, istanbul))
        assertEquals(7, calendarDaysBetween(at(2026, 10, 15, 12), now, istanbul))
    }
}
