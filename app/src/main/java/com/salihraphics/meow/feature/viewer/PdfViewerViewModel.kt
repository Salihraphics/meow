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
import java.io.File
import java.io.IOException
import com.salihraphics.meow.analysis.layout.ChoiceRecognition
import com.salihraphics.meow.analysis.layout.ChoiceRecognizer
import com.salihraphics.meow.analysis.layout.PageLayoutSource
import com.salihraphics.meow.data.answerkey.AnswerKeyRepository
import com.salihraphics.meow.domain.model.SelectionOrigin
import com.salihraphics.meow.domain.model.Stroke
import com.salihraphics.meow.feature.check.AnswerChecker
import com.salihraphics.meow.feature.check.AnswerCheckUi
import com.salihraphics.meow.feature.check.CheckOutcome
import com.salihraphics.meow.feature.check.SessionCheck
import com.salihraphics.meow.feature.check.SessionGrader
import com.salihraphics.meow.domain.model.AttemptResult
import com.salihraphics.meow.domain.model.AnswerAttempt
import com.salihraphics.meow.domain.model.SolvingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /** Confirmed answer keys and attempts; null disables answer checking. */
    private val answerKeys: AnswerKeyRepository? = null,
    /** Reads question positions for answer checking, from the PDF text or OCR. */
    private val layoutSourceFactory: ((File, PdfRenderSession) -> PageLayoutSource)? = null,
    /** The "show right/wrong immediately" setting; off means answers are checked per test. */
    immediateFeedbackSetting: Flow<Boolean> = flowOf(false),
) : ViewModel() {

    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var session: PdfRenderSession? = null

    private val _rotation = MutableStateFlow(0)

    /** How the student turned the pages (degrees clockwise); remembered per PDF. */
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    /** Strokes, tool and undo history; here so rotation keeps them. */
    // Answers are kept apart from drawings: erasing or undoing a circle leaves its answer saved.
    // An answer changes by circling another option, "Geri al" on its note, or the answer sheet.
    private val annotations = AnnotationSession(documentId, annotationRepository, viewModelScope, onStrokeAdded = ::onStrokeAdded)

    private val immediateFeedback: StateFlow<Boolean> = immediateFeedbackSetting.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Answers collected and not yet checked, for the "Testi kontrol et" bar. */
    val solving: StateFlow<SolvingSession?> =
        (answerKeys?.observeOpenSession(documentId) ?: flowOf(null)).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val answerLock = Mutex()

    private val _sessionCheck = MutableStateFlow<SessionCheck?>(null)

    /** The outcome of the last "Testi kontrol et", until dismissed. */
    val sessionCheck: StateFlow<SessionCheck?> = _sessionCheck.asStateFlow()

    private var layoutSource: PageLayoutSource? = null

    private val _answerCheck = MutableStateFlow<AnswerCheckUi?>(null)

    /** The latest answer check to show, if any. */
    val answerCheck: StateFlow<AnswerCheckUi?> = _answerCheck.asStateFlow()
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
        layoutSource = layoutSourceFactory?.invoke(file, opened)
        val lastIndex = opened.pageSizes.lastIndex
        val restored = viewport?.let { it.copy(pageIndex = it.pageIndex.coerceIn(0, lastIndex)) }
        val start = restored?.pageIndex ?: document.lastPageIndex.coerceIn(0, lastIndex)
        viewport = restored ?: ViewportSnapshot(pageIndex = start)
        currentPage = start
        repository.recordReading(documentId, start)
        _rotation.value = PageRotation.normalize(document.viewRotation)
        _state.value = ViewerUiState.Ready(document, SessionPageLoader(opened), start, annotations)
    }

    /** Where to put the reader when the screen is (re)created, e.g. after rotation. */
    fun initialViewport(): ViewportSnapshot = viewport ?: ViewportSnapshot(pageIndex = currentPage ?: 0)

    /** Called as the viewport moves; cheap, so it can run every frame of a scroll. */
    fun onViewportChanged(snapshot: ViewportSnapshot) {
        viewport = snapshot
        savedState[KEY_VIEWPORT] = snapshot.toFloatArray()
    }

    /** Turns the pages; only the view changes, never the PDF or the stored annotations. */
    fun setRotation(degrees: Int) {
        val turn = PageRotation.normalize(degrees)
        if (turn == _rotation.value) return
        _rotation.value = turn
        applicationScope.launch { repository.setViewRotation(documentId, turn) }
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

    /**
     * A finished pen stroke. If it circles an option, the answer is saved in the solving session
     * (and graded on the spot only with immediate feedback on). If the option or question isn't
     * clear, the student is asked. Anything else is just a drawing.
     */
    private fun onStrokeAdded(stroke: Stroke) {
        answerKeys ?: return
        val source = layoutSource ?: return
        // One mark at a time, in the order drawn, so a quick second circle on the same question
        // is always the one that counts. (The lock is fair and taken before the first suspension.)
        viewModelScope.launch { answerLock.withLock { handleMark(source, stroke) } }
    }

    private suspend fun handleMark(source: PageLayoutSource, stroke: Stroke) {
        val layout = try {
            source.layout(stroke.pageIndex)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        val region = strokeRegion(stroke.points)
        when (val recognition = ChoiceRecognizer.recognize(stroke.points, layout)) {
            ChoiceRecognition.NotAnAnswer -> Unit
            is ChoiceRecognition.Selected ->
                answer(stroke.pageIndex, recognition.question, recognition.letter, SelectionOrigin.RECOGNIZED, stroke.id, region)
            is ChoiceRecognition.Unclear -> _answerCheck.value = AnswerCheckUi.Ask(
                question = recognition.question,
                candidates = recognition.candidates,
                options = recognition.options,
                questionChoices = questionChoices(stroke.pageIndex, recognition.nearbyQuestions),
                pageIndex = stroke.pageIndex,
                strokeId = stroke.id,
                region = region.toList(),
            )
        }
    }

    /** For an unknown question: the numbers printed on the page, then the rest of its test. */
    private suspend fun questionChoices(pageIndex: Int, nearby: List<Int>): List<Int> {
        val tests = answerKeys?.tests(documentId).orEmpty().filter { it.answers.isNotEmpty() }
        val test = tests.filter { it.questionPages?.contains(pageIndex) == true }.singleOrNull()
            ?: tests.singleOrNull()?.takeIf { it.questionPages == null }
        val fromKey = test?.answers?.map { it.question } ?: (1..MAX_OFFERED_QUESTION).toList()
        return (nearby + fromKey).distinct()
    }

    /** The student says which question an unclear mark answers. */
    fun chooseQuestion(question: Int) {
        val ask = _answerCheck.value as? AnswerCheckUi.Ask ?: return
        if (ask.candidates.size == 1) {
            viewModelScope.launch {
                answer(ask.pageIndex, question, ask.candidates.single(), SelectionOrigin.CHOSEN_BY_STUDENT, ask.strokeId, ask.region.toFloatArray())
            }
        } else {
            _answerCheck.value = ask.copy(question = question)
        }
    }

    /** The student says which option they marked. */
    fun chooseOption(letter: String) {
        val ask = _answerCheck.value as? AnswerCheckUi.Ask ?: return
        val question = ask.question ?: return
        viewModelScope.launch {
            answer(ask.pageIndex, question, letter, SelectionOrigin.CHOSEN_BY_STUDENT, ask.strokeId, ask.region.toFloatArray())
        }
    }

    fun dismissAnswerCheck() {
        _answerCheck.value = null
    }

    /** "Geri al" on a just-recorded answer: that answer is dropped (an earlier one for the question counts again). */
    fun undoAnswer(attemptId: String) {
        val keys = answerKeys ?: return
        if ((_answerCheck.value as? AnswerCheckUi.Recorded)?.attemptId == attemptId) _answerCheck.value = null
        applicationScope.launch { keys.removeAnswer(attemptId) }
    }

    /** From the answer sheet: the question is unanswered again. */
    fun clearQuestion(attempt: AnswerAttempt) {
        val keys = answerKeys ?: return
        val session = solving.value ?: return
        applicationScope.launch { keys.clearQuestion(session.id, attempt) }
    }

    private suspend fun answer(page: Int, question: Int, letter: String, origin: SelectionOrigin, strokeId: String, region: FloatArray) {
        val keys = answerKeys ?: return
        val tests = keys.tests(documentId)
        val testId = AnswerChecker.testFor(page, question, tests.filter { it.answers.isNotEmpty() })?.id
        if (!immediateFeedback.value) {
            val id = keys.recordSelection(documentId, testId, question, page, letter, origin, strokeId, region)
            _answerCheck.value = AnswerCheckUi.Recorded(question, letter, id)
            return
        }
        val outcome = AnswerChecker.check(page, question, letter, tests)
        val result = (outcome as? CheckOutcome.Graded)?.result ?: AttemptResult.PENDING
        val id = keys.recordSelection(documentId, testId, question, page, letter, origin, strokeId, region, result)
        _answerCheck.value = when (outcome) {
            is CheckOutcome.Graded -> AnswerCheckUi.Result(outcome)
            CheckOutcome.NoKey -> AnswerCheckUi.Recorded(question, letter, id)
            CheckOutcome.KeyNotConfirmed -> AnswerCheckUi.KeyNotConfirmed
            CheckOutcome.PageTestUnknown -> AnswerCheckUi.PageTestUnknown
            is CheckOutcome.NoAnswerForQuestion -> AnswerCheckUi.NoAnswer(outcome.question)
        }
    }

    /** "Testi kontrol et": grades every answer collected so far against the confirmed key. */
    fun checkTest() {
        val keys = answerKeys ?: return
        viewModelScope.launch {
            val session = keys.openSession(documentId)
            val check = SessionGrader.check(session, keys.tests(documentId))
            if (check is SessionCheck.Checked && session != null) {
                val graded = check.result.questions.mapNotNull { q ->
                    val attemptId = q.attemptId ?: return@mapNotNull null
                    AnswerKeyRepository.GradedAttempt(attemptId, q.testId, q.result ?: return@mapNotNull null)
                }
                keys.finishSession(session.id, graded, check.result.correct, check.result.incorrect, check.result.unanswered)
            }
            _sessionCheck.value = check
        }
    }

    /** Answers on [pages] belong to [testId]: widen its question pages, then check again. */
    fun assignPagesToTest(testId: String, pages: List<Int>) {
        val keys = answerKeys ?: return
        viewModelScope.launch {
            val test = keys.tests(documentId).firstOrNull { it.id == testId } ?: return@launch
            val all = pages + listOfNotNull(test.questionPages?.first, test.questionPages?.last)
            keys.updateTest(testId, test.label, all.min()..all.max())
            checkTest()
        }
    }

    fun dismissSessionCheck() {
        _sessionCheck.value = null
    }

    private fun strokeRegion(points: FloatArray): FloatArray {
        val xs = points.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = points.filterIndexed { i, _ -> i % 2 == 1 }
        return floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }

    override fun onCleared() {
        layoutSource?.close()
        layoutSource = null
        // viewModelScope is already cancelled here, so a pending save moves to the app scope.
        unsavedPage?.let { page -> applicationScope.launch { repository.recordReading(documentId, page) } }
        (state.value as? ViewerUiState.Ready)?.loader?.let { (it as? SessionPageLoader)?.clear() }
        session?.close()
        session = null
    }

    private companion object {
        const val SAVE_DELAY_MILLIS = 700L
        const val MAX_OFFERED_QUESTION = 40
        const val KEY_VIEWPORT = "viewer.viewport"

        fun ViewportSnapshot.toFloatArray() = floatArrayOf(pageIndex.toFloat(), pageOffsetFraction, zoom, horizontalFraction)

        fun FloatArray.toViewport(): ViewportSnapshot? =
            if (size == 4) ViewportSnapshot(this[0].toInt(), this[1], this[2], this[3]) else null
    }
}
