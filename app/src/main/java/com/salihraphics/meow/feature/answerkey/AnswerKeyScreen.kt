package com.salihraphics.meow.feature.answerkey

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
)

@Composable
fun AnswerKeyRoute(viewModel: AnswerKeyViewModel, onBack: () -> Unit) {
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
        ),
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
fun AnswerKeyContent(state: AnswerKeyUiState, actions: AnswerKeyActions) {
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
                item { StatusCard(state) }
                if (!state.tests.isNullOrEmpty()) item { Legend() }
                items(state.tests.orEmpty(), key = { it.id }) { test ->
                    TestCard(
                        test = test,
                        onAnswer = { answer -> editing = test to answer },
                        onAddAnswer = { editing = test to null },
                        onEditTest = { editingTest = test },
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

@Composable
private fun StatusCard(state: AnswerKeyUiState) {
    val status = state.status
    val tests = state.tests.orEmpty()
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun testTitle(test: KeyTest) = test.label.ifBlank {
    test.number?.let { stringResource(R.string.key_test_numbered, it) } ?: stringResource(R.string.key_test_untitled)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestCard(test: KeyTest, onAnswer: (KeyAnswer) -> Unit, onAddAnswer: () -> Unit, onEditTest: () -> Unit) {
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
            val pages = test.sourcePages.joinToString(", ") { "s. ${it + 1}" }
            Text(
                when (test.kind) {
                    KeyTestKind.KEY_SECTION -> stringResource(R.string.key_source_section, pages)
                    KeyTestKind.PAGE_MARGIN -> stringResource(R.string.key_source_margin, pages)
                    KeyTestKind.MANUAL -> stringResource(R.string.key_source_manual)
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
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
            TextButton(onClick = onAddAnswer, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.key_add_answer))
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
                                onClick = { value = letter.toString() },
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
