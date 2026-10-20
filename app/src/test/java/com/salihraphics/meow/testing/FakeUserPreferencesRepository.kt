package com.salihraphics.meow.testing

import com.salihraphics.meow.data.preferences.UserPreferencesRepository
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.ThemeMode
import com.salihraphics.meow.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeUserPreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {
    val current = MutableStateFlow(initial)
    override val preferences = current

    override suspend fun completeOnboarding(profile: StudyProfile) =
        current.update { it.copy(profile = profile, onboardingCompleted = true) }

    override suspend fun updateProfile(transform: (StudyProfile) -> StudyProfile) =
        current.update { it.copy(profile = transform(it.profile)) }

    override suspend fun setMascotAnimationsEnabled(enabled: Boolean) =
        current.update { it.copy(mascotAnimationsEnabled = enabled) }

    override suspend fun setThemeMode(mode: ThemeMode) = current.update { it.copy(themeMode = mode) }

    override suspend fun restartOnboarding() = current.update { it.copy(onboardingCompleted = false) }

    override suspend fun resetAll() = current.update { UserPreferences() }
}
