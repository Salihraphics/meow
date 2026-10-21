package com.salihraphics.meow.feature.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salihraphics.meow.R
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.feature.answerkey.testTitle

/** "7 cevap işaretlendi · Testi kontrol et", shown while answers are being collected. */
@Composable
fun SessionBar(answered: Int, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.session_answers, answered), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            Button(onClick = onCheck, shape = CircleShape) { Text(stringResource(R.string.session_check)) }
        }
    }
}

/**
 * What "Testi kontrol et" found: the result with mistakes and blanks to revisit, or what's
 * needed before checking (a confirmed key, or which test some pages belong to).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionCheckDialog(
    check: SessionCheck,
    onDismiss: () -> Unit,
    onOpenKey: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onAssignTest: (testId: String, pages: List<Int>) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp)) {
                when (check) {
                    is SessionCheck.Checked -> Results(check, onDismiss, onGoToPage)
                    SessionCheck.NothingToCheck -> Message(MascotMood.Curious, stringResource(R.string.check_nothing)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_ok)) }
                    }
                    is SessionCheck.KeyNotConfirmed -> Message(MascotMood.Encouraging, stringResource(R.string.check_blocked_not_confirmed)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_dismiss)) }
                        TextButton(onClick = onOpenKey) { Text(stringResource(R.string.check_open_key)) }
                    }
                    is SessionCheck.TestUnknown -> if (check.tests.isEmpty()) {
                        Message(MascotMood.Encouraging, stringResource(R.string.check_blocked_no_key)) {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_dismiss)) }
                            TextButton(onClick = onOpenKey) { Text(stringResource(R.string.check_open_key)) }
                        }
                    } else {
                        val pages = check.pages.joinToString(", ") { "${it + 1}" }
                        Message(MascotMood.Curious, stringResource(R.string.check_blocked_test_unknown, pages)) {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_dismiss)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            check.tests.forEach { test -> OutlinedButton(onClick = { onAssignTest(test.id, check.pages) }) { Text(testTitle(test)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(mood: MascotMood, text: String, actions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MeowCat(mood, Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) { actions() }
}

@Composable
private fun Results(check: SessionCheck.Checked, onDismiss: () -> Unit, onGoToPage: (Int) -> Unit) {
    val result = check.result
    val perfect = result.incorrect == 0 && result.unanswered == 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        MeowCat(if (perfect) MascotMood.Celebrating else MascotMood.Encouraging, Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(if (perfect) R.string.result_all_correct else R.string.result_cheer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(stringResource(R.string.result_correct), result.correct, Color(0xFF1B8A4B), Modifier.weight(1f))
        Stat(stringResource(R.string.result_incorrect), result.incorrect, MaterialTheme.colorScheme.error, Modifier.weight(1f))
        Stat(stringResource(R.string.result_blank), result.unanswered, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
    }
    if (result.selfCheck > 0) {
        Text(
            "${stringResource(R.string.result_self_check)}: ${result.selfCheck}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        val tests = result.questions.groupBy { it.testId }
        if (tests.size > 1) {
            tests.values.forEach { qs ->
                val correct = qs.count { it.result == AttemptResult.CORRECT }
                Text(
                    stringResource(R.string.result_test_header, qs.first().testLabel.ifBlank { stringResource(R.string.key_test_untitled) }, correct, qs.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (result.mistakes.isNotEmpty()) {
            SectionTitle(stringResource(R.string.result_mistakes))
            result.mistakes.forEach { q ->
                QuestionLine(stringResource(R.string.result_mistake_line, q.question, q.selected.orEmpty(), q.correct), q.pageIndex, onGoToPage)
            }
        }
        if (result.blanks.isNotEmpty()) {
            SectionTitle(stringResource(R.string.result_blanks))
            result.blanks.forEach { q -> QuestionLine(stringResource(R.string.result_blank_line, q.question, q.correct), null, onGoToPage) }
        }
        if (check.notInKey.isNotEmpty()) {
            Text(
                stringResource(R.string.result_not_in_key, check.notInKey.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_ok)) }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun QuestionLine(text: String, page: Int?, onGoToPage: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (page != null) TextButton(onClick = { onGoToPage(page) }) { Text(stringResource(R.string.result_go_to_page)) }
    }
}
