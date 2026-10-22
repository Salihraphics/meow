package com.salihraphics.meow.domain

import com.salihraphics.meow.domain.model.Countdown
import com.salihraphics.meow.domain.model.ExamCountdown
import com.salihraphics.meow.domain.model.ExamDate
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.ExamGoalType
import com.salihraphics.meow.domain.model.ExamSession
import com.salihraphics.meow.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExamCountdownTest {
    private val istanbul = TimeZone.getTimeZone("Europe/Istanbul")
    private val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")

    private fun at(zone: TimeZone, y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0, ms: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(y, mo - 1, d, h, mi, s)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis

    @Test
    fun theProvidedTargetsAreTheDefaults() {
        assertEquals(ExamDate(2027, 6, 19, 10, 15), ExamSession.TYT.defaultDate)
        assertEquals(ExamDate(2027, 6, 20, 10, 15), ExamSession.AYT_YDT.defaultDate)
        assertEquals(ExamDate(2027, 6, 13, 9, 30), ExamSession.LGS.defaultDate)
        // YKS has two sittings on different days; LGS one.
        assertEquals(listOf(ExamSession.TYT, ExamSession.AYT_YDT), ExamGoal(ExamGoalType.YKS).sessions)
        assertEquals(listOf(ExamSession.LGS), ExamGoal(ExamGoalType.LGS).sessions)
        assertTrue(ExamGoal(ExamGoalType.YKS).isEstimated(ExamSession.TYT))
    }

    @Test
    fun daysHoursMinutesSecondsFromTheTargetMoment() {
        val target = at(istanbul, 2027, 6, 19, 10, 15)
        val now = at(istanbul, 2026, 10, 22, 21, 8, 14)
        val c = ExamCountdown.countdown(target, now)!!
        // 22 Oct 2026 21:08:14 → 19 Jun 2027 10:15:00
        assertEquals(Countdown(239, 13, 6, 46), c)
    }

    @Test
    fun secondsRoundUpSoZeroArrivesExactlyAtTheStart() {
        val target = at(istanbul, 2027, 6, 13, 9, 30)
        assertEquals(Countdown(0, 0, 0, 1), ExamCountdown.countdown(target, target - 400))
        assertEquals(Countdown(0, 0, 0, 1), ExamCountdown.countdown(target, target - 1_000))
        assertNull(ExamCountdown.countdown(target, target))
        assertNull("never negative", ExamCountdown.countdown(target, target + 5_000))
    }

    @Test
    fun calendarDaysCountLocalDatesNotTwentyFourHourBlocks() {
        val exam = ExamDate(2027, 6, 19, 10, 15)
        // Late in the evening the day before: 1 day, though only ~12 hours remain.
        assertEquals(1, ExamCountdown.calendarDaysUntil(exam, at(istanbul, 2027, 6, 18, 23, 59, 59), istanbul))
        // Just after midnight on the day: 0.
        assertEquals(0, ExamCountdown.calendarDaysUntil(exam, at(istanbul, 2027, 6, 19, 0, 0, 1), istanbul))
        // 23:30 in Istanbul is still 20:30 UTC on the same date: no UTC off-by-one.
        assertEquals(1, ExamCountdown.calendarDaysUntil(exam, at(istanbul, 2027, 6, 18, 23, 30), istanbul))
        assertEquals(0, ExamCountdown.calendarDaysUntil(exam, at(istanbul, 2027, 7, 1), istanbul))
    }

    @Test
    fun aZoneWithDaylightSavingStillCountsWholeDays() {
        // Across the March 2027 change in Los Angeles: 14 Mar 2027 has 23 hours.
        val exam = ExamDate(2027, 3, 20, 9, 0)
        assertEquals(10, ExamCountdown.calendarDaysUntil(exam, at(losAngeles, 2027, 3, 10, 22), losAngeles))
        // The live countdown is real elapsed time: one hour shorter than wall-clock arithmetic.
        val c = ExamCountdown.countdown(exam.toMillis(losAngeles), at(losAngeles, 2027, 3, 10, 9))!!
        assertEquals(Countdown(9, 23, 0, 0), c)
    }

    @Test
    fun leapYearsHaveTheirExtraDay() {
        // 2028 is a leap year: 1 Feb → 1 Mar is 29 days; in 2027 it is 28.
        assertEquals(29, ExamCountdown.calendarDaysUntil(ExamDate(2028, 3, 1), at(istanbul, 2028, 2, 1, 12), istanbul))
        assertEquals(28, ExamCountdown.calendarDaysUntil(ExamDate(2027, 3, 1), at(istanbul, 2027, 2, 1, 12), istanbul))
        val leapDay = ExamDate(2028, 2, 29, 10, 0)
        assertEquals(1, ExamCountdown.calendarDaysUntil(leapDay, at(istanbul, 2028, 2, 28, 18), istanbul))
        assertEquals(Countdown(1, 0, 0, 0), ExamCountdown.countdown(leapDay.toMillis(istanbul), at(istanbul, 2028, 2, 28, 10)))
    }

    @Test
    fun yksFocusMovesFromTytToAytAfterTheFirstSessionStarts() {
        val goal = ExamGoal(ExamGoalType.YKS)
        val before = ExamCountdown.sessions(goal, at(istanbul, 2027, 5, 1, 12), istanbul)
        assertEquals(ExamSession.TYT, ExamCountdown.focus(before)!!.session)
        assertTrue(before.all { it.status == SessionStatus.UPCOMING })
        // AYT/YDT is a day later than TYT, not on the same date.
        assertEquals(before[0].left!!.days + 1, before[1].left!!.days)

        val tytMorning = ExamCountdown.sessions(goal, at(istanbul, 2027, 6, 19, 8, 0), istanbul)
        assertEquals(SessionStatus.TODAY, tytMorning[0].status)
        assertEquals(Countdown(0, 2, 15, 0), tytMorning[0].left)

        val tytStarted = ExamCountdown.sessions(goal, at(istanbul, 2027, 6, 19, 10, 16), istanbul)
        assertEquals(SessionStatus.PASSED, tytStarted[0].status)
        assertNull(tytStarted[0].left)
        assertEquals(ExamSession.AYT_YDT, ExamCountdown.focus(tytStarted)!!.session)
        assertEquals(SessionStatus.UPCOMING, tytStarted[1].status)

        val allDone = ExamCountdown.sessions(goal, at(istanbul, 2027, 6, 21), istanbul)
        assertTrue(allDone.all { it.status == SessionStatus.PASSED && it.left == null && it.calendarDays == 0 })
        assertNull(ExamCountdown.focus(allDone))
    }

    @Test
    fun aDateTheStudentEntersReplacesTheEstimateForThatSessionOnly() {
        val goal = ExamGoal(ExamGoalType.YKS, dates = mapOf(ExamSession.TYT to ExamDate(2027, 6, 26, 10, 15)))
        assertEquals(ExamDate(2027, 6, 26, 10, 15), goal.dateOf(ExamSession.TYT))
        assertFalse(goal.isEstimated(ExamSession.TYT))
        assertTrue(goal.isEstimated(ExamSession.AYT_YDT))
        // Now TYT is after AYT/YDT: the focus follows the actual dates, not the list order.
        val s = ExamCountdown.sessions(goal, at(istanbul, 2027, 6, 1), istanbul)
        assertEquals(ExamSession.AYT_YDT, ExamCountdown.focus(s)!!.session)
    }

    @Test
    fun datesFromTheDatePickerKeepTheirDayInEveryZone() {
        val picked = ExamDate(2027, 6, 19).toPickerMillis()
        val default = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Los_Angeles", "Europe/Istanbul", "Pacific/Kiritimati", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(ExamDate(2027, 6, 19, 10, 15), ExamDate.fromPickerMillis(picked, 10, 15))
            }
        } finally {
            TimeZone.setDefault(default)
        }
    }

    @Test
    fun datesRoundTripThroughStorageAndBadInputIsIgnored() {
        val d = ExamDate(2027, 6, 20, 10, 15)
        assertEquals("2027-06-20T10:15", d.encode())
        assertEquals(d, ExamDate.decode(d.encode()))
        assertNull(ExamDate.decode("2027-13-40T99:99"))
        assertNull(ExamDate.decode("yarın"))
        assertNull(ExamDate.decode(null))
    }
}
