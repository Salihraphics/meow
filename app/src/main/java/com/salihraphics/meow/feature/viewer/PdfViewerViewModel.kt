package com.salihraphics.meow.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.annotation.AnnotationRepository
import com.salihraphics.meow.data.library.PdfLibraryRepository
import com.salihraphics.meow.data.pdf.PdfRenderSession
import com.salihraphics.meow.data.pdf.PdfRenderSessionFactory
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.feature.annotation.AnnotationSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

enum class ViewerError { MISSING, PASSWORD_PROTECTED, DAMAGED }

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Error(val error: ViewerError) : ViewerUiState
    data class Ready(
        val document: PdfDocument,
        val loader: PdfPageLoader,
        val initialPage: Int,
        val annotations: AnnotationSession,
    ) : ViewerUiState
}

class PdfViewerViewModel(
    private val documentId: String,
    /** Keeps the exact viewport across process death, not just rotation. */
    private val savedState: SavedStateHandle,
    private val repository: PdfLibraryRepository,
    private val sessions: PdfRenderSessionFactory,
    annotationRepository: AnnotationRepository,
    /** Outlives this screen, so the final reading position is saved after it closes. */
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var session: PdfRenderSession? = null

    /** Strokes, tool and undo history; here so rotation keeps them. */
    private val annotations = AnnotationSession(documentId, annotationRepository, viewModelScope)
    private var currentPage: Int? = null
    private var saveJob: Job? = null
    /** A page change not yet written to the library. */
    private var unsavedPage: Int? = null

    /**
     * Exact reading position and zoom. Lives here, not in the composition, because rotation
     * recreates the activity and everything remembered in it.
     */
    private var viewport: ViewportSnapshot? = savedState.get<FloatArray>(KEY_VIEWPORT)?.toViewport()

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val document = repository.get(documentId)
        val file = repository.pdfFile(documentId)
        if (document == null || !file.exists()) {
            _state.value = ViewerUiState.Error(ViewerError.MISSING)
            return
        }
        val opened = try {
            sessions.open(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            _state.value = ViewerUiState.Error(ViewerError.PASSWORD_PROTECTED)
            return
        } catch (e: IOException) {
            _state.value = ViewerUiState.Error(ViewerError.DAMAGED)
            return
        } catch (e: RuntimeException) {
            _state.value = ViewerUiState.Error(ViewerError.DAMAGED)
            return
        }
        try {
            currentCoroutineContext().ensureActive()
        } catch (e: CancellationException) {
            opened.close() // The screen closed while the PDF was opening.
            throw e
        }
        if (opened.pageSizes.isEmpty()) {
            opened.close()
            _state.value = ViewerUiState.Error(ViewerError.DAMAGED)
            return
        }
        session = opened
        val lastIndex = opened.pageSizes.lastIndex
        val restored = viewport?.let { it.copy(pageIndex = it.pageIndex.coerceIn(0, lastIndex)) }
        val start = restored?.pageIndex ?: document.lastPageIndex.coerceIn(0, lastIndex)
        viewport = restored ?: ViewportSnapshot(pageIndex = start)
        currentPage = start
        repository.recordReading(documentId, start)
        _state.value = ViewerUiState.Ready(document, SessionPageLoader(opened), start, annotations)
    }

    /** Where to put the reader when the screen is (re)created, e.g. after rotation. */
    fun initialViewport(): ViewportSnapshot = viewport ?: ViewportSnapshot(pageIndex = currentPage ?: 0)

    /** Called as the viewport moves; cheap, so it can run every frame of a scroll. */
    fun onViewportChanged(snapshot: ViewportSnapshot) {
        viewport = snapshot
        savedState[KEY_VIEWPORT] = snapshot.toFloatArray()
    }

    /** Called as the student scrolls; saved after a short pause so scrolling stays cheap. */
    fun onPageChanged(pageIndex: Int) {
        if (pageIndex == currentPage) return
        currentPage = pageIndex
        unsavedPage = pageIndex
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MILLIS)
            repository.recordReading(documentId, pageIndex)
            if (unsavedPage == pageIndex) unsavedPage = null
        }
    }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so a pending save moves to the app scope.
        unsavedPage?.let { page -> applicationScope.launch { repository.recordReading(documentId, page) } }
        (state.value as? ViewerUiState.Ready)?.loader?.let { (it as? SessionPageLoader)?.clear() }
        session?.close()
        session = null
    }

    private companion object {
        const val SAVE_DELAY_MILLIS = 700L
        const val KEY_VIEWPORT = "viewer.viewport"

        fun ViewportSnapshot.toFloatArray() = floatArrayOf(pageIndex.toFloat(), pageOffsetFraction, zoom, horizontalFraction)

        fun FloatArray.toViewport(): ViewportSnapshot? =
            if (size == 4) ViewportSnapshot(this[0].toInt(), this[1], this[2], this[3]) else null
    }
}
