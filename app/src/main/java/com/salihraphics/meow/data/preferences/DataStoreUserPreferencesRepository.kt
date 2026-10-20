package com.salihraphics.meow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.ThemeMode
import com.salihraphics.meow.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userPreferencesStore by preferencesDataStore(name = "user_preferences")

fun DataStoreUserPreferencesRepository(context: Context): DataStoreUserPreferencesRepository =
    DataStoreUserPreferencesRepository(context.applicationContext.userPreferencesStore)

/** Enums are stored by name so reordering them never corrupts saved data. */
class DataStoreUserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val exam = stringPreferencesKey("exam")
        val subjects = stringSetPreferencesKey("subjects")
        val level = stringPreferencesKey("level")
        val sessionLength = stringPreferencesKey("session_length")
        val displayName = stringPreferencesKey("display_name")
        val mascotAnimations = booleanPreferencesKey("mascot_animations")
        val themeMode = stringPreferencesKey("theme_mode")
    }

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toUserPreferences() }

    override suspend fun completeOnboarding(profile: StudyProfile) {
        dataStore.edit {
            it.writeProfile(profile)
            it[Keys.onboardingCompleted] = true
        }
    }

    override suspend fun updateProfile(transform: (StudyProfile) -> StudyProfile) {
        dataStore.edit { it.writeProfile(transform(it.readProfile())) }
    }

    override suspend fun setMascotAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.mascotAnimations] = enabled }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    override suspend fun restartOnboarding() {
        dataStore.edit { it[Keys.onboardingCompleted] = false }
    }

    override suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toUserPreferences() = UserPreferences(
        onboardingCompleted = this[Keys.onboardingCompleted] ?: false,
        profile = readProfile(),
        mascotAnimationsEnabled = this[Keys.mascotAnimations] ?: true,
        themeMode = enumOrNull<ThemeMode>(this[Keys.themeMode]) ?: ThemeMode.SYSTEM,
    )

    private fun Preferences.readProfile() = StudyProfile(
        exam = enumOrNull<ExamType>(this[Keys.exam]),
        subjects = this[Keys.subjects].orEmpty().mapNotNullTo(LinkedHashSet()) { enumOrNull<Subject>(it) },
        level = enumOrNull<StudyLevel>(this[Keys.level]),
        sessionLength = enumOrNull<SessionLength>(this[Keys.sessionLength]),
        displayName = StudyProfile.sanitizeName(this[Keys.displayName]),
    )

    private fun MutablePreferences.writeProfile(profile: StudyProfile) {
        setOrRemove(Keys.exam, profile.exam?.name)
        if (profile.subjects.isEmpty()) remove(Keys.subjects) else this[Keys.subjects] = profile.subjects.map { it.name }.toSet()
        setOrRemove(Keys.level, profile.level?.name)
        setOrRemove(Keys.sessionLength, profile.sessionLength?.name)
        setOrRemove(Keys.displayName, StudyProfile.sanitizeName(profile.displayName))
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }
}
