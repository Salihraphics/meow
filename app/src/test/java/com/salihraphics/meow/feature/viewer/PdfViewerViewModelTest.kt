package com.salihraphics.meow.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.salihraphics.meow.testing.FakeAnnotationRepository
import com.salihraphics.meow.testing.FakePdfLibraryRepository
import com.salihraphics.meow.testing.FakeSessionFactory
import com.salihraphics.meow.testing.sampleDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfViewerViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repo: FakePdfLibraryRepository,
        sessions: FakeSessionFactory,
        id: String = "doc-1",
        savedState: SavedStateHandle = SavedStateHandle(),
    ): PdfViewerViewModel =
        ViewModelProvider.create(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
                PdfViewerViewModel(id, savedState, repo, sessions, FakeAnnotationRepository(), scope) as T
        })[PdfViewerViewModel::class]

    @Test
    fun opensAtTheLastReadPage() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument(lastPage = 9)) }
        val vm = viewModel(repo, FakeSessionFactory(pages = 24))
        advanceUntilIdle()
        val ready = vm.state.value as ViewerUiState.Ready
        assertEquals(9, ready.initialPage)
        assertEquals(24, ready.loader.pageCount)
        assertEquals(ViewportSnapshot(pageIndex = 9), vm.initialViewport())
    }

    @Test
    fun aRequestedPageWinsOverTheLastReadPageButNotOverARestoredViewport() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument(lastPage = 9)) }
        val fromNote = viewModel(repo, FakeSessionFactory(pages = 24), savedState = SavedStateHandle(mapOf(VIEWER_START_PAGE to 17)))
        advanceUntilIdle()
        assertEquals(17, (fromNote.state.value as ViewerUiState.Ready).initialPage)
        store.clear()

        // -1 is the navigation default: where the student left off.
        val plain = viewModel(repo, FakeSessionFactory(pages = 24), savedState = SavedStateHandle(mapOf(VIEWER_START_PAGE to -1)))
        advanceUntilIdle()
        assertEquals(9, (plain.state.value as ViewerUiState.Ready).initialPage)
        store.clear()

        val tooFar = viewModel(repo, FakeSessionFactory(pages = 5), savedState = SavedStateHandle(mapOf(VIEWER_START_PAGE to 40)))
        advanceUntilIdle()
        assertEquals(4, (tooFar.state.value as ViewerUiState.Ready).initialPage)
    }

    @Test
    fun lastPageBeyondTheDocumentIsClamped() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument(lastPage = 99)) }
        val vm = viewModel(repo, FakeSessionFactory(pages = 5))
        advanceUntilIdle()
        assertEquals(4, (vm.state.value as ViewerUiState.Ready).initialPage)
    }

    @Test
    fun missingDocumentOrFileIsAnError() = scope.runTest {
        val noEntry = viewModel(FakePdfLibraryRepository(tmp.root), FakeSessionFactory())
        advanceUntilIdle()
        assertEquals(ViewerUiState.Error(ViewerError.MISSING), noEntry.state.value)
    }

    @Test
    fun deletedFileIsAnError() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument(), withFile = false) }
        val vm = viewModel(repo, FakeSessionFactory())
        advanceUntilIdle()
        assertEquals(ViewerUiState.Error(ViewerError.MISSING), vm.state.value)
    }

    @Test
    fun passwordAndDamageAreReportedSeparately() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument()) }
        val locked = viewModel(repo, FakeSessionFactory(failure = SecurityException("pw")), id = "doc-1")
        advanceUntilIdle()
        assertEquals(ViewerUiState.Error(ViewerError.PASSWORD_PROTECTED), locked.state.value)

        store.clear()
        val broken = viewModel(repo, FakeSessionFactory(failure = IOException("bad")), id = "doc-1")
        advanceUntilIdle()
        assertEquals(ViewerUiState.Error(ViewerError.DAMAGED), broken.state.value)
    }

    @Test
    fun readingPositionIsSavedAfterScrollingSettles() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument()) }
        val vm = viewModel(repo, FakeSessionFactory())
        advanceUntilIdle()
        repo.readings.clear()

        vm.onPageChanged(3)
        vm.onPageChanged(4)
        vm.onPageChanged(5)
        advanceTimeBy(300)
        runCurrent()
        assertTrue(repo.readings.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("doc-1" to 5), repo.readings)
    }

    @Test
    fun exactViewportOutlivesTheUiWithoutReopeningThePdf() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument(lastPage = 2)) }
        val sessions = FakeSessionFactory()
        val vm = viewModel(repo, sessions)
        advanceUntilIdle()
        val firstSession = sessions.opened

        // What the viewer reports while the student reads, then rotation recreates the UI.
        val reading = ViewportSnapshot(pageIndex = 6, pageOffsetFraction = 0.42f, zoom = 2.5f, horizontalFraction = 0.7f)
        vm.onViewportChanged(reading)

        // The recreated screen asks the same ViewModel where to start.
        assertEquals(reading, vm.initialViewport())
        assertTrue(sessions.opened === firstSession)
        assertEquals(1, sessions.openCount)
        assertTrue(vm.state.value is ViewerUiState.Ready)
    }

    @Test
    fun exactViewportSurvivesProcessDeath() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument(lastPage = 0)) }
        val handle = SavedStateHandle()
        val before = viewModel(repo, FakeSessionFactory(), savedState = handle)
        advanceUntilIdle()
        before.onViewportChanged(ViewportSnapshot(pageIndex = 4, pageOffsetFraction = 0.25f, zoom = 1.5625f, horizontalFraction = 0.5f))
        store.clear()

        // A new ViewModel built from the same saved state, as after the process is restored.
        val after = viewModel(repo, FakeSessionFactory(), savedState = handle)
        advanceUntilIdle()
        assertEquals(4, (after.state.value as ViewerUiState.Ready).initialPage)
        assertEquals(ViewportSnapshot(4, 0.25f, 1.5625f, 0.5f), after.initialViewport())
    }

    @Test
    fun savedViewportIsClampedIfTheDocumentShrank() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument()) }
        val handle = SavedStateHandle()
        viewModel(repo, FakeSessionFactory(pages = 30), savedState = handle).also { advanceUntilIdle() }
            .onViewportChanged(ViewportSnapshot(pageIndex = 25, zoom = 2f))
        store.clear()
        val after = viewModel(repo, FakeSessionFactory(pages = 10), savedState = handle)
        advanceUntilIdle()
        assertEquals(9, after.initialViewport().pageIndex)
        assertEquals(2f, after.initialViewport().zoom)
    }

    @Test
    fun closingTheScreenSavesThePageAndClosesTheRenderer() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument()) }
        val sessions = FakeSessionFactory()
        val vm = viewModel(repo, sessions)
        advanceUntilIdle()
        repo.readings.clear()

        vm.onPageChanged(11)
        store.clear() // the screen goes away before the save delay passes
        advanceUntilIdle()

        assertTrue(sessions.opened!!.closed)
        assertEquals(listOf("doc-1" to 11), repo.readings)
    }

    @Test
    fun rotationIsRememberedPerPdfAndNeverTouchesAnnotations() = scope.runTest {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument()) }
        val vm = viewModel(repo, FakeSessionFactory(pages = 4))
        advanceUntilIdle()
        assertEquals(0, vm.rotation.value)
        vm.setRotation(90)
        assertEquals(90, vm.rotation.value)
        vm.setRotation(360) // a full turn is the original orientation
        assertEquals(0, vm.rotation.value)
        vm.setRotation(-90)
        assertEquals(270, vm.rotation.value)
        advanceUntilIdle()
        assertEquals(270, repo.get("doc-1")!!.viewRotation)

        // Reopening the PDF (a new screen) comes back turned the same way.
        store.clear()
        val again = viewModel(repo, FakeSessionFactory(pages = 4))
        advanceUntilIdle()
        assertEquals(270, again.rotation.value)
    }
}
