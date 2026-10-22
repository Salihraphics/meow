package com.salihraphics.meow.screenshots

import androidx.compose.runtime.Composable
import com.salihraphics.meow.data.dashboard.NoteSummary
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.ExamGoalType
import com.salihraphics.meow.domain.model.LibraryFolder
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.feature.home.HomeActions
import com.salihraphics.meow.feature.home.HomeContent
import com.salihraphics.meow.feature.home.HomeDocuments
import com.salihraphics.meow.feature.home.HomeNote
import com.salihraphics.meow.feature.home.HomeViewModel
import com.salihraphics.meow.feature.home.StudySummary
import com.salihraphics.meow.testing.sampleDocument
import java.util.Calendar

/** 22 Oct 2026, 21:08:14 on the device's clock: fixed, so screenshots don't change by the day. */
val sampleNow: Long = Calendar.getInstance().apply { clear(); set(2026, 9, 22, 21, 8, 14) }.timeInMillis

private const val HOUR = 3_600_000L

val sampleDocuments = listOf(
    sampleDocument("1", "TYT Matematik Soru Bankası", pages = 312, lastPage = 41).copy(lastOpenedAt = sampleNow - 2 * HOUR, folderId = "f1", pinned = true),
    sampleDocument("2", "AYT Fizik Deneme 3", pages = 48, lastPage = 12).copy(lastOpenedAt = sampleNow - 26 * HOUR, folderId = "f2"),
    sampleDocument("3", "Paragraf 50 Günde", pages = 120, lastPage = 7).copy(lastOpenedAt = sampleNow - 4 * 24 * HOUR, pinned = true),
    sampleDocument("4", "Kimya Konu Anlatımı", pages = 220),
)

val sampleFolders = listOf(LibraryFolder("f1", "Matematik"), LibraryFolder("f2", "Fizik"))

val sampleNotes = listOf(
    HomeNote(NoteSummary("n1", "1", 41, "Türevde zincir kuralını tekrar et, 12. soru!", false, sampleNow - HOUR), "TYT Matematik Soru Bankası"),
    HomeNote(NoteSummary("n2", "2", 11, "", true, sampleNow - 26 * HOUR), "AYT Fizik Deneme 3"),
)

val sampleSummary = StudySummary(todayMs = 50 * 60_000L, weekMs = 205 * 60_000L, weekSessions = 7, weekCompleted = 5, weekTests = 3, weekCorrect = 92, weekIncorrect = 21)

@Composable
fun SampleHome(
    profile: StudyProfile,
    full: Boolean = true,
    goal: ExamGoal? = ExamGoal(ExamGoalType.YKS),
    now: Long = sampleNow,
    actions: HomeActions = HomeActions(),
) = HomeContent(
    profile = profile,
    documents = if (full) HomeViewModel.arrange(sampleDocuments, sampleFolders) else HomeDocuments(),
    notes = if (full) sampleNotes else emptyList(),
    summary = if (full) sampleSummary else StudySummary(),
    examGoal = goal,
    thumbnails = { null },
    actions = actions,
    clock = { now },
    ticking = false,
)
