package com.salihraphics.meow.feature.study

import com.salihraphics.meow.data.study.StudyDao
import com.salihraphics.meow.data.study.StudySessionEntity
import com.salihraphics.meow.data.study.StudyTimerEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = StudyTimer.MINUTE_MS

class StudyTimerTest {
    @Test
    fun elapsedComesFromTheClockNotFromCounting() {
        val t = StudyTimer().start(now = 1_000, wallNow = 0, documentId = "doc")
        // Nothing ran in between (backgrounded app, sleeping UI): the clock still says 7 minutes.
        assertEquals(7 * MIN, t.elapsedMs(1_000 + 7 * MIN))
        assertEquals(18 * MIN, t.remainingMs(1_000 + 7 * MIN))
    }

    @Test
    fun pauseBanksTheTimeAndResumeCarriesOn() {
        var t = StudyTimer().start(0, 0, null)
        t = t.pause(10 * MIN)
        assertEquals(TimerStatus.PAUSED, t.status)
        // Time while paused doesn't count.
        assertEquals(10 * MIN, t.elapsedMs(50 * MIN))
        t = t.resume(50 * MIN)
        assertEquals(15 * MIN, t.elapsedMs(55 * MIN))
    }

    @Test
    fun completesOnlyWhenTheFullDurationHasRun() {
        var t = StudyTimer().start(0, 0, null)
        assertEquals(TimerStatus.RUNNING, t.tick(25 * MIN - 1).status)
        t = t.tick(25 * MIN)
        assertEquals(TimerStatus.COMPLETED, t.status)
        assertEquals(25 * MIN, t.elapsedMs(90 * MIN)) // never more than the duration
    }

    @Test
    fun resetGoesBackToIdleKeepingTheChosenDuration() {
        val t = StudyTimer().withDuration(45 * MIN).start(0, 0, null).pause(3 * MIN).reset()
        assertEquals(TimerStatus.IDLE, t.status)
        assertEquals(0, t.elapsedMs(100 * MIN))
        assertEquals(45 * MIN, t.durationMs)
    }

    @Test
    fun durationDefaultsTo25MinutesAndIsKeptInRange() {
        assertEquals(25 * MIN, StudyTimer().durationMs)
        assertEquals(StudyTimer.MIN_DURATION_MS, StudyTimer().withDuration(1 * MIN).durationMs)
        assertEquals(StudyTimer.MAX_DURATION_MS, StudyTimer().withDuration(600 * MIN).durationMs)
        // Not while a session is under way.
        val running = StudyTimer().start(0, 0, null)
        assertEquals(25 * MIN, running.withDuration(50 * MIN).durationMs)
    }

    @Test
    fun aRunningTimerComesBackPausedAfterTheProcessEnded() {
        val saved = StudyTimer().start(0, 0, "doc")
        val restored = saved.restoredAfterProcessDeath(savedElapsedMs = 12 * MIN)
        assertEquals(TimerStatus.PAUSED, restored.status)
        // However long the app was gone, nothing more is counted and nothing completes.
        assertEquals(12 * MIN, restored.elapsedMs(10_000 * MIN))
        assertEquals(TimerStatus.PAUSED, restored.tick(10_000 * MIN).status)
    }
}

/** In-memory [StudyDao]. */
class FakeStudyDao : StudyDao {
    var saved: StudyTimerEntity? = null
    val sessions = MutableStateFlow<List<StudySessionEntity>>(emptyList())

    override suspend fun timer() = saved
    override suspend fun putTimer(timer: StudyTimerEntity) { saved = timer }
    override suspend fun insertSession(session: StudySessionEntity) { sessions.value = sessions.value + session }
    override fun observeSessionsSince(since: Long): Flow<List<StudySessionEntity>> = sessions.map { list -> list.filter { it.endedAt >= since } }
    override suspend fun allSessions() = sessions.value
}

@OptIn(ExperimentalCoroutinesApi::class)
class StudyTimerControllerTest {
    private val dao = FakeStudyDao()
    private var wall = 1_700_000_000_000L
    private var ids = 0

