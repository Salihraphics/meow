package com.salihraphics.meow.feature.answerkey

import android.content.Intent
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salihraphics.meow.analysis.AnswerKeyAnalysisScheduler.KeyRequest
import com.salihraphics.meow.domain.model.AnalysisInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.MeowButtonStyle
import com.salihraphics.meow.core.designsystem.component.MeowPrimaryButton
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.domain.model.AnswerGrader
import com.salihraphics.meow.domain.model.AnswerOrigin
import com.salihraphics.meow.domain.model.AnswerType
import com.salihraphics.meow.domain.model.KeyAnswer
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.domain.model.KeyTest
import com.salihraphics.meow.domain.model.KeyTestKind

data class AnswerKeyActions(
    val onBack: () -> Unit = {},
    val onCorrect: (KeyAnswer, AnswerType, String, Double?) -> Unit = { _, _, _, _ -> },
    val onSetAnswer: (testId: String, question: Int, AnswerType, String, Double?) -> Unit = { _, _, _, _, _ -> },
    val onDeleteAnswer: (KeyAnswer) -> Unit = {},
    val onAddTest: (label: String, number: Int?) -> Unit = { _, _ -> },
    val onUpdateTest: (KeyTest, label: String, pages: IntRange?) -> Unit = { _, _, _ -> },
    val onDeleteTest: (KeyTest) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onSearchAgain: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    /** The key is on these pages (1-based, inclusive). */
    val onFindKeyPages: (first: Int, last: Int) -> Unit = { _, _ -> },
    val onAddKeyPdf: (Uri) -> Unit = {},
    val onClearRequest: () -> Unit = {},
)

@Composable
fun AnswerKeyRoute(
    viewModel: AnswerKeyViewModel,
    onBack: () -> Unit,
    /** Renders a page of the book (or of a key PDF, by its id) to check answers against. */
    pagePreview: (suspend (sourceId: String, pageIndex: Int) -> ImageBitmap?)? = null,
    loadReport: (suspend () -> String?)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AnswerKeyContent(
        state,
        AnswerKeyActions(
            onBack = onBack,
            onCorrect = { a, t, v, tol -> viewModel.correct(a.id, t, v, tol) },
            onSetAnswer = viewModel::setAnswer,
            onDeleteAnswer = { viewModel.deleteAnswer(it.id) },
            onAddTest = viewModel::addTest,
            onUpdateTest = { t, label, pages -> viewModel.updateTest(t.id, label, pages) },
            onDeleteTest = { viewModel.deleteTest(it.id) },
            onConfirm = viewModel::confirm,
            onSearchAgain = viewModel::searchAgain,
            onMessageShown = viewModel::messageShown,
            onFindKeyPages = viewModel::readKeyPages,
            onAddKeyPdf = viewModel::readKeyPdf,
            onClearRequest = viewModel::clearRequest,
        ),
        pagePreview = pagePreview,
        loadReport = loadReport,
    )
}

/** Which of the four states an answer is in, for colour and screen readers. */
private enum class AnswerState { DETECTED, UNCERTAIN, MANUAL, CONFIRMED }

private fun KeyAnswer.state(test: KeyTest) = when {
    needsReview -> AnswerState.UNCERTAIN
    test.isConfirmed -> AnswerState.CONFIRMED
    origin == AnswerOrigin.MANUAL -> AnswerState.MANUAL
    else -> AnswerState.DETECTED
}

