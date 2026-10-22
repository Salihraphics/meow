package com.salihraphics.meow.feature.study

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.BubbleTail
import com.salihraphics.meow.core.designsystem.component.RepeatingButton
import com.salihraphics.meow.core.designsystem.component.SpeechBubble
import com.salihraphics.meow.core.designsystem.icon.MeowIcons
import com.salihraphics.meow.core.designsystem.theme.LocalMeowMotion
import com.salihraphics.meow.core.mascot.MeowCat
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The study timer with the cat beside it. The time shown is worked out from the timer's clock
 * each second while it runs (nothing is counted here), and the cat only moves when something
 * happens: no idle animation loops in the panel.
 */
@Composable
fun StudyTimerSection(controller: StudyTimerController, documentId: String?, modifier: Modifier = Modifier) {
    val timer by controller.timer.collectAsState()
    val stats by remember(controller) { controller.todayStats() }.collectAsState(StudyStats())
    var now by remember { mutableLongStateOf(controller.now()) }
    val cat = remember { StudyCat() }
    var catState by remember { mutableStateOf(StudyCatState()) }
    // Kept while the bubble fades out, so it doesn't empty first.
    var lastLine by remember { mutableStateOf<CatLine?>(null) }

    LaunchedEffect(timer.status) {
        now = controller.now()
        while (timer.status == TimerStatus.RUNNING) {
            val elapsed = timer.elapsedMs(now)
            delay(1_000 - elapsed % 1_000)
            now = controller.now()
            if (timer.remainingMs(now) <= 0) controller.tick()
        }
    }
    // Leaving the app saves the exact time studied so far.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.persistNow() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        now = controller.now()
        controller.tick()
    }

    LaunchedEffect(controller) {
        controller.events.collect { event ->
            if (event != null && event.seq > controller.catSeenSeq) {
                controller.catSeenSeq = event.seq
                catState = cat.onEvent(event)
            }
        }
    }
    LaunchedEffect(catState.line) {
        catState.line?.let { lastLine = it }
        if (catState.line != null) {
            delay(StudyCat.BUBBLE_MS)
            catState = cat.quiet(catState)
        }
    }
    LaunchedEffect(catState, timer.status) {
        if (!catState.sleeping && timer.status != TimerStatus.RUNNING) {
            delay(StudyCat.DOZE_AFTER_MS)
            catState = cat.dozedOff()
        }
    }

    Column(modifier) {
        // The bubble sits above the cat, so it never squeezes the timer beside a narrow panel.
        AnimatedVisibility(
            visible = catState.line != null,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        ) {
            (catState.line ?: lastLine)?.let { line ->
                val text = line.minutes?.let { stringResource(line.text, it) } ?: stringResource(line.text)
                SpeechBubble(text, Modifier.padding(bottom = 4.dp), tail = BubbleTail.BottomStart)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalMeowMotion provides LocalMeowMotion.current.copy(ambientAnimations = false)) {
                MeowCat(catState.mood, Modifier.size(64.dp), onTap = { catState = cat.tapped(catState, timer.status) })
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {

                val remaining = timer.remainingMs(now)
                val clock = formatClock(remaining)
                // Tabular digits: the time doesn't jitter as it counts down.
                Text(
                    clock,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = if (timer.status == TimerStatus.RUNNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        contentDescription = clock
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                Text(
                    when (timer.status) {
                        TimerStatus.PAUSED -> stringResource(R.string.timer_paused) + " · " + stringResource(R.string.timer_elapsed, formatClock(timer.elapsedMs(now), roundUp = false))
                        TimerStatus.COMPLETED -> stringResource(R.string.timer_completed)
                        else -> stringResource(R.string.timer_elapsed, formatClock(timer.elapsedMs(now), roundUp = false))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val remainingLabel = stringResource(R.string.timer_remaining, formatClock(timer.remainingMs(now)))
        LinearProgressIndicator(
            progress = { (timer.elapsedMs(now).toFloat() / timer.durationMs).coerceIn(0f, 1f) },
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = remainingLabel },
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val main = Modifier.weight(1f)
            when (timer.status) {
                TimerStatus.IDLE -> Button(onClick = { controller.start(documentId) }, modifier = main) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.timer_start))
                }
                TimerStatus.RUNNING -> FilledTonalButton(onClick = controller::pause, modifier = main) {
                    Icon(MeowIcons.Pause, contentDescription = null)
                    Text(stringResource(R.string.timer_pause))
                }
                TimerStatus.PAUSED -> Button(onClick = controller::resume, modifier = main) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.timer_resume))
                }
                TimerStatus.COMPLETED -> Button(onClick = { controller.reset() }, modifier = main) { Text(stringResource(R.string.timer_new)) }
            }
            if (timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED) {
                OutlinedButton(onClick = controller::reset) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(stringResource(R.string.timer_reset))
                }
            }
        }

        val minutes = (timer.durationMs / StudyTimer.MINUTE_MS).toInt()
        val canChange = timer.status == TimerStatus.IDLE || timer.status == TimerStatus.COMPLETED
        // Read fresh on every repeat while a button is held.
        fun current() = controller.timer.value
        fun canGoDown() = current().let { (it.status == TimerStatus.IDLE || it.status == TimerStatus.COMPLETED) && it.durationMs > StudyTimer.MIN_DURATION_MS }
        fun canGoUp() = current().let { (it.status == TimerStatus.IDLE || it.status == TimerStatus.COMPLETED) && it.durationMs < StudyTimer.MAX_DURATION_MS }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.timer_duration, minutes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            // Tap: 5 minutes. Hold: keeps going, a little faster the longer it's held.
            RepeatingButton(
                label = stringResource(R.string.timer_duration_less),
                enabled = canChange && timer.durationMs > StudyTimer.MIN_DURATION_MS,
                onStep = { canGoDown().also { if (it) controller.adjustDurationMinutes(-DURATION_STEP_MINUTES) } },
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            RepeatingButton(
                label = stringResource(R.string.timer_duration_more),
                enabled = canChange && timer.durationMs < StudyTimer.MAX_DURATION_MS,
                onStep = { canGoUp().also { if (it) controller.adjustDurationMinutes(DURATION_STEP_MINUTES) } },
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        Text(
            if (stats.studiedMs > 0 || stats.completedSessions > 0) {
                stringResource(R.string.timer_today, formatDuration(stats.studiedMs), stats.completedSessions)
            } else {
                stringResource(R.string.timer_today_none)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val DURATION_STEP_MINUTES = 5

/** "24:59", or "1:04:59" past an hour. A countdown rounds up, so it reads 00:00 only when time is up. */
fun formatClock(ms: Long, roundUp: Boolean = true): String {
    val total = (ms.coerceAtLeast(0) + if (roundUp) 999 else 0) / 1_000
    val h = total / 3_600
    val m = (total % 3_600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

@Composable
private fun formatDuration(ms: Long): String {
    val minutes = (ms / StudyTimer.MINUTE_MS).toInt()
    return if (minutes >= 60) stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_minutes, minutes)
}
