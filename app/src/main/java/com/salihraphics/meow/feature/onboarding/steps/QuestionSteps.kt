package com.salihraphics.meow.feature.onboarding.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.salihraphics.meow.R
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.mascot.CompanionCat
import com.salihraphics.meow.core.mascot.MascotStateHolder
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MascotState
import com.salihraphics.meow.core.mascot.rememberMascotState
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.feature.common.ExamOptions
import com.salihraphics.meow.feature.common.LevelOptions
import com.salihraphics.meow.feature.common.NameField
import com.salihraphics.meow.feature.common.SessionOptions
import com.salihraphics.meow.feature.common.SubjectOptions

@Composable
internal fun ExamStep(selected: ExamType?, onSelect: (ExamType) -> Unit, nav: StepNavigation) {
    val cat = rememberMascotState(MascotState(MascotMood.Curious))
    QuestionStepLayout(
        number = 1, total = nav.total,
        title = stringResource(R.string.exam_title),
        subtitle = stringResource(R.string.exam_subtitle),
        primaryLabel = stringResource(R.string.action_continue),
        onPrimary = nav.onNext, onBack = nav.onBack, onSkip = nav.onSkip,
        showCompanionWhenCompact = false,
        companion = { side -> StepCat(cat, stringResource(R.string.exam_bubble), side) },
    ) {
        ExamOptions(selected, onSelect)
    }
}

@Composable
internal fun SubjectsStep(available: List<Subject>, selected: Set<Subject>, onToggle: (Subject) -> Unit, nav: StepNavigation) {
    val cat = rememberMascotState(MascotState(MascotMood.Happy))
    QuestionStepLayout(
        number = 2, total = nav.total,
        title = stringResource(R.string.subjects_title),
        subtitle = stringResource(R.string.subjects_subtitle),
        primaryLabel = stringResource(R.string.action_continue),
        onPrimary = nav.onNext, onBack = nav.onBack, onSkip = nav.onSkip,
        showCompanionWhenCompact = false,
        companion = { side -> StepCat(cat, stringResource(R.string.subjects_bubble), side) },
    ) {
        SubjectOptions(available, selected, onToggle)
        if (selected.isNotEmpty()) {
            Text(
                stringResource(R.string.subjects_selected_count, selected.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LevelStep(selected: StudyLevel?, onSelect: (StudyLevel) -> Unit, nav: StepNavigation) {
    val cat = rememberMascotState(MascotState(MascotMood.Encouraging))
    QuestionStepLayout(
        number = 3, total = nav.total,
        title = stringResource(R.string.level_title),
        subtitle = stringResource(R.string.level_subtitle),
        primaryLabel = stringResource(R.string.action_continue),
        onPrimary = nav.onNext, onBack = nav.onBack, onSkip = nav.onSkip,
        companion = { side -> StepCat(cat, stringResource(R.string.level_bubble), side) },
    ) {
        LevelOptions(selected, onSelect)
    }
}

@Composable
internal fun SessionStep(selected: SessionLength?, onSelect: (SessionLength) -> Unit, nav: StepNavigation) {
    val cat = rememberMascotState(MascotState(MascotMood.Happy))
    QuestionStepLayout(
        number = 4, total = nav.total,
        title = stringResource(R.string.session_title),
        subtitle = stringResource(R.string.session_subtitle),
        primaryLabel = stringResource(R.string.action_continue),
        onPrimary = nav.onNext, onBack = nav.onBack, onSkip = nav.onSkip,
        companion = { side -> StepCat(cat, stringResource(R.string.session_bubble), side) },
    ) {
        SessionOptions(selected, onSelect)
    }
}

@Composable
internal fun NameStep(input: String, displayName: String?, onInput: (String) -> Unit, nav: StepNavigation) {
    val resting = MascotState(if (displayName == null) MascotMood.Curious else MascotMood.Happy)
    val cat = rememberMascotState(resting)
    LaunchedEffect(resting) { if (cat.resting != resting) cat.rest(resting) }
    QuestionStepLayout(
        number = 5, total = nav.total,
        title = stringResource(R.string.name_title),
        subtitle = stringResource(R.string.name_subtitle),
        primaryLabel = stringResource(R.string.action_continue),
        onPrimary = nav.onNext, onBack = nav.onBack, onSkip = nav.onSkip,
        companion = { side ->
            StepCat(
                cat,
                if (displayName == null) stringResource(R.string.name_bubble_empty)
                else stringResource(R.string.name_bubble_filled, displayName),
                side,
            )
        },
    ) {
        NameField(input, onInput, onDone = nav.onNext)
    }
}

/** The cat is larger in a side pane, but stays modest when the window is short (landscape phone). */
@Composable
private fun StepCat(holder: MascotStateHolder, speech: String, inSidePane: Boolean) {
    val size = when {
        !inSidePane -> 128.dp
        LocalMeowWindow.current.shortHeight -> 112.dp
        else -> 184.dp
    }
    CompanionCat(holder, speech, catSize = size)
}

internal data class StepNavigation(
    val total: Int,
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onSkip: () -> Unit,
)
