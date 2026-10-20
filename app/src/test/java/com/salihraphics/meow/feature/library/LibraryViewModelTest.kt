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
}
