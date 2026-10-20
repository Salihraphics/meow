package com.salihraphics.meow.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.feature.onboarding.steps.ExamStep
import com.salihraphics.meow.feature.onboarding.steps.LevelStep
import com.salihraphics.meow.feature.onboarding.steps.MeetStep
import com.salihraphics.meow.feature.onboarding.steps.NameStep
import com.salihraphics.meow.feature.onboarding.steps.ReadyStep
import com.salihraphics.meow.feature.onboarding.steps.SessionStep
import com.salihraphics.meow.feature.onboarding.steps.StepNavigation
import com.salihraphics.meow.feature.onboarding.steps.SubjectsStep
import com.salihraphics.meow.feature.onboarding.steps.WelcomeStep

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel, onCompleted: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latestOnCompleted by rememberUpdatedState(onCompleted)
    LaunchedEffect(state.completed) { if (state.completed) latestOnCompleted() }
    OnboardingContent(
        state = state,
        actions = OnboardingActions(
            onNext = viewModel::next,
            onBack = viewModel::back,
            onSkip = viewModel::skip,
            onSelectExam = viewModel::selectExam,
            onToggleSubject = viewModel::toggleSubject,
            onSelectLevel = viewModel::selectLevel,
            onSelectSession = viewModel::selectSession,
            onNameChange = viewModel::updateName,
            onFinish = viewModel::finish,
        ),
    )
}

data class OnboardingActions(
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onSelectExam: (ExamType) -> Unit = {},
    val onToggleSubject: (Subject) -> Unit = {},
    val onSelectLevel: (StudyLevel) -> Unit = {},
    val onSelectSession: (SessionLength) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onFinish: () -> Unit = {},
)

/** Stateless onboarding UI; everything it shows comes from [state]. */
@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    // System back walks back through the questions; on the intro screens it leaves the app.
    BackHandler(enabled = state.step.ordinal > OnboardingStep.MEET.ordinal) { actions.onBack() }

    val nav = StepNavigation(
        total = OnboardingStep.QUESTION_COUNT,
        onNext = actions.onNext,
        onBack = actions.onBack,
        onSkip = actions.onSkip,
    )

    AnimatedContent(
        targetState = state.step,
        transitionSpec = { stepTransition(state.movingForward) },
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        label = "onboardingStep",
    ) { step ->
        if (step == OnboardingStep.WELCOME) {
            WelcomeStep(onContinue = actions.onNext)
        } else {
            CenteredColumn {
                when (step) {
                    OnboardingStep.WELCOME, OnboardingStep.MEET -> MeetStep(onContinue = actions.onNext)
                    OnboardingStep.EXAM -> InsetsBox { ExamStep(state.exam, actions.onSelectExam, nav) }
                    OnboardingStep.SUBJECTS -> InsetsBox {
                        SubjectsStep(state.availableSubjects, state.subjects, actions.onToggleSubject, nav)
                    }
                    OnboardingStep.LEVEL -> InsetsBox { LevelStep(state.level, actions.onSelectLevel, nav) }
                    OnboardingStep.SESSION -> InsetsBox { SessionStep(state.sessionLength, actions.onSelectSession, nav) }
                    OnboardingStep.NAME -> InsetsBox { NameStep(state.nameInput, state.displayName, actions.onNameChange, nav) }
                    OnboardingStep.READY -> ReadyStep(state.displayName, state.isSaving, actions.onFinish)
                }
            }
        }
    }
}

/** Keeps content readable on tablets and in landscape. */
@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight()) { content() }
    }
}

/** Pads for status bar, navigation bar, cutouts and the keyboard. */
@Composable
private fun InsetsBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { content() }
}

private fun AnimatedContentTransitionScope<OnboardingStep>.stepTransition(forward: Boolean): ContentTransform {
    if (initialState == OnboardingStep.WELCOME) {
        return fadeIn(tween(450)) togetherWith fadeOut(tween(350))
    }
    val direction = if (forward) 1 else -1
    return (slideInHorizontally(tween(320)) { direction * it / 6 } + fadeIn(tween(260))) togetherWith
        (slideOutHorizontally(tween(320)) { -direction * it / 6 } + fadeOut(tween(200)))
}
