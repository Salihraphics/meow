package com.salihraphics.meow.feature.answerkey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.answerkey.AnswerKeyRepository
import com.salihraphics.meow.data.answerkey.ConfirmResult
import com.salihraphics.meow.domain.model.AnalysisInfo
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.domain.model.KeyTest
import android.net.Uri
import com.salihraphics.meow.analysis.AnswerKeyAnalysisScheduler.KeyRequest
import com.salihraphics.meow.domain.model.KeySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface KeyMessage {
    data object Confirmed : KeyMessage
    data class Conflicts(val count: Int) : KeyMessage
    data object NothingToConfirm : KeyMessage
}

data class AnswerKeyUiState(
    val documentName: String = "",
    val pageCount: Int = 0,
    val status: KeyStatus? = null,
    val analysis: AnalysisInfo? = null,
    val tests: List<KeyTest>? = null,
    val message: KeyMessage? = null,
    val documentId: String = "",
    /** Separate answer-key PDFs added for this book. */
    val keySources: List<KeySource> = emptyList(),
    /** A search the student started here ("key is on pages…", "key PDF"), and how it went. */
    val request: KeyRequest? = null,
) {
    val canConfirm get() = tests.orEmpty().any { it.answers.isNotEmpty() } && tests.orEmpty().any { !it.isConfirmed }
}

class AnswerKeyViewModel(
    private val documentId: String,
    documentName: Flow<kotlin.Pair<String, Int>>,
    private val keys: AnswerKeyRepository,
    progress: Flow<Map<String, Float>>,
    /** Asks the scheduler to search this document again. */
    private val searchAgainAction: () -> Unit,
    private val requests: Flow<Map<String, KeyRequest>> = flowOf(emptyMap()),
    /** Reads the given pages (0-based) as answer-key pages. */
    private val readKeyPagesAction: (List<Int>) -> Unit = {},
    /** Reads a separate answer-key PDF for this book. */
    private val readKeyPdfAction: (Uri) -> Unit = {},
    private val clearRequestAction: () -> Unit = {},
) : ViewModel() {

    private val message = MutableStateFlow<KeyMessage?>(null)

    val state: StateFlow<AnswerKeyUiState> = combine(
        combine(documentName, keys.observeStatuses(progress), keys.observeAnalyses(), keys.observeTests(documentId), message) {
                (name, pages), statuses, analyses, tests, msg ->
            AnswerKeyUiState(name, pages, statuses[documentId], analyses[documentId], tests, msg, documentId)
        },
        keys.observeKeySources(documentId),
        requests,
    ) { base, sources, running -> base.copy(keySources = sources, request = running[documentId]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnswerKeyUiState(documentId = documentId))

    fun correct(answerId: String, type: AnswerType, value: String, tolerance: Double?) =
        launch { keys.correctAnswer(answerId, type, value, tolerance) }

    fun setAnswer(testId: String, question: Int, type: AnswerType, value: String, tolerance: Double?) =
        launch { keys.setAnswer(testId, question, type, value, tolerance) }

    fun deleteAnswer(answerId: String) = launch { keys.deleteAnswer(answerId) }

    fun addTest(label: String, number: Int?) = launch { keys.addManualTest(documentId, label, number) }

    fun updateTest(testId: String, label: String, pages: IntRange?) = launch { keys.updateTest(testId, label, pages) }

    fun deleteTest(testId: String) = launch { keys.deleteTest(testId) }

    fun confirm() = launch {
        message.value = when (val result = keys.confirm(documentId)) {
            ConfirmResult.Confirmed -> KeyMessage.Confirmed
            ConfirmResult.NothingToConfirm -> KeyMessage.NothingToConfirm
            is ConfirmResult.UnresolvedConflicts -> KeyMessage.Conflicts(result.count)
        }
    }

    fun searchAgain() = searchAgainAction()

    /** "The key is on pages [first]–[last]" (1-based, as printed). */
    fun readKeyPages(first: Int, last: Int) = readKeyPagesAction(((minOf(first, last) - 1)..(maxOf(first, last) - 1)).toList())

    fun readKeyPdf(uri: Uri) = readKeyPdfAction(uri)

    fun clearRequest() = clearRequestAction()

    fun messageShown() = message.update { null }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
