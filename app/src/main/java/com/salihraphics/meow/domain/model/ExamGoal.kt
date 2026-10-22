package com.salihraphics.meow.domain.model

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil

/** Exams MEOW can count down to. */
enum class ExamGoalType { YKS, LGS }

/**
 * A wall-clock moment in the student's own time zone: a calendar date and a start time. Stored
 * as fields, never as an instant, so it means the same day wherever and whenever it is read.
 */
data class ExamDate(val year: Int, val month: Int, val day: Int, val hour: Int = 0, val minute: Int = 0) {
    init {
        require(month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59) { "Invalid exam date $this" }
    }

    /** The moment it starts, in [zone] (the device's by default). */
    fun toMillis(zone: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    /** "2027-06-19T10:15", for storage. */
    fun encode(): String = "%04d-%02d-%02dT%02d:%02d".format(java.util.Locale.ROOT, year, month, day, hour, minute)

    companion object {
        fun decode(text: String?): ExamDate? {
            val m = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})""").matchEntire(text ?: return null) ?: return null
            val (y, mo, d, h, mi) = m.destructured
            return runCatching { ExamDate(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt()) }.getOrNull()
        }

        /**
         * A date picked in Material's DatePicker, which reports the chosen day as midnight UTC.
         * Reading it in the device's zone would give the previous day west of Greenwich.
         */
        fun fromPickerMillis(utcMidnight: Long, hour: Int, minute: Int): ExamDate {
            val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
            return ExamDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), hour, minute)
        }
    }

    /** This day as midnight UTC, what Material's DatePicker expects. */
    fun toPickerMillis(): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, day, 0, 0, 0)
    }.timeInMillis
}

/**
 * One sitting of an exam. The default dates are the targets MEOW starts with for the 2027 cycle;
 * they are not confirmed by ÖSYM or MEB here, so the app labels them as estimated until the
 * student enters their own. Start times are the usual ones and are editable too.
 */
enum class ExamSession(val goal: ExamGoalType, val defaultDate: ExamDate) {
    TYT(ExamGoalType.YKS, ExamDate(2027, 6, 19, 10, 15)),
    AYT_YDT(ExamGoalType.YKS, ExamDate(2027, 6, 20, 10, 15)),
    LGS(ExamGoalType.LGS, ExamDate(2027, 6, 13, 9, 30)),
}

/**
 * The exam the student is counting down to. [dates] holds only dates the student entered;
 * every other session uses its (estimated) default. [enabled] false keeps the choice but hides
 * the countdown.
 */
data class ExamGoal(
    val type: ExamGoalType,
    val dates: Map<ExamSession, ExamDate> = emptyMap(),
    val enabled: Boolean = true,
) {
    val sessions: List<ExamSession> get() = ExamSession.entries.filter { it.goal == type }

    fun dateOf(session: ExamSession): ExamDate = dates[session] ?: session.defaultDate

    /** True for MEOW's default date; false once the student has set it themselves. */
    fun isEstimated(session: ExamSession): Boolean = session !in dates
}

/** Days, hours, minutes and seconds left; never negative. */
data class Countdown(val days: Long, val hours: Int, val minutes: Int, val seconds: Int)

enum class SessionStatus {
    UPCOMING,

    /** The exam is today and hasn't started yet. */
    TODAY,

    /** It has started (or is over): no countdown any more. */
    PASSED,
}

data class SessionCountdown(
    val session: ExamSession,
    val date: ExamDate,
    val estimated: Boolean,
    val status: SessionStatus,
    /** Null once the session has started. */
    val left: Countdown?,
    /** Whole calendar days from today to the exam day, by local dates (0 on the day). */
    val calendarDays: Int,
)

/**
 * Countdowns worked out from the target moment and the current time on every call: nothing is
 * decremented or stored, so pausing, resuming or a changed clock can't make them drift.
 */
object ExamCountdown {
    private const val SECOND = 1_000L

    fun countdown(targetMillis: Long, nowMillis: Long): Countdown? {
        val left = targetMillis - nowMillis
        if (left <= 0) return null
        // Round up, so the display reaches 0:00:00:00 exactly when the exam starts.
        val total = ceil(left / SECOND.toDouble()).toLong()
        return Countdown(total / 86_400, ((total % 86_400) / 3_600).toInt(), ((total % 3_600) / 60).toInt(), (total % 60).toInt())
    }

    /** Calendar days between the local date of [nowMillis] and [date]'s day; 0 on or after it. */
    fun calendarDaysUntil(date: ExamDate, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val today = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        // Both days as UTC midnights: whole days apart, with no DST or time-of-day in between.
        val from = ExamDate(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH)).toPickerMillis()
        val to = date.copy(hour = 0, minute = 0).toPickerMillis()
        return ((to - from) / 86_400_000L).toInt().coerceAtLeast(0)
    }

    fun sessions(goal: ExamGoal, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): List<SessionCountdown> =
        goal.sessions.map { session ->
            val date = goal.dateOf(session)
            val left = countdown(date.toMillis(zone), nowMillis)
            val days = calendarDaysUntil(date, nowMillis, zone)
            val status = when {
                left == null -> SessionStatus.PASSED
                days == 0 -> SessionStatus.TODAY
                else -> SessionStatus.UPCOMING
            }
            SessionCountdown(session, date, goal.isEstimated(session), status, left, days)
        }

    /** The session to show most prominently: the next one still to start, or null when all have. */
    fun focus(sessions: List<SessionCountdown>): SessionCountdown? =
        sessions.filter { it.status != SessionStatus.PASSED }.minByOrNull { it.date.toMillis() }
}
