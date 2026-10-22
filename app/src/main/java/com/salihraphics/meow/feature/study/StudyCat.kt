package com.salihraphics.meow.feature.study

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.salihraphics.meow.R
import com.salihraphics.meow.core.mascot.MascotMood
import kotlin.random.Random

/** One thing the cat says; [minutes] fills a "%d dakika" in the text when the line has one. */
@Immutable
data class CatLine(@StringRes val text: Int, val minutes: Int? = null)

@Immutable
data class StudyCatState(
    val sleeping: Boolean = false,
    val mood: MascotMood = MascotMood.Idle,
    val line: CatLine? = null,
)

/**
 * The study cat next to the timer. Pure: what it says comes from the timer's real events and
 * the time actually studied. Short sessions get warm words, never a nudge to do more; finishing
 * a long one gets a celebration. Lines vary, and the same one is never said twice in a row.
 */
class StudyCat(private val random: Random = Random.Default) {
    private val lastPicked = HashMap<List<Int>, Int>()

    fun tapped(state: StudyCatState, timer: TimerStatus): StudyCatState = when {
        state.sleeping -> StudyCatState(false, MascotMood.Surprised, pick(WAKE))
        timer == TimerStatus.RUNNING -> StudyCatState(false, MascotMood.Happy, pick(TAP_STUDYING))
        else -> StudyCatState(false, if (random.nextBoolean()) MascotMood.Curious else MascotMood.Happy, pick(TAP_IDLE))
    }

    /** Nothing has happened for a while and no session is running: the cat naps. */
    fun dozedOff(): StudyCatState = StudyCatState(true, MascotMood.Sleeping, pick(DOZE))

    fun onEvent(event: TimerEvent): StudyCatState = when (event) {
        is TimerEvent.Started -> StudyCatState(false, MascotMood.Happy, pick(STARTED))
        is TimerEvent.Paused -> StudyCatState(false, MascotMood.Idle, pick(PAUSED))
        is TimerEvent.Finished -> finished(event)
    }

    /** The bubble went away; the cat keeps its pose. */
    fun quiet(state: StudyCatState): StudyCatState = state.copy(line = null, mood = if (state.sleeping) MascotMood.Sleeping else MascotMood.Idle)

    private fun finished(event: TimerEvent.Finished): StudyCatState {
        val minutes = (event.elapsedMs / StudyTimer.MINUTE_MS).toInt()
        return when {
            event.completed && event.plannedMs >= LONG_SESSION_MS -> StudyCatState(false, MascotMood.Celebrating, pick(COMPLETED_LONG, minutes))
            event.completed -> StudyCatState(false, MascotMood.Happy, pick(COMPLETED_SHORT, minutes))
            minutes < 1 -> StudyCatState(false, MascotMood.Encouraging, pick(ENDED_AT_ONCE))
            else -> StudyCatState(false, MascotMood.Encouraging, pick(ENDED_EARLY, minutes))
        }
    }

    private fun pick(pool: List<Int>, minutes: Int? = null): CatLine {
        val previous = lastPicked[pool]
        var index = random.nextInt(pool.size)
        if (pool.size > 1 && index == previous) index = (index + 1 + random.nextInt(pool.size - 1)) % pool.size
        lastPicked[pool] = index
        return CatLine(pool[index], minutes)
    }

    companion object {
        /** Sessions planned at least this long are celebrated as long ones. */
        const val LONG_SESSION_MS = 25 * StudyTimer.MINUTE_MS

        /** The cat dozes off after this long without a tap or a timer event, unless a session runs. */
        const val DOZE_AFTER_MS = 3 * StudyTimer.MINUTE_MS

        /** How long a speech bubble stays. */
        const val BUBBLE_MS = 6_000L

        val TAP_IDLE = listOf(R.string.study_cat_tap_1, R.string.study_cat_tap_2, R.string.study_cat_tap_3, R.string.study_cat_tap_4)
        val TAP_STUDYING = listOf(R.string.study_cat_focus_1, R.string.study_cat_focus_2, R.string.study_cat_focus_3)
        val WAKE = listOf(R.string.study_cat_wake_1, R.string.study_cat_wake_2, R.string.study_cat_wake_3)
        val DOZE = listOf(R.string.study_cat_doze_1, R.string.study_cat_doze_2, R.string.study_cat_doze_3)
        val STARTED = listOf(R.string.study_cat_start_1, R.string.study_cat_start_2, R.string.study_cat_start_3)
        val PAUSED = listOf(R.string.study_cat_pause_1, R.string.study_cat_pause_2)
        val ENDED_AT_ONCE = listOf(R.string.study_cat_ended_now_1, R.string.study_cat_ended_now_2)
        val ENDED_EARLY = listOf(R.string.study_cat_ended_early_1, R.string.study_cat_ended_early_2, R.string.study_cat_ended_early_3)
        val COMPLETED_SHORT = listOf(R.string.study_cat_done_short_1, R.string.study_cat_done_short_2)
        val COMPLETED_LONG = listOf(R.string.study_cat_done_long_1, R.string.study_cat_done_long_2, R.string.study_cat_done_long_3)
    }
}
