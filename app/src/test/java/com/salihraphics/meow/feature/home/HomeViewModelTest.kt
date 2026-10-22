package com.salihraphics.meow.feature.home

import com.salihraphics.meow.data.dashboard.NoteSummary
import com.salihraphics.meow.data.dashboard.StudyLog
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.ExamGoalType
import com.salihraphics.meow.domain.model.LibraryFolder
import com.salihraphics.meow.testing.FakeDashboardRepository
import com.salihraphics.meow.testing.FakeUserPreferencesRepository
import com.salihraphics.meow.testing.sampleDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val istanbul = TimeZone.getTimeZone("Europe/Istanbul")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) =
        Calendar.getInstance(istanbul).apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis

    @Test
    fun continueIsTheLastOpenedPdfAtItsSavedPage() {
        val a = sampleDocument("a", "A", lastPage = 3).copy(lastOpenedAt = 100)
        val b = sampleDocument("b", "B", lastPage = 41).copy(lastOpenedAt = 300, folderId = "f")
        val c = sampleDocument("c", "C").copy(importedAt = 500, pinned = true)
        val home = HomeViewModel.arrange(listOf(a, b, c), listOf(LibraryFolder("f", "Fizik")))
        assertEquals("b", home.continueWith!!.id)
        assertEquals(41, home.continueWith!!.lastPageIndex)
        assertEquals(listOf("a"), home.recent.map { it.id })
        assertEquals(listOf("c"), home.pinned.map { it.id })
        assertEquals(3, home.count)
        assertEquals("Fizik", home.folderNames["f"])
    }

    @Test
    fun withNothingOpenedYetTheNewestPdfIsOfferedAndAnEmptyLibraryOffersNothing() {
        val old = sampleDocument("old").copy(importedAt = 1)
        val new = sampleDocument("new").copy(importedAt = 2)
        assertEquals("new", HomeViewModel.arrange(listOf(old, new)).continueWith!!.id)
        val empty = HomeViewModel.arrange(emptyList())
        assertNull(empty.continueWith)
        assertTrue(empty.recent.isEmpty() && empty.pinned.isEmpty())
    }

    @Test
    fun notesShowTheirPdfNameAndNotesOfRemovedPdfsDisappear() = runTest(dispatcher) {
        val docs = MutableStateFlow(listOf(sampleDocument("a", "Türev"), sampleDocument("b", "Limit")))
        val dash = FakeDashboardRepository().apply {
            notes.value = listOf(
                NoteSummary("n1", "a", 4, "Zincir kuralı", hasInk = false, editedAt = 2),
                NoteSummary("n2", "b", 0, "", hasInk = true, editedAt = 1),
            )
        }
        val vm = HomeViewModel(docs, MutableStateFlow(emptyList()), dash, MutableStateFlow(null), {}, now = { at(2026, 10, 22) }, zone = { istanbul })
        backgroundScope.launch { vm.notes.collect {} }
        assertEquals(listOf("Türev", "Limit"), vm.notes.value.map { it.documentName })
        docs.value = docs.value.filter { it.id == "a" }
        assertEquals(listOf("n1"), vm.notes.value.map { it.note.noteId })
    }

    @Test
    fun theSummaryComesFromRecordedSessionsAndRollsOverOnMonday() = runTest(dispatcher) {
        var now = at(2026, 10, 25, 23, 59) // Sunday
        val dash = FakeDashboardRepository().apply {
            study.value = listOf(StudyLog(30 * 60_000L, true, at(2026, 10, 25, 20)))
        }
        val vm = HomeViewModel(MutableStateFlow(emptyList()), MutableStateFlow(emptyList()), dash, MutableStateFlow(null), {}, now = { now }, zone = { istanbul })
        backgroundScope.launch { vm.summary.collect {} }
        assertEquals(30 * 60_000L, vm.summary.value!!.weekMs)
        assertEquals(at(2026, 10, 19), dash.studyQueries.last())

        now = at(2026, 10, 26, 0, 1) // Monday: a new week, nothing yet
        advanceTimeBy(61_000)
        assertTrue(vm.summary.value!!.isEmpty)
        assertEquals(at(2026, 10, 26), dash.studyQueries.last())
    }

    @Test
    fun theExamGoalIsSavedToPreferences() = runTest(dispatcher) {
        val prefs = FakeUserPreferencesRepository()
        val vm = HomeViewModel(
            MutableStateFlow(emptyList()), MutableStateFlow(emptyList()), FakeDashboardRepository(),
            prefs.preferences.map { it.examGoal }, prefs::setExamGoal,
        )
        backgroundScope.launch { vm.examGoal.collect {} }
        assertNull(vm.examGoal.value)
        vm.setExamGoal(ExamGoal(ExamGoalType.LGS))
        assertEquals(ExamGoal(ExamGoalType.LGS), vm.examGoal.value)
        vm.setExamGoal(null)
        assertNull(vm.examGoal.value)
    }
}
