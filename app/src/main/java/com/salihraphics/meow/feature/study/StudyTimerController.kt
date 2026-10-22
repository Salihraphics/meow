package com.salihraphics.meow.feature.study

import android.os.SystemClock
import com.salihraphics.meow.data.study.StudyDao
import com.salihraphics.meow.data.study.StudySessionEntity
import com.salihraphics.meow.data.study.StudyTimerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.UUID

/** Something the timer did, for the cat to react to. [seq] tells repeats of the same event apart. */
sealed interface TimerEvent {
    val seq: Long

    data class Started(override val seq: Long, val durationMs: Long) : TimerEvent
    data class Paused(override val seq: Long, val elapsedMs: Long) : TimerEvent

    /** A session ended: [completed] when it reached its duration, otherwise reset early. */
    data class Finished(override val seq: Long, val elapsedMs: Long, val plannedMs: Long, val completed: Boolean) : TimerEvent
}

/** Today's study: time actually studied (any session, finished or not) and sessions completed. */
data class StudyStats(val studiedMs: Long = 0, val completedSessions: Int = 0)

/**
 * The one study timer of the app. Lives in the app container, so leaving the viewer or opening
 * another PDF doesn't stop it. Every change is saved; while running, the elapsed time is also
 * saved every [SAVE_EVERY_MS] and when the app goes to the background, so a process that ends
 * loses at most that much, and the timer comes back paused (see [StudyTimer.restoredAfterProcessDeath]).
 */
class StudyTimerController(
    private val dao: StudyDao,
    private val scope: CoroutineScope,
    private val clock: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _timer = MutableStateFlow(StudyTimer())
    val timer: StateFlow<StudyTimer> = _timer.asStateFlow()

    private val _events = MutableStateFlow<TimerEvent?>(null)

    /** The latest timer event; collectors react to each new [TimerEvent.seq] once. */
    val events: StateFlow<TimerEvent?> = _events.asStateFlow()

    /** The last event the study cat reacted to, so reopening the panel doesn't replay it. */
    var catSeenSeq: Long = 0

    private val lock = Mutex()
    private var seq = 0L
    private var ticker: Job? = null
    private val loaded = scope.launch { load() }

    fun now(): Long = clock.nowMs()

    private suspend fun load() {
        val saved = dao.timer() ?: return
        val status = TimerStatus.entries.firstOrNull { it.name == saved.status } ?: TimerStatus.IDLE
        _timer.value = StudyTimer(saved.durationMs, status, saved.elapsedMs, null, saved.startedAt, saved.documentId)
            .restoredAfterProcessDeath(saved.elapsedMs)
        save()
    }

    fun start(documentId: String?) = change { t ->
        t.start(now(), wallClock(), documentId).also { if (it !== t) emit { s -> TimerEvent.Started(s, it.durationMs) } }
    }

    fun pause() = change { t -> t.pause(now()).also { if (it !== t) emit { s -> TimerEvent.Paused(s, it.bankedMs) } } }

    fun resume() = change { t -> t.resume(now()) }

    /** Ends the session early (logged with the time studied) or clears a completed one. */
    fun reset() = change { t ->
        val elapsed = t.elapsedMs(now())
        if ((t.status == TimerStatus.RUNNING || t.status == TimerStatus.PAUSED)) {
            if (elapsed > 0) log(t, elapsed, completed = false)
            emit { s -> TimerEvent.Finished(s, elapsed, t.durationMs, completed = false) }
        }
        t.reset()
    }

    fun setDurationMinutes(minutes: Int) = change { t -> t.withDuration(minutes * StudyTimer.MINUTE_MS) }

    /** Checks whether time is up; the UI calls this as it redraws, the ticker otherwise. */
    fun tick() = change { t -> completeIfDue(t) }

    /** Saves the exact elapsed time now, e.g. when the app goes to the background. */
    fun persistNow() {
        scope.launch {
            loaded.join()
            lock.withLock { save() }
        }
    }

    fun todayStats(): Flow<StudyStats> = dao.observeSessionsSince(startOfToday()).map { sessions ->
        StudyStats(sessions.sumOf { it.elapsedMs }, sessions.count { it.completed })
    }

    private suspend fun completeIfDue(t: StudyTimer): StudyTimer {
        val next = t.tick(now())
        if (next.status == TimerStatus.COMPLETED && t.status == TimerStatus.RUNNING) {
            log(t, next.durationMs, completed = true)
            emit { s -> TimerEvent.Finished(s, next.durationMs, next.durationMs, completed = true) }
        }
        return next
    }

    private fun change(transform: suspend (StudyTimer) -> StudyTimer) {
        scope.launch {
            loaded.join()
            lock.withLock {
                _timer.value = transform(_timer.value)
                save()
                scheduleTicker()
            }
        }
    }

    /** While running: checks for the end and saves the elapsed time now and then. */
    private fun scheduleTicker() {
        ticker?.cancel()
        val t = _timer.value
        if (t.status != TimerStatus.RUNNING) return
        ticker = scope.launch {
            delay(t.remainingMs(now()).coerceIn(1, SAVE_EVERY_MS))
            lock.withLock {
                _timer.value = completeIfDue(_timer.value)
                save()
            }
            scheduleTicker()
        }
    }

    private inline fun emit(make: (Long) -> TimerEvent) {
        _events.value = make(++seq)
    }

    private suspend fun log(t: StudyTimer, elapsed: Long, completed: Boolean) {
        val end = wallClock()
        dao.insertSession(StudySessionEntity(newId(), t.documentId, t.durationMs, elapsed, completed, t.startedAt ?: (end - elapsed), end))
    }

    private suspend fun save() {
        val t = _timer.value
        dao.putTimer(StudyTimerEntity(durationMs = t.durationMs, elapsedMs = t.elapsedMs(now()), status = t.status.name, startedAt = t.startedAt, documentId = t.documentId, updatedAt = wallClock()))
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        timeInMillis = wallClock()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val SAVE_EVERY_MS = 30_000L
    }
}
