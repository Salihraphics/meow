package com.salihraphics.meow.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.core.designsystem.adaptive.CenteredContent
import com.salihraphics.meow.core.designsystem.adaptive.CenteredScrollColumn
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWidths
import com.salihraphics.meow.core.designsystem.adaptive.TwoPaneRow
import com.salihraphics.meow.core.designsystem.component.MeowPrimaryButton
import com.salihraphics.meow.core.designsystem.component.StepHeader

/**
 * Shared frame for the five question steps.
 *
 * - Phones: one column, continue button pinned within thumb reach.
 * - Phones in landscape: question and cat on the left, answers and button on the right.
 * - Tablets: the same two panes (or one column in portrait), centred vertically with the button
 *   right under the answers.
 *
 * [companion] receives whether it is being shown in the roomy side pane.
 */
@Composable
internal fun QuestionStepLayout(
    number: Int,
    total: Int,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    showCompanionWhenCompact: Boolean = true,
    companion: (@Composable (inSidePane: Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val window = LocalMeowWindow.current
    val button = @Composable { mod: Modifier -> MeowPrimaryButton(text = primaryLabel, onClick = onPrimary, modifier = mod) }
    val answers = @Composable { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }

    Column(modifier.fillMaxSize()) {
        CenteredContent(MeowWidths.Wide) {
            StepHeader(current = number, total = total, onBack = onBack, onSkip = onSkip)
        }
        when {
            window.useTwoPanes && window.keepActionsNearContent -> TwoPaneRow(Modifier.weight(1f).padding(horizontal = 32.dp)) {
                CenteredScrollColumn(Modifier.weight(0.45f).fillMaxHeight()) {
                    StepTitle(title, subtitle)
                    if (companion != null) {
                        Spacer(Modifier.height(40.dp))
                        companion(true)
                    }
                }
                CenteredScrollColumn(Modifier.weight(0.55f).fillMaxHeight()) {
                    answers()
                    Spacer(Modifier.height(28.dp))
                    button(Modifier.padding(bottom = 16.dp))
                }
            }
            window.useTwoPanes -> TwoPaneRow(Modifier.weight(1f).padding(horizontal = 32.dp)) {
                // Short window: every dp of height counts, so the button stays pinned.
                Column(Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(8.dp))
                    StepTitle(title, subtitle)
                    if (companion != null) {
                        Spacer(Modifier.height(16.dp))
                        companion(true)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Column(Modifier.weight(0.55f).fillMaxHeight()) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        answers()
                        Spacer(Modifier.height(8.dp))
                    }
                    button(Modifier.padding(top = 8.dp, bottom = 16.dp))
                }
            }
            window.keepActionsNearContent -> CenteredContent(MeowWidths.Readable, Modifier.weight(1f)) {
                CenteredScrollColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    StepTitle(title, subtitle)
                    Spacer(Modifier.height(24.dp))
                    answers()
                    if (companion != null && showCompanionWhenCompact) {
                        Spacer(Modifier.height(28.dp))
                        companion(false)
                    }
                    Spacer(Modifier.height(28.dp))
                    button(Modifier.padding(bottom = 16.dp))
                }
            }
            else -> CenteredContent(MeowWidths.Readable, Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                    ) {
                        Spacer(Modifier.height(20.dp))
                        StepTitle(title, subtitle)
                        Spacer(Modifier.height(24.dp))
                        answers()
                        if (companion != null && showCompanionWhenCompact) {
                            Spacer(Modifier.height(28.dp))
                            companion(false)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    button(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.widthIn(max = MeowWidths.Readable).semantics { heading() },
    )
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = MeowWidths.Readable),
    )
}
