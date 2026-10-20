package com.salihraphics.meow.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.library.PdfLibraryRepository
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.domain.model.PdfImportError
import com.salihraphics.meow.domain.model.PdfImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A one-off message for the snackbar. */
sealed interface LibraryMessage {
    data class Imported(val name: String) : LibraryMessage
    data class AlreadyInLibrary(val name: String) : LibraryMessage
    data class ImportFailed(val error: PdfImportError) : LibraryMessage
    data object RenameFailed : LibraryMessage
    data class Removed(val name: String) : LibraryMessage
}

data class LibraryUiState(
    val isImporting: Boolean = false,
    val message: LibraryMessage? = null,
)

class LibraryViewModel(private val repository: PdfLibraryRepository) : ViewModel() {

    /** Null while the first read is in flight. */
    val documents: StateFlow<List<PdfDocument>?> = repository.documents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** [uri] is null when the student closed the picker without choosing; that is not an error. */
    fun onDocumentPicked(uri: Uri?, fallbackName: String) {
        if (uri == null || _state.value.isImporting) return
        _state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val message = try {
                when (val result = repository.import(uri, fallbackName)) {
                    is PdfImportResult.Imported -> LibraryMessage.Imported(result.document.displayName)
                    is PdfImportResult.AlreadyInLibrary -> LibraryMessage.AlreadyInLibrary(result.document.displayName)
                    is PdfImportResult.Failed -> LibraryMessage.ImportFailed(result.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibraryMessage.ImportFailed(PdfImportError.STORAGE)
            }
            _state.update { it.copy(isImporting = false, message = message) }
        }
    }

    fun rename(document: PdfDocument, newName: String) {
        viewModelScope.launch {
            if (!repository.rename(document.id, newName)) {
                _state.update { it.copy(message = LibraryMessage.RenameFailed) }
            }
        }
    }

    fun remove(document: PdfDocument) {
        viewModelScope.launch {
            repository.remove(document.id)
            _state.update { it.copy(message = LibraryMessage.Removed(document.displayName)) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
