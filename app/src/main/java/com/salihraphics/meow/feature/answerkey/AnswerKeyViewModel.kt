package com.salihraphics.meow.feature.answerkey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.answerkey.AnswerKeyRepository
import com.salihraphics.meow.data.answerkey.ConfirmResult
import com.salihraphics.meow.domain.model.AnalysisInfo
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.domain.model.KeyTest
import kotlinx.coroutines.flow.Flow
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
) : ViewModel() {

    private val message = MutableStateFlow<KeyMessage?>(null)

    val state: StateFlow<AnswerKeyUiState> = combine(
        documentName,
        keys.observeStatuses(progress),
        keys.observeAnalyses(),
        keys.observeTests(documentId),
        message,
    ) { (name, pages), statuses, analyses, tests, msg ->
        AnswerKeyUiState(name, pages, statuses[documentId], analyses[documentId], tests, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnswerKeyUiState())

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

    fun messageShown() = message.update { null }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