@Composable
fun AnswerKeyContent(
    state: AnswerKeyUiState,
    actions: AnswerKeyActions,
    pagePreview: (suspend (sourceId: String, pageIndex: Int) -> ImageBitmap?)? = null,
    /** The latest search report, to read and share; null hides the action. */
    loadReport: (suspend () -> String?)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = when (val m = state.message) {
        KeyMessage.Confirmed -> stringResource(R.string.key_msg_confirmed)
        is KeyMessage.Conflicts -> stringResource(R.string.key_msg_conflicts, m.count)
        KeyMessage.NothingToConfirm -> stringResource(R.string.key_msg_nothing)
        null -> null
    }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    var editing by remember { mutableStateOf<kotlin.Pair<KeyTest, KeyAnswer?>?>(null) }
    var editingTest by remember { mutableStateOf<KeyTest?>(null) }
    var addingTest by rememberSaveable { mutableStateOf(false) }
    var askingKeyPages by rememberSaveable { mutableStateOf(false) }
    var previewing by remember { mutableStateOf<KeyTest?>(null) }
    var quickEntry by remember { mutableStateOf<KeyTest?>(null) }
    var showingReport by rememberSaveable { mutableStateOf(false) }
    var onlyToCheck by rememberSaveable { mutableStateOf(false) }
    val pickKeyPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(actions.onAddKeyPdf) }
    val findActions = FindKeyActions(
        onPages = { askingKeyPages = true },
        onPdf = { pickKeyPdf.launch(arrayOf("application/pdf")) },
        onManual = { addingTest = true },
        onReport = loadReport?.let { { showingReport = true } },
    )
    val toCheck = state.tests.orEmpty().filter { it.needsAttention() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Column {
                    Text(stringResource(R.string.answer_key_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(state.documentName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        bottomBar = {
            if (state.canConfirm) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MeowPrimaryButton(
                        text = stringResource(R.string.key_confirm),
                        onClick = actions.onConfirm,
                        style = MeowButtonStyle.Brand,
                        showArrow = false,
                        modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { StatusCard(state, findActions) }
                state.request?.let { request -> item { RequestCard(request, actions.onClearRequest) } }
                if (!state.tests.isNullOrEmpty()) {
                    item { Legend() }
                    if (toCheck.isNotEmpty()) {
                        item {
                            FilterChip(
                                selected = onlyToCheck,
                                onClick = { onlyToCheck = !onlyToCheck },
                                label = { Text(stringResource(R.string.key_filter_to_check, toCheck.size)) },
                            )
                        }
                    }
                }
                items(if (onlyToCheck) toCheck else state.tests.orEmpty(), key = { it.id }) { test ->
                    TestCard(
                        test = test,
                        source = sourceLabel(test, state),
                        onAnswer = { answer -> editing = test to answer },
                        onAddAnswer = { editing = test to null },
                        onEditTest = { editingTest = test },
                        onViewSource = if (pagePreview != null && test.sourcePages.isNotEmpty()) ({ previewing = test }) else null,
                        onQuickEntry = { quickEntry = test },
                    )
                }
                item {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { addingTest = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.key_add_test))
                            }
                            if (state.status !is KeyStatus.Searching) {
                                TextButton(onClick = actions.onSearchAgain) { Text(stringResource(R.string.key_search_again)) }
                            }
                        }
                        if (!state.tests.isNullOrEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = findActions.onPages) { Text(stringResource(R.string.key_find_pages_more)) }
                                TextButton(onClick = findActions.onPdf) { Text(stringResource(R.string.key_add_pdf)) }
                            }
                        }
                        findActions.onReport?.let { TextButton(onClick = it) { Text(stringResource(R.string.key_report)) } }
                        if (state.tests.orEmpty().any { it.isConfirmed }) {
                            Text(
                                stringResource(R.string.key_search_again_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { (test, answer) ->
        AnswerDialog(
            answer = answer,
            existingQuestions = test.answers.map { it.question }.toSet(),
            onDismiss = { editing = null },
            onSave = { question, type, value, tolerance ->
                if (answer != null) actions.onCorrect(answer, type, value, tolerance) else actions.onSetAnswer(test.id, question, type, value, tolerance)
                editing = null
            },
            onDelete = answer?.let { { actions.onDeleteAnswer(it); editing = null } },
        )
    }
    editingTest?.let { test ->
        TestDialog(
            initialLabel = test.label,
            initialPages = test.questionPages,
            pageCount = state.pageCount,
            onDismiss = { editingTest = null },
            onSave = { label, pages -> actions.onUpdateTest(test, label, pages); editingTest = null },
            onDelete = { actions.onDeleteTest(test); editingTest = null },
        )
    }
    if (askingKeyPages) {
        KeyPagesDialog(
            pageCount = state.pageCount,
            onDismiss = { askingKeyPages = false },
            onRead = { first, last -> actions.onFindKeyPages(first, last); askingKeyPages = false },
        )
    }
    quickEntry?.let { entering ->
        // Follow the live test, so answers saved here show up as the dialog moves on.
        val test = state.tests.orEmpty().firstOrNull { it.id == entering.id } ?: entering
        QuickEntryDialog(test, onAnswer = { q, letter -> actions.onSetAnswer(test.id, q, AnswerType.CHOICE, letter, null) }, onDismiss = { quickEntry = null })
    }
    if (showingReport && loadReport != null) ReportDialog(loadReport, onDismiss = { showingReport = false })
    previewing?.let { test ->
        if (pagePreview != null) SourcePreviewDialog(test, sourceLabel(test, state), pagePreview, onDismiss = { previewing = null })
    }
    if (addingTest) {
        TestDialog(
            initialLabel = "",
            initialPages = null,
            pageCount = state.pageCount,
            onDismiss = { addingTest = false },
            onSave = { label, _ -> actions.onAddTest(label, Regex("\\d+").find(label)?.value?.toIntOrNull()); addingTest = false },
            onDelete = null,
        )
    }
}

/** The ways to give MEOW the key when it didn't find it: point at pages, add a key PDF, type it. */
private class FindKeyActions(val onPages: () -> Unit, val onPdf: () -> Unit, val onManual: () -> Unit, val onReport: (() -> Unit)?)

/** Missing, uncertain or conflicting answers: the tests the "to check" filter keeps. */
private fun KeyTest.needsAttention() = !isConfirmed && (missingQuestions(this).isNotEmpty() || answers.any { it.needsReview })

@Composable
private fun StatusCard(state: AnswerKeyUiState, find: FindKeyActions) {
    val status = state.status
    val tests = state.tests.orEmpty()
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val mood = when {
                    status is KeyStatus.Searching -> MascotMood.Curious
                    tests.isNotEmpty() && tests.all { it.isConfirmed } -> MascotMood.Happy
                    tests.isNotEmpty() -> MascotMood.Idle
                    else -> MascotMood.Encouraging
                }
                MeowCat(mood, Modifier.size(72.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val body = when {
                        status is KeyStatus.Searching -> R.string.key_searching_body
                        tests.isNotEmpty() && tests.all { it.isConfirmed } -> R.string.key_confirmed_body
                        tests.isNotEmpty() -> R.string.key_found_body
                        status == KeyStatus.Failed -> R.string.key_failed_body
                        else -> R.string.key_not_found_body
                    }
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                    if (status is KeyStatus.Searching) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    if ((state.analysis?.ocrReads ?: 0) > 0 && tests.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.key_scanned_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val searched = status == KeyStatus.NotFound || status == KeyStatus.Failed
            if (searched && tests.isEmpty()) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    state.analysis?.let { Diagnostics(it) }
                    Spacer(Modifier.height(10.dp))
                    FindKeyButtons(find)
                }
            }
        }
    }
}

/** Why nothing was found, in plain words, from what the search saw. */
@Composable
private fun Diagnostics(analysis: AnalysisInfo) {
    val clues = analysis.clues
    val lines = buildList {
        add(stringResource(R.string.key_diag_text, analysis.pageCount, analysis.textPages))
        if (clues.unreadablePages.isNotEmpty()) add(stringResource(R.string.key_diag_unreadable, clues.unreadablePages.size))
        if (clues.scannedPages > 0) add(stringResource(R.string.key_diag_scanned, clues.scannedPages, clues.ocrPages.size))
        add(
            if (clues.headingPages.isNotEmpty()) stringResource(R.string.key_diag_headings, pageList(clues.headingPages))
            else stringResource(R.string.key_diag_no_heading),
        )
        if (clues.answerLines == 0) add(stringResource(R.string.key_diag_no_lines))
        add(stringResource(R.string.key_diag_hint))
    }
    Text(stringResource(R.string.key_diag_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun pageList(pages: List<Int>) = pages.take(8).joinToString(", ") { "s. ${it + 1}" } + if (pages.size > 8) "…" else ""

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindKeyButtons(find: FindKeyActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = find.onPages) { Text(stringResource(R.string.key_find_pages)) }
        OutlinedButton(onClick = find.onPdf) { Text(stringResource(R.string.key_add_pdf)) }
        TextButton(onClick = find.onManual) { Text(stringResource(R.string.key_manual)) }
        find.onReport?.let { TextButton(onClick = it) { Text(stringResource(R.string.key_report)) } }
    }
}

@Composable
private fun RequestCard(request: KeyRequest, onDismiss: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            when (request) {
                is KeyRequest.Running -> {
                    Text(stringResource(R.string.key_request_running), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { request.progress }, modifier = Modifier.fillMaxWidth())
                }
                is KeyRequest.Done -> Text(
                    if (request.testsFound > 0) stringResource(R.string.key_request_done, request.testsFound) else stringResource(R.string.key_request_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
                is KeyRequest.Failed -> Text(
                    stringResource(
                        when (request.reason) {
                            KeyRequest.Reason.NOT_A_PDF -> R.string.key_request_not_pdf
                            KeyRequest.Reason.NO_ACCESS -> R.string.key_request_no_access
                            KeyRequest.Reason.UNREADABLE -> R.string.key_request_unreadable
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (request !is KeyRequest.Running) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.check_ok)) }
            }
        }
    }
}

/** Where a test's answers were read from, naming a separate key PDF when that's the source. */
@Composable
private fun sourceLabel(test: KeyTest, state: AnswerKeyUiState): String {
    val pages = test.sourcePages.joinToString(", ") { "s. ${it + 1}" }
    if (test.kind != KeyTestKind.MANUAL && test.sourceDocumentId != state.documentId && state.documentId.isNotEmpty()) {
        val name = state.keySources.firstOrNull { it.id == test.sourceDocumentId }?.displayName?.ifBlank { null }
            ?: stringResource(R.string.key_source_pdf_untitled)
        return stringResource(R.string.key_source_pdf, name, pages)
    }
    return when (test.kind) {
        KeyTestKind.KEY_SECTION -> stringResource(R.string.key_source_section, pages)
        KeyTestKind.PAGE_MARGIN -> stringResource(R.string.key_source_margin, pages)
        KeyTestKind.IN_TEST -> stringResource(R.string.key_source_in_test, pages)
        KeyTestKind.MANUAL -> stringResource(R.string.key_source_manual)
    }
}

/**
 * Typing a key one tap per answer: the missing questions first, then onwards from the last one.
 * Each letter saves and moves to the next question.
 */
@Composable
private fun QuickEntryDialog(test: KeyTest, onAnswer: (Int, String) -> Unit, onDismiss: () -> Unit) {
    val missing = missingQuestions(test)
    var question by rememberSaveable(test.id) { mutableIntStateOf(missing.firstOrNull() ?: ((test.answers.lastOrNull()?.question ?: 0) + 1)) }
    fun next(after: Int): Int {
        val stillMissing = missingQuestions(test).filter { it > after }
        return stillMissing.firstOrNull() ?: (maxOf(after, test.answers.lastOrNull()?.question ?: 0) + 1)
    }
    val current = test.answers.firstOrNull { it.question == question }?.value
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_quick_entry_title, testTitle(test))) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.key_quick_entry_question, question), style = MaterialTheme.typography.headlineMedium)
                Text(
                    current?.let { stringResource(R.string.key_quick_entry_current, it) } ?: stringResource(R.string.key_quick_entry_missing, missingQuestions(test).size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ('A'..'E').forEach { letter ->
                        Surface(
                            onClick = {
                                onAnswer(question, letter.toString())
                                question = next(question)
                            },
                            shape = CircleShape,
                            color = if (current == letter.toString()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (current == letter.toString()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(52.dp).semantics { contentDescription = letter.toString() },
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(letter.toString(), style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.key_quick_entry_done)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { question = (question - 1).coerceAtLeast(1) }, enabled = question > 1) { Text(stringResource(R.string.key_quick_entry_back)) }
                TextButton(onClick = { question = next(question) }) { Text(stringResource(R.string.key_quick_entry_skip)) }
            }
        },
    )
}

/** The latest search report, to read here or send to whoever is helping. */
@Composable
private fun ReportDialog(loadReport: suspend () -> String?, onDismiss: () -> Unit) {
    var report by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        report = loadReport()
        loaded = true
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.key_report)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_report)) },
        text = {
            Column {
                Text(stringResource(R.string.key_report_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        report ?: if (loaded) stringResource(R.string.key_report_none) else "…",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                report?.let { text ->
                    TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text(stringResource(R.string.key_report_copy)) }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }) { Text(stringResource(R.string.key_report_share)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_dismiss)) }
            }
        },
    )
}

