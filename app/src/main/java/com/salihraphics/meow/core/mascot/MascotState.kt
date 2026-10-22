package com.salihraphics.meow.core.mascot

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.salihraphics.meow.R

/** How the cat looks right now. Each mood maps to a distinct face/pose in [MeowCat]. */
enum class MascotMood {
    Idle,
    Curious,
    Happy,
    Celebrating,
    Sleepy,
    Sleeping,
    /** Just woken: eyes half open, a small yawn. */
    Waking,
    Surprised,
    Grumpy,
    /** Soft, supportive look used after mistakes. Never disappointed, never shaming. */
    Encouraging,
}

@Immutable
data class MascotState(
    val mood: MascotMood = MascotMood.Idle,
    @StringRes val speech: Int? = null,
    /** Consecutive taps without the cat settling down; drives the playful "grumpy" reaction. */
    val tapStreak: Int = 0,
)

/**
 * Things that happen in the app that the cat may react to. Study events (answers, tests) are
 * defined now so solving screens can emit them as they are built.
 */
sealed interface MascotEvent {
    data object Greet : MascotEvent
    data object Tapped : MascotEvent
    data object AnswerCorrect : MascotEvent
    data object AnswerIncorrect : MascotEvent
    /** The cat naps quietly so it never distracts during a test. */
    data object TestStarted : MascotEvent
    data object TestCompleted : MascotEvent
    data object LongIdle : MascotEvent
    /** A transient reaction has run its course; return to the resting state. */
    data object Settle : MascotEvent
}

object MascotReactions {

    const val GRUMPY_TAP_THRESHOLD = 5

    private val tapReplies = listOf(
        MascotMood.Happy to R.string.mascot_tap_1,
        MascotMood.Surprised to R.string.mascot_tap_2,
        MascotMood.Curious to R.string.mascot_tap_3,
    )

    /**
     * Pure reducer: the next state for [event], given the screen's [resting] state. Kept free of
     * Android and time so it can be unit tested and reused by any screen.
     */
    fun reduce(current: MascotState, event: MascotEvent, resting: MascotState): MascotState =
        when (event) {
            MascotEvent.Greet -> MascotState(MascotMood.Happy, R.string.mascot_hello)
            MascotEvent.Tapped -> {
                val streak = current.tapStreak + 1
                if (current.mood == MascotMood.Sleeping) {
                    MascotState(MascotMood.Surprised, R.string.mascot_tap_2, tapStreak = streak)
                } else if (streak >= GRUMPY_TAP_THRESHOLD) {
                    MascotState(MascotMood.Grumpy, R.string.mascot_tap_grumpy, tapStreak = streak)
                } else {
                    val (mood, speech) = tapReplies[(streak - 1) % tapReplies.size]
                    MascotState(mood, speech, tapStreak = streak)
                }
            }
            MascotEvent.AnswerCorrect -> MascotState(MascotMood.Happy, R.string.mascot_correct)
            MascotEvent.AnswerIncorrect -> MascotState(MascotMood.Encouraging, R.string.mascot_incorrect)
            MascotEvent.TestStarted -> MascotState(MascotMood.Sleeping)
            MascotEvent.TestCompleted -> MascotState(MascotMood.Celebrating, R.string.mascot_test_done)
            MascotEvent.LongIdle -> MascotState(MascotMood.Sleeping, R.string.mascot_idle_sleep)
            MascotEvent.Settle -> resting
        }

    /** Whether a state is a short reaction that should [MascotEvent.Settle] after a moment. */
    fun isTransient(state: MascotState, resting: MascotState): Boolean = state != resting
}
