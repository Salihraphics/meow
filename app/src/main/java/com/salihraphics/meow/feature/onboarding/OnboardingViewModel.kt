package com.salihraphics.meow.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.preferences.UserPreferencesRepository
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.SubjectCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val exam: ExamType? = null,
    val subjects: Set<Subject> = emptySet(),
    val level: StudyLevel? = null,
    val sessionLength: SessionLength? = null,
    /** Raw text field contents; sanitized only when saved. */
    val nameInput: String = "",
    /** True when the last navigation moved forward; drives transition direction. */
    val movingForward: Boolean = true,
    val isSaving: Boolean = false,
    val completed: Boolean = false,
) {
    val availableSubjects: List<Subject> get() = SubjectCatalog.subjectsFor(exam)

    val displayName: String? get() = StudyProfile.sanitizeName(nameInput)

    fun toProfile() = StudyProfile(
        exam = exam,
        subjects = SubjectCatalog.retainAvailable(subjects, exam),
        level = level,
        sessionLength = sessionLength,
        displayName = displayName,
    )
}

class OnboardingViewModel(
    private val savedState: SavedStateHandle,
    private val repository: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(restore() ?: OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var touched = savedState.contains(KEY_STEP)

    init {
        if (!touched) prefillFromSavedProfile()
    }

    fun next() = move(forward = true)

    fun back() = move(forward = false)

    /** Skipping clears this step's answer: "skip" means "I'd rather not say", not "keep it". */
    fun skip() {
        edit {
            when (step) {
                OnboardingStep.EXAM -> copy(exam = null, subjects = SubjectCatalog.retainAvailable(subjects, null))
                OnboardingStep.SUBJECTS -> copy(subjects = emptySet())
                OnboardingStep.LEVEL -> copy(level = null)
                OnboardingStep.SESSION -> copy(sessionLength = null)
                OnboardingStep.NAME -> copy(nameInput = "")
                else -> this
            }
        }
        next()
    }

    fun selectExam(exam: ExamType) = edit {
        val newExam = if (this.exam == exam) null else exam
        copy(exam = newExam, subjects = SubjectCatalog.retainAvailable(subjects, newExam))
    }

    fun toggleSubject(subject: Subject) = edit {
        copy(subjects = if (subject in subjects) subjects - subject else subjects + subject)
    }

    fun selectLevel(level: StudyLevel) = edit { copy(level = if (this.level == level) null else level) }

    fun selectSession(length: SessionLength) = edit {
        copy(sessionLength = if (sessionLength == length) null else length)
    }

    fun updateName(input: String) = edit { copy(nameInput = input.take(StudyProfile.MAX_NAME_LENGTH)) }

    fun finish() {
        val current = _state.value
        if (current.isSaving || current.completed) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.completeOnboarding(current.toProfile())
            _state.update { it.copy(isSaving = false, completed = true) }
            savedState.remove<String>(KEY_STEP)
        }
    }

    private fun move(forward: Boolean) = edit {
        val target = if (forward) step.next() else step.previous() ?: step
        copy(step = target, movingForward = forward)
    }

    private inline fun edit(transform: OnboardingUiState.() -> OnboardingUiState) {
        touched = true
        _state.update(transform)
        persist(_state.value)
    }

    private fun prefillFromSavedProfile() {
        viewModelScope.launch {
            val profile = repository.preferences.first().profile
            if (touched || profile.isEmpty) return@launch
            _state.update {
                it.copy(
                    exam = profile.exam,
                    subjects = profile.subjects,
                    level = profile.level,
                    sessionLength = profile.sessionLength,
                    nameInput = profile.displayName.orEmpty(),
                )
            }
        }
    }

    private fun persist(s: OnboardingUiState) {
        savedState[KEY_STEP] = s.step.name
        savedState[KEY_EXAM] = s.exam?.name
        savedState[KEY_SUBJECTS] = ArrayList(s.subjects.map { it.name })
        savedState[KEY_LEVEL] = s.level?.name
        savedState[KEY_SESSION] = s.sessionLength?.name
        savedState[KEY_NAME] = s.nameInput
    }

    private fun restore(): OnboardingUiState? {
        val step = savedState.get<String>(KEY_STEP)?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
            ?: return null
        return OnboardingUiState(
            step = step,
            exam = savedState.get<String>(KEY_EXAM)?.let { n -> ExamType.entries.firstOrNull { it.name == n } },
            subjects = savedState.get<ArrayList<String>>(KEY_SUBJECTS).orEmpty()
                .mapNotNullTo(LinkedHashSet()) { n -> Subject.entries.firstOrNull { it.name == n } },
            level = savedState.get<String>(KEY_LEVEL)?.let { n -> StudyLevel.entries.firstOrNull { it.name == n } },
            sessionLength = savedState.get<String>(KEY_SESSION)?.let { n -> SessionLength.entries.firstOrNull { it.name == n } },
            nameInput = savedState.get<String>(KEY_NAME).orEmpty(),
        )
    }

    private companion object {
        const val KEY_STEP = "onboarding.step"
        const val KEY_EXAM = "onboarding.exam"
        const val KEY_SUBJECTS = "onboarding.subjects"
        const val KEY_LEVEL = "onboarding.level"
        const val KEY_SESSION = "onboarding.session"
        const val KEY_NAME = "onboarding.name"
    }
}
