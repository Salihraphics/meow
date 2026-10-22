package com.salihraphics.meow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** When a held button repeats: after [initialDelayMs], then faster each time down to [fastestMs]. */
object HoldToRepeat {
    const val INITIAL_DELAY_MS = 400L
    const val FIRST_INTERVAL_MS = 220L
    const val FASTEST_MS = 70L
    const val SPEED_UP = 0.8f

    /** The wait before each repeat after the first one, modestly shorter each time. */
    fun interval(repeat: Int): Long {
        var ms = FIRST_INTERVAL_MS.toFloat()
        repeat(repeat) { ms *= SPEED_UP }
        return ms.toLong().coerceAtLeast(FASTEST_MS)
    }
}

/**
 * A round button that steps once on a tap and keeps stepping while held: the first repeat after
 * [HoldToRepeat.INITIAL_DELAY_MS], then a little faster each time. The repeats are a coroutine
 * of the gesture (delays, no busy loop), so lifting the finger, the gesture being cancelled or
 * the button leaving the screen stops them at once. A hold never adds an extra step on release.
 * [onStep] returns false at a limit, which ends the repeats.
 */
@Composable
fun RepeatingButton(
    label: String,
    enabled: Boolean,
    onStep: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val step by rememberUpdatedState(onStep)
    val active by rememberUpdatedState(enabled)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(48.dp)
            .background(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                CircleShape,
            )
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!active) return@awaitEachGesture
                        down.consume()
                        pressed = true
                        var repeated = false
                        val repeats = launch {
                            delay(HoldToRepeat.INITIAL_DELAY_MS)
                            var n = 0
                            while (active) {
                                repeated = true
                                if (!step()) break
                                delay(HoldToRepeat.interval(n++))
                            }
                        }
                        val up = waitForUpOrCancellation()
                        repeats.cancel()
                        pressed = false
                        // A tap steps once, on release; a hold already stepped while held.
                        if (up != null && !repeated) {
                            up.consume()
                            step()
                        }
                    }
                }
            }
            .semantics {
                contentDescription = label
                role = Role.Button
                if (!enabled) disabled()
                onClick { if (enabled) step() else false }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f),
        ) { content() }
    }
}