    private fun TestScope.controller(scope: kotlinx.coroutines.CoroutineScope = backgroundScope) =
        StudyTimerController(dao, scope, { testScheduler.currentTime }, { wall + testScheduler.currentTime }, { "s${++ids}" })

    @Test
    fun aFullSessionCompletesByItselfAndIsLoggedAsCompleted() = runTest {
        val c = controller()
        c.start("doc")
        runCurrent()
        assertEquals(TimerStatus.RUNNING, c.timer.value.status)
        advanceTimeBy(25 * MIN + 1)
        assertEquals(TimerStatus.COMPLETED, c.timer.value.status)
        val session = dao.sessions.value.single()
        assertTrue(session.completed)
        assertEquals(25 * MIN, session.elapsedMs)
        assertEquals("doc", session.documentId)
        val event = c.events.value as TimerEvent.Finished
        assertTrue(event.completed)
    }

    @Test
    fun resettingEarlyLogsTheTimeStudiedButNotACompletion() = runTest {
        val c = controller()
        c.start(null)
        runCurrent()
        advanceTimeBy(8 * MIN)
        c.pause()
        runCurrent()
        advanceTimeBy(30 * MIN) // a long break doesn't count
        c.resume()
        runCurrent()
        advanceTimeBy(2 * MIN)
        c.reset()
        runCurrent()
        assertEquals(TimerStatus.IDLE, c.timer.value.status)
        val session = dao.sessions.value.single()
        assertFalse(session.completed)
        assertEquals(10 * MIN, session.elapsedMs)
        val stats = c.todayStats().first()
        assertEquals(StudyStats(studiedMs = 10 * MIN, completedSessions = 0), stats)
        assertEquals(TimerEvent.Finished((c.events.value as TimerEvent.Finished).seq, 10 * MIN, 25 * MIN, completed = false), c.events.value)
    }

    @Test
    fun resettingAtOnceLogsNothing() = runTest {
        val c = controller()
        c.start(null)
        c.reset()
        runCurrent()
        assertTrue(dao.sessions.value.isEmpty())
    }

    @Test
    fun theElapsedTimeIsSavedWhileRunningAndOnBackgrounding() = runTest {
        val c = controller()
        c.start(null)
        runCurrent()
        advanceTimeBy(StudyTimerController.SAVE_EVERY_MS + 1)
        assertEquals(StudyTimerController.SAVE_EVERY_MS, dao.saved!!.elapsedMs)
        advanceTimeBy(5_000)
        c.persistNow()
        runCurrent()
        assertEquals(StudyTimerController.SAVE_EVERY_MS + 5_001, dao.saved!!.elapsedMs)
        assertEquals("RUNNING", dao.saved!!.status)
    }

    @Test
    fun afterTheProcessEndsTheTimerReturnsPausedAndIsNotCompleted() = runTest {
        val first = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        val c1 = controller(first)
        c1.start("doc")
        runCurrent()
        advanceTimeBy(6 * MIN)
        c1.persistNow()
        runCurrent()
        first.cancel() // the process ends

        advanceTimeBy(60 * MIN) // the app is gone well past the end of the session
        val c2 = controller()
        runCurrent()
        assertEquals(TimerStatus.PAUSED, c2.timer.value.status)
        assertEquals(6 * MIN, c2.timer.value.elapsedMs(testScheduler.currentTime))
        assertTrue(dao.sessions.value.isEmpty())
        assertNull(c2.events.value)
        c2.resume()
        runCurrent()
        advanceTimeBy(19 * MIN + 1)
        assertEquals(TimerStatus.COMPLETED, c2.timer.value.status)
    }

    @Test
    fun theChosenDurationIsKept() = runTest {
        val c = controller()
        c.setDurationMinutes(45)
        runCurrent()
        assertEquals(45 * MIN, dao.saved!!.durationMs)
        val again = controller()
        runCurrent()
        assertEquals(45 * MIN, again.timer.value.durationMs)
    }
}
