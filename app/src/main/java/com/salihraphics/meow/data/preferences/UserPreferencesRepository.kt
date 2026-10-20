package com.salihraphics.meow.data.preferences

import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.ThemeMode
import com.salihraphics.meow.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    /** Saves the onboarding answers and marks onboarding as done. */
    suspend fun completeOnboarding(profile: StudyProfile)

    suspend fun updateProfile(transform: (StudyProfile) -> StudyProfile)

    suspend fun setMascotAnimationsEnabled(enabled: Boolean)

    suspend fun setThemeMode(mode: ThemeMode)

    /** Shows onboarding again on next navigation while keeping the saved profile. */
    suspend fun restartOnboarding()

    /** Forgets everything, as if the app were freshly installed. */
    suspend fun resetAll()
}
