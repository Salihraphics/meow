package com.salihraphics.meow.feature.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.domain.model.AttemptResult

/**
 * The cat's reaction to a marked answer, or its question when the mark was unclear.
 * Shown over the page; never covers more than a small strip of it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnswerCheckCard(
    check: AnswerCheckUi,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mood = when (check) {
        is AnswerCheckUi.Result -> when (check.outcome.result) {
            AttemptResult.CORRECT -> MascotMood.Celebrating
            AttemptResult.INCORRECT -> MascotMood.Encouraging
            AttemptResult.NEEDS_SELF_CHECK -> MascotMood.Curious
        }
        is AnswerCheckUi.Ask -> MascotMood.Curious
        else -> MascotMood.Encouraging
    }
    val message = when (check) {
        is AnswerCheckUi.Result -> with(check.outcome) {
            when (result) {
                AttemptResult.CORRECT -> stringResource(R.string.check_correct, question, selected)
                AttemptResult.INCORRECT -> stringResource(R.string.check_incorrect, question, selected, correct)
                AttemptResult.NEEDS_SELF_CHECK -> stringResource(R.string.check_self, question, correct)
            }
        }
        is AnswerCheckUi.Ask -> stringResource(R.string.check_which_option, check.question)
        AnswerCheckUi.KeyNotConfirmed -> stringResource(R.string.check_key_not_confirmed)
        AnswerCheckUi.PageTestUnknown -> stringResource(R.string.check_page_test_unknown)
        is AnswerCheckUi.NoAnswer -> stringResource(R.string.check_no_key_for_question, check.question)
    }
    Surface(
        modifier = modifier.widthIn(max = 460.dp).padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeowCat(mood, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(10.dp))
            when (check) {
                is AnswerCheckUi.Ask -> {
                    // Every option, the likely ones first, so a wrong guess is one tap to fix.
                    val ordered = check.candidates + (check.options - check.candidates.toSet())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ordered.forEach { letter ->
                            OptionButton(letter, highlighted = letter in check.candidates) { onChoose(letter) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_not_an_answer)) }
                    }
                }
                AnswerCheckUi.KeyNotConfirmed, AnswerCheckUi.PageTestUnknown, is AnswerCheckUi.NoAnswer ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_dismiss)) }
                        TextButton(onClick = onOpenKey) { Text(stringResource(R.string.check_open_key)) }
                    }
                is AnswerCheckUi.Result ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_ok)) }
                    }
            }
        }
    }
}

@Composable
private fun OptionButton(letter: String, highlighted: Boolean, onClick: () -> Unit) {
    if (highlighted) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(48.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(letter, style = MaterialTheme.typography.titleMedium) }
        }
    } else {
        OutlinedButton(onClick = onClick, shape = CircleShape, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
            Text(letter, style = MaterialTheme.typography.titleMedium)
        }
    }
}
