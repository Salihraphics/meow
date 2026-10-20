package com.salihraphics.meow.core.mascot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** One screen's cat: its resting state plus whatever reaction is currently playing. */
@Stable
class MascotStateHolder(initialResting: MascotState) {
    var resting: MascotState by mutableStateOf(initialResting)
        private set

    var state: MascotState by mutableStateOf(initialResting)
        private set

    fun onEvent(event: MascotEvent) {
        state = MascotReactions.reduce(state, event, resting)
    }

    /** Changes what the cat returns to. With [settle], the cat switches to it right away. */
    fun rest(newResting: MascotState, settle: Boolean = true) {
        resting = newResting
        if (settle) state = newResting
    }
}

/**
 * Remembers a cat for one screen. Reactions settle back to the resting state after
 * [settleAfterMillis], so the cat never stays stuck in a surprised or grumpy face.
 */
@Composable
fun rememberMascotState(
    resting: MascotState = MascotState(),
    settleAfterMillis: Long = 2_600L,
): MascotStateHolder {
    val holder = remember { MascotStateHolder(resting) }
    LaunchedEffect(holder.state, holder.resting) {
        if (MascotReactions.isTransient(holder.state, holder.resting)) {
            delay(settleAfterMillis)
            holder.onEvent(MascotEvent.Settle)
        }
    }
    return holder
}
