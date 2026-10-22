package com.salihraphics.meow.feature.library

import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import com.salihraphics.meow.domain.model.LibrarySort
import com.salihraphics.meow.domain.model.LibraryFolder
import com.salihraphics.meow.domain.model.FolderResult
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.library.PdfLibraryRepository
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.domain.model.PdfImportError
import com.salihraphics.meow.domain.model.PdfImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    data class FolderCreated(val name: String) : LibraryMessage
    data object FolderNameTaken : LibraryMessage
    data object FolderNameBlank : LibraryMessage
    data class FolderDeleted(val name: String, val moved: Int) : LibraryMessage
    data class Moved(val name: String, val folder: String?) : LibraryMessage
}

data class LibraryUiState(
    val isImporting: Boolean = false,
    val message: LibraryMessage? = null,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.All,
)

class LibraryViewModel(
    private val repository: PdfLibraryRepository,
    keyStatuses: Flow<Map<String, KeyStatus>> = flowOf(emptyMap()),
    /** The saved sort order and how to change it (user preferences). */
    sortSetting: Flow<LibrarySort> = flowOf(LibrarySort.RECENT),
    private val saveSort: suspend (LibrarySort) -> Unit = {},
    /** Whether a document's own copy of the PDF is on disk. */
    private val fileExists: (String) -> Boolean = { true },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** Answer-key status per document id. */
    val keyStatuses: StateFlow<Map<String, KeyStatus>> = keyStatuses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())


    /** Null while the first read is in flight. */
    val documents: StateFlow<List<PdfDocument>?> = repository.documents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    val folders: StateFlow<List<LibraryFolder>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sort: StateFlow<LibrarySort> = sortSetting.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySort.RECENT)

    /** The documents to show: filtered, searched and sorted; null while loading. */
    val visible: StateFlow<List<PdfDocument>?> = combine(repository.documents, _state, sort) { docs, state, sort ->
        LibraryOrganizer.arrange(docs, state.filter, state.query, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Documents whose PDF copy is gone (e.g. app storage cleared by hand); shown as unavailable. */
    val missing: StateFlow<Set<String>> = repository.documents
        .map { docs -> withContext(io) { docs.filterNot { fileExists(it.id) }.map { it.id }.toSet() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

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

    fun setQuery(query: String) = _state.update { it.copy(query = query.take(MAX_QUERY)) }

    fun setFilter(filter: LibraryFilter) = _state.update { it.copy(filter = filter) }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { saveSort(sort) }
    }

    fun createFolder(name: String, thenMove: PdfDocument? = null) {
        viewModelScope.launch {
            when (val result = repository.createFolder(name)) {
                is FolderResult.Done -> {
                    thenMove?.let { repository.moveToFolder(it.id, result.folder.id) }
                    message(LibraryMessage.FolderCreated(result.folder.name))
                }
                FolderResult.DuplicateName -> message(LibraryMessage.FolderNameTaken)
                FolderResult.BlankName, FolderResult.NotFound -> message(LibraryMessage.FolderNameBlank)
            }
        }
    }

    /** Folders named after the student's subjects; ones that exist already are skipped. */
    fun createFolders(names: List<String>) {
        viewModelScope.launch { names.forEach { repository.createFolder(it) } }
    }

    fun renameFolder(folder: LibraryFolder, name: String) {
        viewModelScope.launch {
            when (repository.renameFolder(folder.id, name)) {
                FolderResult.DuplicateName -> message(LibraryMessage.FolderNameTaken)
                FolderResult.BlankName -> message(LibraryMessage.FolderNameBlank)
                else -> Unit
            }
        }
    }

    /** Deletes the folder only; its documents move to "Klasörsüz". */
    fun deleteFolder(folder: LibraryFolder) {
        viewModelScope.launch {
            val moved = documents.value.orEmpty().count { it.folderId == folder.id }
            repository.deleteFolder(folder.id)
            if ((_state.value.filter as? LibraryFilter.Folder)?.id == folder.id) setFilter(LibraryFilter.All)
            message(LibraryMessage.FolderDeleted(folder.name, moved))
        }
    }

    fun moveToFolder(document: PdfDocument, folder: LibraryFolder?) {
        viewModelScope.launch {
            repository.moveToFolder(document.id, folder?.id)
            message(LibraryMessage.Moved(document.displayName, folder?.name))
        }
    }

    fun togglePinned(document: PdfDocument) {
        viewModelScope.launch { repository.setPinned(document.id, !document.pinned) }
    }

    private fun message(message: LibraryMessage) = _state.update { it.copy(message = message) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private companion object {
        const val MAX_QUERY = 80
    }
}
