package com.salihraphics.meow.feature.library

import android.net.Uri
import com.salihraphics.meow.domain.model.PdfImportError
import com.salihraphics.meow.domain.model.PdfImportResult
import com.salihraphics.meow.testing.FakePdfLibraryRepository
import com.salihraphics.meow.testing.sampleDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import com.salihraphics.meow.domain.model.LibrarySort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val uri = Uri.parse("content://picker/1")

    @Test
    fun closingThePickerIsNotAnError() {
        val vm = LibraryViewModel(FakePdfLibraryRepository(tmp.root))
        vm.onDocumentPicked(null, "Adsız")
        assertEquals(LibraryUiState(), vm.state.value)
    }

    @Test
    fun importShowsProgressThenAMessage() {
        val repo = FakePdfLibraryRepository(tmp.root).apply {
            importGate = CompletableDeferred()
            nextImport = PdfImportResult.Imported(sampleDocument(name = "TYT"))
        }
        val vm = LibraryViewModel(repo)
        vm.onDocumentPicked(uri, "Adsız")
        assertTrue(vm.state.value.isImporting)

        vm.onDocumentPicked(uri, "Adsız") // a second pick while busy is ignored
        repo.importGate!!.complete(Unit)

        assertFalse(vm.state.value.isImporting)
        assertEquals(LibraryMessage.Imported("TYT"), vm.state.value.message)
        vm.messageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun duplicatesAndFailuresAreExplained() {
        val repo = FakePdfLibraryRepository(tmp.root)
        val vm = LibraryViewModel(repo)
        repo.nextImport = PdfImportResult.AlreadyInLibrary(sampleDocument(name = "Eski"))
        vm.onDocumentPicked(uri, "")
        assertEquals(LibraryMessage.AlreadyInLibrary("Eski"), vm.state.value.message)

        repo.nextImport = PdfImportResult.Failed(PdfImportError.PASSWORD_PROTECTED)
        vm.onDocumentPicked(uri, "")
        assertEquals(LibraryMessage.ImportFailed(PdfImportError.PASSWORD_PROTECTED), vm.state.value.message)
    }

    @Test
    fun blankRenameIsRefused() {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument()) }
        val vm = LibraryViewModel(repo)
        vm.rename(sampleDocument(), "  ")
        assertEquals(LibraryMessage.RenameFailed, vm.state.value.message)
        assertEquals("TYT Deneme 1", repo.current.value.single().displayName)
    }

    @Test
    fun foldersAreCreatedWithoutDuplicatesAndADocumentCanMoveStraightIn() = runTest(UnconfinedTestDispatcher()) {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument()) }
        val vm = LibraryViewModel(repo)
        vm.createFolder("Fizik", thenMove = sampleDocument())
        assertEquals(LibraryMessage.FolderCreated("Fizik"), vm.state.value.message)
        assertEquals("f1", repo.current.value.single().folderId)

        vm.createFolder("fizik")
        assertEquals(LibraryMessage.FolderNameTaken, vm.state.value.message)
        vm.createFolder("   ")
        assertEquals(LibraryMessage.FolderNameBlank, vm.state.value.message)
        assertEquals(1, repo.folderList.value.size)

        // Subject suggestions skip folders that already exist.
        vm.createFolders(listOf("Fizik", "Kimya"))
        assertEquals(listOf("Fizik", "Kimya"), repo.folderList.value.map { it.name })
    }

    @Test
    fun deletingTheShownFolderMovesItsDocumentsOutAndShowsEverything() = runTest(UnconfinedTestDispatcher()) {
        val repo = FakePdfLibraryRepository(tmp.root).apply {
            add(sampleDocument("a", "A"))
            add(sampleDocument("b", "B"))
        }
        val vm = LibraryViewModel(repo)
        backgroundScope.launch { vm.documents.collect {} }
        vm.createFolder("Deneme")
        val folder = repo.folderList.value.single()
        vm.moveToFolder(repo.current.value[0], folder)
        assertEquals(LibraryMessage.Moved("A", "Deneme"), vm.state.value.message)
        vm.moveToFolder(repo.current.value[1], folder)
        vm.setFilter(LibraryFilter.Folder(folder.id))

        vm.deleteFolder(folder)
        assertEquals(LibraryMessage.FolderDeleted("Deneme", 2), vm.state.value.message)
        assertEquals(LibraryFilter.All, vm.state.value.filter)
        assertTrue(repo.current.value.all { it.folderId == null })
        assertEquals(2, repo.current.value.size)
        assertTrue("PDF files stay", repo.current.value.all { repo.pdfFile(it.id).exists() })

        vm.moveToFolder(repo.current.value[0], null)
        assertEquals(LibraryMessage.Moved("A", null), vm.state.value.message)
    }

    @Test
    fun pinningSortingSearchAndMissingFiles() = runTest(UnconfinedTestDispatcher()) {
        val repo = FakePdfLibraryRepository(tmp.root).apply {
            add(sampleDocument("a", "Biyoloji"))
            add(sampleDocument("b", "Matematik"), withFile = false)
        }
        val sort = MutableStateFlow(LibrarySort.RECENT)
        val vm = LibraryViewModel(
            repo,
            sortSetting = sort,
            saveSort = { sort.value = it },
            fileExists = { repo.pdfFile(it).exists() },
            io = Dispatchers.Unconfined,
        )
        backgroundScope.launch { vm.visible.collect {} }
        backgroundScope.launch { vm.missing.collect {} }
        backgroundScope.launch { vm.sort.collect {} }

        assertEquals(setOf("b"), vm.missing.value)

        vm.setSort(LibrarySort.NAME)
        assertEquals(LibrarySort.NAME, vm.sort.value)
        assertEquals(listOf("a", "b"), vm.visible.value!!.map { it.id })

        vm.togglePinned(repo.current.value[1])
        assertTrue(repo.current.value[1].pinned)
        assertEquals(listOf("b", "a"), vm.visible.value!!.map { it.id })

        vm.setQuery("biyo")
        assertEquals(listOf("a"), vm.visible.value!!.map { it.id })
        vm.setFilter(LibraryFilter.Pinned)
        assertEquals(emptyList<String>(), vm.visible.value!!.map { it.id })
    }
}