@Composable
private fun KeyPagesDialog(pageCount: Int, onDismiss: () -> Unit, onRead: (Int, Int) -> Unit) {
    val focus = LocalFocusManager.current
    val done = KeyboardActions(onDone = { focus.clearFocus() })
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    val firstPage = first.toIntOrNull()?.takeIf { it in 1..maxOf(pageCount, 1) }
    val lastPage = (last.toIntOrNull() ?: firstPage)?.takeIf { firstPage != null && it in firstPage..maxOf(pageCount, 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_pages_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.key_pages_dialog_body, pageCount), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = first, onValueChange = { first = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.key_pages_from)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = done, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = last, onValueChange = { last = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.key_pages_to)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = done, modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = firstPage != null && lastPage != null, onClick = { onRead(firstPage!!, lastPage!!) }) {
                Text(stringResource(R.string.key_pages_read))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The page a test's answers were read from, to check them without leaving this screen. */
@Composable
private fun SourcePreviewDialog(
    test: KeyTest,
    source: String,
    pagePreview: suspend (sourceId: String, pageIndex: Int) -> ImageBitmap?,
    onDismiss: () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = test.sourcePages[index.coerceIn(0, test.sourcePages.lastIndex)]
    // null while loading; then the page, or a failure.
    var loaded by remember(test.sourceDocumentId, page) { mutableStateOf<Result<ImageBitmap>?>(null) }
    LaunchedEffect(test.sourceDocumentId, page) {
        loaded = runCatching { pagePreview(test.sourceDocumentId, page) ?: error("no page") }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.padding(12.dp).widthIn(max = 720.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.key_preview_title, testTitle(test), page + 1), style = MaterialTheme.typography.titleMedium)
                Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 560.dp).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    val bitmap = loaded?.getOrNull()
                    when {
                        bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        loaded != null -> Text(stringResource(R.string.key_preview_failed))
                        else -> CircularProgressIndicator()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (test.sourcePages.size > 1) {
                        IconButton(onClick = { index-- }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.key_preview_previous))
                        }
                        IconButton(onClick = { index++ }, enabled = index < test.sourcePages.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.key_preview_next))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_dismiss)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            AnswerState.DETECTED to R.string.key_legend_detected,
            AnswerState.UNCERTAIN to R.string.key_legend_uncertain,
            AnswerState.MANUAL to R.string.key_legend_manual,
            AnswerState.CONFIRMED to R.string.key_legend_confirmed,
        ).forEach { (state, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = state.container(), border = state.border(), modifier = Modifier.size(14.dp)) {}
                Spacer(Modifier.width(6.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnswerState.container(): Color = when (this) {
    AnswerState.DETECTED -> MaterialTheme.colorScheme.surfaceContainerHighest
    AnswerState.UNCERTAIN -> MaterialTheme.colorScheme.errorContainer
    AnswerState.MANUAL -> MaterialTheme.colorScheme.secondaryContainer
    AnswerState.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun AnswerState.border(): BorderStroke? = when (this) {
    AnswerState.UNCERTAIN -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
    AnswerState.MANUAL -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    else -> null
}

/** A test's name as the student sees it: its label, or "Test 5", or "Adsız test". */
@Composable
fun testTitle(test: KeyTest) = test.label.ifBlank {
    test.number?.let { stringResource(R.string.key_test_numbered, it) } ?: stringResource(R.string.key_test_untitled)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestCard(
    test: KeyTest,
    source: String,
    onAnswer: (KeyAnswer) -> Unit,
    onAddAnswer: () -> Unit,
    onEditTest: () -> Unit,
    onViewSource: (() -> Unit)?,
    onQuickEntry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow, border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(testTitle(test), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                Surface(shape = CircleShape, color = if (test.isConfirmed) scheme.primaryContainer else scheme.surfaceContainerHighest) {
                    Text(
                        stringResource(if (test.isConfirmed) R.string.key_confirmed_badge else R.string.key_pending_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (test.isConfirmed) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                IconButton(onClick = onEditTest) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.key_edit_test))
                }
            }
            Text(source, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(
                test.questionPages?.let { stringResource(R.string.key_question_pages, it.first + 1, it.last + 1) }
                    ?: stringResource(R.string.key_question_pages_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            val missing = missingQuestions(test)
            if (missing.isNotEmpty()) {
                Text(stringResource(R.string.key_missing, missing.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
            if (test.unresolvedConflicts > 0) {
                Text(stringResource(R.string.key_conflicts, test.unresolvedConflicts), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                test.answers.forEach { answer -> AnswerChip(test, answer, onClick = { onAnswer(answer) }) }
            }
            Row(Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onAddAnswer) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.key_add_answer))
                }
                TextButton(onClick = onQuickEntry) { Text(stringResource(R.string.key_quick_entry)) }
                if (onViewSource != null) TextButton(onClick = onViewSource) { Text(stringResource(R.string.key_view_source)) }
            }
        }
    }
}

/** Gaps inside the numbered range, counted from 1 for whole-test keys. */
private fun missingQuestions(test: KeyTest): List<Int> {
    if (test.answers.isEmpty()) return emptyList()
    val start = if (test.kind == KeyTestKind.PAGE_MARGIN) test.answers.first().question else 1
    val present = test.answers.map { it.question }.toSet()
    return (start..test.answers.last().question).filter { it !in present }
}

@Composable
private fun AnswerChip(test: KeyTest, answer: KeyAnswer, onClick: () -> Unit) {
    val state = answer.state(test)
    val stateLabel = stringResource(
        when (state) {
            AnswerState.DETECTED -> R.string.key_legend_detected
            AnswerState.UNCERTAIN -> R.string.key_legend_uncertain
            AnswerState.MANUAL -> R.string.key_legend_manual
            AnswerState.CONFIRMED -> R.string.key_legend_confirmed
        },
    )
    val description = stringResource(R.string.key_cd_answer, answer.question, answer.value, stateLabel)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = state.container(),
        border = state.border(),
        modifier = Modifier.heightIn(min = 44.dp).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${answer.question}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                if (state == AnswerState.UNCERTAIN) "${answer.value}?" else answer.value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

@Composable
private fun AnswerDialog(
    answer: KeyAnswer?,
    existingQuestions: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (question: Int, AnswerType, String, Double?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val focus = LocalFocusManager.current
    val done = KeyboardActions(onDone = { focus.clearFocus() })
    var questionText by rememberSaveable { mutableStateOf(answer?.question?.toString() ?: ((existingQuestions.maxOrNull() ?: 0) + 1).toString()) }
    var type by rememberSaveable { mutableStateOf(answer?.type ?: AnswerType.CHOICE) }
    var value by rememberSaveable { mutableStateOf(answer?.value.orEmpty()) }
    var toleranceText by rememberSaveable { mutableStateOf(answer?.tolerance?.toString().orEmpty()) }
    val question = questionText.toIntOrNull()?.takeIf { it in 1..999 }
    val tolerance = AnswerGrader.parseNumber(toleranceText)
    val valid = question != null && value.isNotBlank() &&
        (type != AnswerType.NUMBER || AnswerGrader.parseNumber(value) != null) &&
        (toleranceText.isBlank() || tolerance != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (answer != null) stringResource(R.string.key_answer_dialog_title, answer.question) else stringResource(R.string.key_add_answer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (answer == null) {
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.key_question_number)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = done,
                    )
                }
                answer?.sourcePage?.let { Text(stringResource(R.string.key_answer_source, it + 1), style = MaterialTheme.typography.bodySmall) }
                if (answer?.note == "OCR_GUESS") Text(stringResource(R.string.key_answer_ocr_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (answer?.note == "CONFLICT") {
                    Text(stringResource(R.string.key_answer_conflict_note, answer.alternatives.joinToString(" / ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                SingleChoiceSegmentedButtonRow {
                    AnswerType.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t; if (t == AnswerType.CHOICE && value.length != 1) value = "" },
                            shape = SegmentedButtonDefaults.itemShape(i, AnswerType.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (t) {
                                        AnswerType.CHOICE -> R.string.key_answer_type_choice
                                        AnswerType.NUMBER -> R.string.key_answer_type_number
                                        AnswerType.TEXT -> R.string.key_answer_type_text
                                    },
                                ),
                            )
                        }
                    }
                }
                when (type) {
                    AnswerType.CHOICE -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ('A'..'E').forEach { letter ->
                            val selected = value.equals(letter.toString(), ignoreCase = true)
                            Surface(
                                // Fixing a found answer is one tap: the letter saves it.
                                onClick = {
                                    if (answer != null) onSave(answer.question, AnswerType.CHOICE, letter.toString(), null) else value = letter.toString()
                                },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(44.dp).semantics { contentDescription = letter.toString() },
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(letter.toString(), style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                    AnswerType.NUMBER, AnswerType.TEXT -> {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it.take(120) },
                            label = { Text(stringResource(R.string.key_answer_value)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (type == AnswerType.NUMBER) KeyboardType.Decimal else KeyboardType.Text,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = done,
                        )
                        if (type == AnswerType.NUMBER) {
                            OutlinedTextField(
                                value = toleranceText,
                                onValueChange = { toleranceText = it.take(12) },
                                label = { Text(stringResource(R.string.key_tolerance)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = done,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(question!!, type, value, if (type == AnswerType.NUMBER) tolerance else null) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.key_delete_answer), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun TestDialog(
    initialLabel: String,
    initialPages: IntRange?,
    pageCount: Int,
    onDismiss: () -> Unit,
    onSave: (String, IntRange?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val focus = LocalFocusManager.current
    val done = KeyboardActions(onDone = { focus.clearFocus() })
    var label by rememberSaveable { mutableStateOf(initialLabel) }
    var first by rememberSaveable { mutableStateOf(initialPages?.first?.plus(1)?.toString().orEmpty()) }
    var last by rememberSaveable { mutableStateOf(initialPages?.last?.plus(1)?.toString().orEmpty()) }
    val firstPage = first.toIntOrNull()
    val lastPage = last.toIntOrNull()
    val pagesValid = (first.isBlank() && last.isBlank()) ||
        (firstPage != null && lastPage != null && firstPage in 1..maxOf(pageCount, 1) && lastPage in firstPage..maxOf(pageCount, 1))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (onDelete != null) R.string.key_edit_test else R.string.key_add_test)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(60) }, label = { Text(stringResource(R.string.key_test_name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = done,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = first, onValueChange = { first = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.key_first_page)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = done, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = last, onValueChange = { last = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.key_last_page)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = done, modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pagesValid, onClick = {
                onSave(label, if (firstPage != null && lastPage != null) (firstPage - 1)..(lastPage - 1) else null)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.key_delete_test), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
