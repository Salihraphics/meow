package com.salihraphics.meow.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.salihraphics.meow.data.preferences.DataStoreUserPreferencesRepository
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.ThemeMode
import com.salihraphics.meow.domain.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreUserPreferencesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun repository(fileName: String = "prefs.preferences_pb") = DataStoreUserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve(fileName) },
    )

    @After fun tearDown() = scope.cancel()

    @Test
    fun freshInstallHasDefaults() = runBlocking {
        assertEquals(UserPreferences(), repository().preferences.first())
    }

    @Test
    fun completingOnboardingPersistsEveryAnswer() = runBlocking {
        val repo = repository()
        val profile = StudyProfile(
            exam = ExamType.YKS,
            subjects = setOf(Subject.MATH, Subject.PHYSICS),
            level = StudyLevel.MIXED,
            sessionLength = SessionLength.MIN_45,
            displayName = "Salih",
        )
        repo.completeOnboarding(profile)
        val saved = repo.preferences.first()
        assertTrue(saved.onboardingCompleted)
        assertEquals(profile, saved.profile)
    }

    @Test
    fun skippingEverythingStillCompletesOnboarding() = runBlocking {
        val repo = repository()
        repo.completeOnboarding(StudyProfile())
        val saved = repo.preferences.first()
        assertTrue(saved.onboardingCompleted)
        assertTrue(saved.profile.isEmpty)
    }

    @Test
    fun restartKeepsProfileAndResetForgetsIt() = runBlocking {
        val repo = repository()
        repo.completeOnboarding(StudyProfile(exam = ExamType.LGS))
        repo.setThemeMode(ThemeMode.DARK)

        repo.restartOnboarding()
        repo.preferences.first().let {
            assertFalse(it.onboardingCompleted)
            assertEquals(ExamType.LGS, it.profile.exam)
        }

        repo.resetAll()
        assertEquals(UserPreferences(), repo.preferences.first())
    }

    @Test
    fun clearingAFieldRemovesIt() = runBlocking {
        val repo = repository()
        repo.completeOnboarding(StudyProfile(level = StudyLevel.BEGINNER, displayName = "Ada"))
        repo.updateProfile { it.copy(level = null, displayName = StudyProfile.sanitizeName("  ")) }
        val profile = repo.preferences.first().profile
        assertEquals(null, profile.level)
        assertEquals(null, profile.displayName)
    }
}
