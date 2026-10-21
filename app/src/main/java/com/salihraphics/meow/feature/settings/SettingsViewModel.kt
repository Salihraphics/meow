package com.salihraphics.meow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.account.AccountRepository
import com.salihraphics.meow.data.preferences.UserPreferencesRepository
import com.salihraphics.meow.domain.model.AccountState
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.SubjectCatalog
import com.salihraphics.meow.domain.model.ThemeMode
import com.salihraphics.meow.domain.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: UserPreferencesRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    val account: StateFlow<AccountState> = accountRepository.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState.SignedOut)

    val isSignInAvailable: Boolean = accountRepository.isSignInAvailable

    /** Null until the first read completes. */
    val preferences: StateFlow<UserPreferences?> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setExam(exam: ExamType?) = updateProfile {
        it.copy(exam = exam, subjects = SubjectCatalog.retainAvailable(it.subjects, exam))
    }

    fun setSubjects(subjects: Set<Subject>) = updateProfile { it.copy(subjects = subjects) }

    fun setLevel(level: StudyLevel?) = updateProfile { it.copy(level = level) }

    fun setSessionLength(length: SessionLength?) = updateProfile { it.copy(sessionLength = length) }

    fun setDisplayName(raw: String) = updateProfile { it.copy(displayName = StudyProfile.sanitizeName(raw)) }

    fun setMascotAnimations(enabled: Boolean) {
        viewModelScope.launch { repository.setMascotAnimationsEnabled(enabled) }
    }

    fun setImmediateFeedback(enabled: Boolean) {
        viewModelScope.launch { repository.setImmediateFeedback(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun restartOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.restartOnboarding()
            onDone()
        }
    }

    fun resetAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.resetAll()
            onDone()
        }
    }

    private fun updateProfile(transform: (StudyProfile) -> StudyProfile) {
        viewModelScope.launch { repository.updateProfile(transform) }
    }
}
