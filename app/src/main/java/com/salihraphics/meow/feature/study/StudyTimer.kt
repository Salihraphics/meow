package com.salihraphics.meow.feature.study

enum class TimerStatus { IDLE, RUNNING, PAUSED, COMPLETED }

/** Milliseconds from a clock that only moves forward (not the wall clock, which can jump). */
fun interface MonotonicClock {
    fun nowMs(): Long
}

/**
 * The study timer, as a value: elapsed time is the time banked at the last pause plus the time
 * since [runningSince] on a [MonotonicClock], so it stays right however rarely it is looked at
 * (no per-second counter to fall behind while the app is in the background).
 */
data class StudyTimer(
    val durationMs: Long = DEFAULT_DURATION_MS,
    val status: TimerStatus = TimerStatus.IDLE,
    /** Time studied before the current run, i.e. up to the last pause. */
    val bankedMs: Long = 0,
    /** Monotonic time the current run started; null unless running. */
    val runningSince: Long? = null,
    /** Wall-clock start of the session, for the log. */
    val startedAt: Long? = null,
    val documentId: String? = null,
) {
    fun elapsedMs(now: Long): Long {
        val running = runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0
        return (bankedMs + running).coerceAtMost(durationMs)
    }

    fun remainingMs(now: Long): Long = durationMs - elapsedMs(now)

    /** Starts a new session; from idle or after one completed. */
    fun start(now: Long, wallNow: Long, documentId: String?): StudyTimer =
        if (status == TimerStatus.RUNNING || status == TimerStatus.PAUSED) this
        else StudyTimer(durationMs, TimerStatus.RUNNING, 0, now, wallNow, documentId)

    fun pause(now: Long): StudyTimer =
        if (status != TimerStatus.RUNNING) this else copy(status = TimerStatus.PAUSED, bankedMs = elapsedMs(now), runningSince = null)

    fun resume(now: Long): StudyTimer =
        if (status != TimerStatus.PAUSED) this else copy(status = TimerStatus.RUNNING, runningSince = now)

    /** Back to idle; the caller logs the session first if any time was studied. */
    fun reset(): StudyTimer = StudyTimer(durationMs)

    /** The session length can change only while no session is under way. */
    fun withDuration(ms: Long): StudyTimer =
        if (status == TimerStatus.RUNNING || status == TimerStatus.PAUSED) this
        else copy(durationMs = ms.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS))

    /** Completes a running timer whose time is up; anything else is unchanged. */
    fun tick(now: Long): StudyTimer =
        if (status == TimerStatus.RUNNING && elapsedMs(now) >= durationMs) {
            copy(status = TimerStatus.COMPLETED, bankedMs = durationMs, runningSince = null)
        } else {
            this
        }

    /**
     * After the app's process ended: a running timer comes back paused at [savedElapsedMs], the
     * time saved last. Time while the app was gone isn't counted and nothing completes by itself.
     */
    fun restoredAfterProcessDeath(savedElapsedMs: Long): StudyTimer = when (status) {
        TimerStatus.RUNNING, TimerStatus.PAUSED -> copy(status = TimerStatus.PAUSED, bankedMs = savedElapsedMs.coerceIn(0, durationMs), runningSince = null)
        else -> copy(runningSince = null)
    }

    companion object {
        const val MINUTE_MS = 60_000L
        const val DEFAULT_DURATION_MS = 25 * MINUTE_MS
        const val MIN_DURATION_MS = 5 * MINUTE_MS
        const val MAX_DURATION_MS = 180 * MINUTE_MS
    }
}
