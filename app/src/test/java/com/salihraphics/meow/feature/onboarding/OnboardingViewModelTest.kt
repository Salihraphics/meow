package com.salihraphics.meow.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.UserPreferences
import com.salihraphics.meow.testing.FakeUserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun goTo(vm: OnboardingViewModel, step: OnboardingStep) {
        while (vm.state.value.step != step) vm.next()
    }

    @Test
    fun skippingEveryQuestionStillFinishesWithAnEmptyProfile() {
        val repo = FakeUserPreferencesRepository()
        val vm = OnboardingViewModel(SavedStateHandle(), repo)
        goTo(vm, OnboardingStep.EXAM)
        repeat(OnboardingStep.QUESTION_COUNT) { vm.skip() }
        assertEquals(OnboardingStep.READY, vm.state.value.step)

        vm.finish()
        assertTrue(vm.state.value.completed)
        assertTrue(repo.current.value.onboardingCompleted)
        assertTrue(repo.current.value.profile.isEmpty)
    }

    @Test
    fun answersAreSavedOnFinish() {
        val repo = FakeUserPreferencesRepository()
        val vm = OnboardingViewModel(SavedStateHandle(), repo)
        vm.selectExam(ExamType.YKS)
        vm.toggleSubject(Subject.MATH)
        vm.toggleSubject(Subject.PHYSICS)
        vm.selectSession(SessionLength.MIN_30)
        vm.updateName("  Salih ")
        vm.finish()
        assertEquals(
            StudyProfile(
                exam = ExamType.YKS,
                subjects = setOf(Subject.MATH, Subject.PHYSICS),
                sessionLength = SessionLength.MIN_30,
                displayName = "Salih",
            ),
            repo.current.value.profile,
        )
    }

    @Test
    fun changingExamPrunesSubjects() {
        val vm = OnboardingViewModel(SavedStateHandle(), FakeUserPreferencesRepository())
        vm.selectExam(ExamType.YKS)
        vm.toggleSubject(Subject.PHYSICS)
        vm.toggleSubject(Subject.MATH)
        vm.selectExam(ExamType.LGS)
        assertEquals(setOf(Subject.MATH), vm.state.value.subjects)
    }

    @Test
    fun skipClearsOnlyTheCurrentAnswer() {
        val vm = OnboardingViewModel(SavedStateHandle(), FakeUserPreferencesRepository())
        vm.selectExam(ExamType.LGS)
        goTo(vm, OnboardingStep.SESSION)
        vm.selectSession(SessionLength.MIN_60)
        vm.skip()
        assertEquals(null, vm.state.value.sessionLength)
        assertEquals(ExamType.LGS, vm.state.value.exam)
        assertEquals(OnboardingStep.NAME, vm.state.value.step)
    }

    @Test
    fun backWalksThroughStepsAndStopsAtTheStart() {
        val vm = OnboardingViewModel(SavedStateHandle(), FakeUserPreferencesRepository())
        goTo(vm, OnboardingStep.SUBJECTS)
        vm.back()
        assertEquals(OnboardingStep.EXAM, vm.state.value.step)
        repeat(5) { vm.back() }
        assertEquals(OnboardingStep.WELCOME, vm.state.value.step)
    }

    @Test
    fun progressSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val first = OnboardingViewModel(handle, FakeUserPreferencesRepository())
        first.selectExam(ExamType.YKS)
        first.toggleSubject(Subject.CHEMISTRY)
        goTo(first, OnboardingStep.LEVEL)

        val restored = OnboardingViewModel(handle, FakeUserPreferencesRepository())
        assertEquals(OnboardingStep.LEVEL, restored.state.value.step)
        assertEquals(ExamType.YKS, restored.state.value.exam)
        assertEquals(setOf(Subject.CHEMISTRY), restored.state.value.subjects)
    }

    @Test
    fun revisitingOnboardingPrefillsSavedAnswers() {
        val saved = StudyProfile(exam = ExamType.SCHOOL, displayName = "Ada")
        val vm = OnboardingViewModel(SavedStateHandle(), FakeUserPreferencesRepository(UserPreferences(profile = saved)))
        assertEquals(ExamType.SCHOOL, vm.state.value.exam)
        assertEquals("Ada", vm.state.value.nameInput)
    }

    @Test
    fun finishingTwiceSavesOnce() {
        val repo = FakeUserPreferencesRepository()
        val vm = OnboardingViewModel(SavedStateHandle(), repo)
        vm.finish()
        vm.updateName("Late edit")
        vm.finish()
        assertEquals(null, repo.current.value.profile.displayName)
    }
}
