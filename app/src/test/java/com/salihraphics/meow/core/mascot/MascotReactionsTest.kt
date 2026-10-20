package com.salihraphics.meow.core.mascot

import com.salihraphics.meow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotReactionsTest {
    private val resting = MascotState(MascotMood.Idle)

    private fun MascotState.after(event: MascotEvent) = MascotReactions.reduce(this, event, resting)

    @Test
    fun mistakesGetEncouragementNeverGrumpiness() {
        val s = resting.after(MascotEvent.AnswerIncorrect)
        assertEquals(MascotMood.Encouraging, s.mood)
        assertEquals(R.string.mascot_incorrect, s.speech)
    }

    @Test
    fun catSleepsQuietlyDuringTests() {
        val s = resting.after(MascotEvent.TestStarted)
        assertEquals(MascotMood.Sleeping, s.mood)
        assertEquals(null, s.speech)
    }

    @Test
    fun repeatedTapsVaryThenTurnPlayfullyGrumpy() {
        var s = resting
        val moods = mutableListOf<MascotMood>()
        repeat(MascotReactions.GRUMPY_TAP_THRESHOLD) {
            s = s.after(MascotEvent.Tapped)
            moods += s.mood
        }
        assertNotEquals(moods[0], moods[1])
        assertEquals(MascotMood.Grumpy, moods.last())
        assertEquals(R.string.mascot_tap_grumpy, s.speech)
    }

    @Test
    fun tappingASleepingCatStartlesIt() {
        assertEquals(MascotMood.Surprised, MascotState(MascotMood.Sleeping).after(MascotEvent.Tapped).mood)
    }

    @Test
    fun settlingReturnsToRestAndResetsTapStreak() {
        val tapped = resting.after(MascotEvent.Tapped).after(MascotEvent.Tapped)
        assertTrue(MascotReactions.isTransient(tapped, resting))
        val settled = tapped.after(MascotEvent.Settle)
        assertEquals(resting, settled)
        assertFalse(MascotReactions.isTransient(settled, resting))
    }

    @Test
    fun completingATestIsCelebrated() {
        assertEquals(MascotMood.Celebrating, resting.after(MascotEvent.TestCompleted).mood)
    }
}
