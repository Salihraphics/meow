package com.salihraphics.meow.feature.study

import com.salihraphics.meow.R
import com.salihraphics.meow.core.mascot.MascotMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StudyCatTest {
    private val min = StudyTimer.MINUTE_MS
    private val cat = StudyCat(Random(7))

    @Test
    fun tappingASleepingCatWakesIt() {
        val asleep = cat.dozedOff()
        assertTrue(asleep.sleeping)
        assertEquals(MascotMood.Sleeping, asleep.mood)
        val awake = cat.tapped(asleep, TimerStatus.IDLE)
        assertFalse(awake.sleeping)
        assertTrue(awake.line!!.text in StudyCat.WAKE)
    }

    @Test
    fun theSameLineIsNeverSaidTwiceInARow() {
        var state = StudyCatState()
        var previous: Int? = null
        repeat(50) {
            state = cat.tapped(state, TimerStatus.IDLE)
            assertNotEquals(previous, state.line!!.text)
            previous = state.line!!.text
        }
    }

    @Test
    fun aShortSessionEndedEarlyGetsAGentleLineWithItsMinutes() {
        val state = cat.onEvent(TimerEvent.Finished(1, 7 * min + 20_000, 25 * min, completed = false))
        assertEquals(MascotMood.Encouraging, state.mood)
        assertTrue(state.line!!.text in StudyCat.ENDED_EARLY)
        assertEquals(7, state.line!!.minutes)
    }

    @Test
    fun endingAlmostAtOnceIsFineToo() {
        val state = cat.onEvent(TimerEvent.Finished(1, 20_000, 25 * min, completed = false))
        assertTrue(state.line!!.text in StudyCat.ENDED_AT_ONCE)
    }

    @Test
    fun aCompletedLongSessionIsCelebratedDifferentlyFromAShortOne() {
        val long = cat.onEvent(TimerEvent.Finished(1, 50 * min, 50 * min, completed = true))
        assertEquals(MascotMood.Celebrating, long.mood)
        assertTrue(long.line!!.text in StudyCat.COMPLETED_LONG)
        assertEquals(50, long.line!!.minutes)
        val short = cat.onEvent(TimerEvent.Finished(2, 10 * min, 10 * min, completed = true))
        assertEquals(MascotMood.Happy, short.mood)
        assertTrue(short.line!!.text in StudyCat.COMPLETED_SHORT)
    }

    @Test
    fun whileStudyingTheCatIsQuietlySupportive() {
        val state = cat.tapped(StudyCatState(), TimerStatus.RUNNING)
        assertTrue(state.line!!.text in StudyCat.TAP_STUDYING)
        assertEquals(R.string.study_cat_start_1, StudyCat.STARTED.first())
    }
}
