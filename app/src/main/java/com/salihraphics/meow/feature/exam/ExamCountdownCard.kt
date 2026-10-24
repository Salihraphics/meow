package com.salihraphics.meow.feature.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.MeowTextButton
import com.salihraphics.meow.core.designsystem.theme.MeowTokens
import com.salihraphics.meow.core.designsystem.theme.cardBorder
import com.salihraphics.meow.core.designsystem.theme.cardColor
import com.salihraphics.meow.core.designsystem.theme.insetColor
import com.salihraphics.meow.domain.model.Countdown
import com.salihraphics.meow.domain.model.ExamCountdown
import com.salihraphics.meow.domain.model.ExamDate
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.ExamGoalType
import com.salihraphics.meow.domain.model.ExamSession
import com.salihraphics.meow.domain.model.SessionCountdown
import com.salihraphics.meow.domain.model.SessionStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The current time, refreshed on every second boundary while the screen is at least STARTED.
 * The loop stops when the app goes to the background or the card leaves the screen, and the
 * value is read from [clock] each time, so a pause never makes the countdown drift.
 */
@Composable
fun rememberTickingNow(clock: () -> Long, ticking: Boolean = true): Long {
    val now = remember { mutableLongStateOf(clock()) }
    val currentClock by rememberUpdatedState(clock)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, ticking) {
        if (!ticking) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = currentClock()
                now.longValue = t
                delay(1_000 - t % 1_000)
            }
        }
    }
    return now.longValue
}

/** The compact countdown on the home screen: a 2×2 grid for the next sitting, then the rest. */
@Composable
fun ExamCountdownCard(
    goal: ExamGoal,
    nowMillis: Long,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions = ExamCountdown.sessions(goal, nowMillis)
    val focus = ExamCountdown.focus(sessions)
    val exam = stringResource(goal.type.labelRes)
    Surface(shape = MeowTokens.CardShape, color = cardColor, border = cardBorder, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        focus == null -> exam
                        sessions.size > 1 -> stringResource(R.string.exam_countdown_title_session, exam, stringResource(focus.session.labelRes))
                        else -> stringResource(R.string.exam_countdown_title, exam)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.exam_edit), modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.padding(end = 12.dp)) {
                if (focus == null) {
                    Text(
                        stringResource(R.string.exam_all_done, exam),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MeowTextButton(stringResource(R.string.exam_new_goal), onClick = onEdit)
                    return@Column
                }
                if (focus.status == SessionStatus.TODAY) {
                    Text(
                        stringResource(R.string.exam_today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                CountdownGrid(focus.left ?: Countdown(0, 0, 0, 0), stringResource(focus.session.labelRes))
                Spacer(Modifier.height(8.dp))
                Text(
                    formatExamDate(focus.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                EstimateTag(focus.estimated)
                sessions.filter { it !== focus }.forEach { other -> OtherSession(other) }
            }
        }
    }
}

@Composable
private fun CountdownGrid(left: Countdown, sessionName: String) {
    val description = stringResource(R.string.exam_countdown_a11y, sessionName, left.days.toInt(), left.hours, left.minutes)
    // Read as one sentence, without the seconds, so a screen reader isn't told about every tick.
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cell(left.days.toString(), stringResource(R.string.exam_days))
            Cell(left.hours.twoDigits(), stringResource(R.string.exam_hours))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cell(left.minutes.twoDigits(), stringResource(R.string.exam_minutes))
            Cell(left.seconds.twoDigits(), stringResource(R.string.exam_seconds))
        }
    }
}

@Composable
private fun RowScope.Cell(value: String, unit: String) {
    Surface(shape = MeowTokens.ControlShape, color = insetColor, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    // Same-width digits, so the grid doesn't shimmer as seconds change.
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EstimateTag(estimated: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (estimated) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Text(
            stringResource(if (estimated) R.string.exam_estimated else R.string.exam_custom),
            style = MaterialTheme.typography.labelMedium,
            color = if (estimated) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun OtherSession(session: SessionCountdown) {
    val name = stringResource(session.session.labelRes)
    Text(
        when (session.status) {
            SessionStatus.PASSED -> stringResource(R.string.exam_session_done, name)
            SessionStatus.TODAY -> stringResource(R.string.exam_other_session_today, name, session.date.timeText())
            SessionStatus.UPCOMING -> stringResource(R.string.exam_other_session, name, formatExamDate(session.date, withWeekday = false), session.calendarDays)
        } + if (session.status != SessionStatus.PASSED && session.estimated) " · " + stringResource(R.string.exam_estimated).lowercase(TURKISH) else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Shown when no exam is chosen: the dashboard still works, this only offers the countdown. */
@Composable
fun ExamGoalPrompt(goal: ExamGoal?, onSetUp: () -> Unit, onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MeowTokens.CardShape, color = cardColor, border = cardBorder, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            if (goal != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.exam_off) + " · " + stringResource(goal.type.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MeowTextButton(stringResource(R.string.exam_turn_on), onClick = onTurnOn)
                }
            } else {
                Text(
                    stringResource(R.string.exam_set_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.exam_set_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MeowTextButton(stringResource(R.string.exam_set_action), onClick = onSetUp)
                }
            }
        }
    }
}

private fun Int.twoDigits() = toString().padStart(2, '0')

private val TURKISH = Locale("tr", "TR")

/** "19 Haziran 2027 Cumartesi, 10:15", always in Turkish like the rest of the app. */
fun formatExamDate(date: ExamDate, withWeekday: Boolean = true): String {
    val pattern = if (withWeekday) "d MMMM yyyy EEEE, HH:mm" else "d MMMM yyyy"
    return SimpleDateFormat(pattern, TURKISH).format(date.toMillis())
}

fun ExamDate.timeText(): String = "%02d:%02d".format(TURKISH, hour, minute)

val ExamGoalType.labelRes: Int
    get() = when (this) {
        ExamGoalType.YKS -> R.string.exam_goal_yks
        ExamGoalType.LGS -> R.string.exam_goal_lgs
    }

val ExamSession.labelRes: Int
    get() = when (this) {
        ExamSession.TYT -> R.string.exam_session_tyt
        ExamSession.AYT_YDT -> R.string.exam_session_ayt
        ExamSession.LGS -> R.string.exam_session_lgs
    }
