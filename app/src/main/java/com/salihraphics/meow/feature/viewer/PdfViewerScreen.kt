package com.salihraphics.meow.feature.viewer

import com.salihraphics.meow.core.window.StudyWindowEffect
import com.salihraphics.meow.core.window.ImmersiveDialogWindow
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.window.Dialog
import com.salihraphics.meow.domain.model.StickyNote
import com.salihraphics.meow.feature.annotation.AnnotationActions
import com.salihraphics.meow.feature.annotation.AnnotationToolRail
import com.salihraphics.meow.feature.annotation.PageNotesLayer
import com.salihraphics.meow.feature.study.StudyTimerController
import androidx.compose.runtime.remember
import com.salihraphics.meow.feature.check.AnswerCheckCard
import com.salihraphics.meow.feature.check.AnswerCheckUi
import com.salihraphics.meow.domain.model.AnswerAttempt
import com.salihraphics.meow.feature.check.AnswerSheetDialog
import com.salihraphics.meow.feature.check.SessionBar
import com.salihraphics.meow.feature.check.SessionCheck
import com.salihraphics.meow.feature.check.SessionCheckDialog
import androidx.compose.material.icons.filled.CheckCircle
import com.salihraphics.meow.feature.annotation.PageAnnotationLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWidths
import com.salihraphics.meow.core.designsystem.adaptive.WidthClass
import com.salihraphics.meow.core.designsystem.component.MeowButtonStyle
import com.salihraphics.meow.core.designsystem.component.MeowPrimaryButton
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MeowCat
import kotlinx.coroutines.launch

@Composable
fun PdfViewerRoute(
    viewModel: PdfViewerViewModel,
    onBack: () -> Unit,
    onOpenAnswerKey: (() -> Unit)? = null,
    studyTimer: StudyTimerController? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val answerCheck by viewModel.answerCheck.collectAsStateWithLifecycle()
    val solving by viewModel.solving.collectAsStateWithLifecycle()
    val sessionCheck by viewModel.sessionCheck.collectAsStateWithLifecycle()
    val rotation by viewModel.rotation.collectAsStateWithLifecycle()
    PdfViewerContent(
        state = state,
        initialViewport = viewModel::initialViewport,
        onViewportChanged = viewModel::onViewportChanged,
        onPageChanged = viewModel::onPageChanged,
        onBack = onBack,
        answers = ViewerAnswers(answerCheck, solving?.latest.orEmpty(), sessionCheck),
        answerActions = ViewerAnswerActions(
            onChooseOption = viewModel::chooseOption,
            onChooseQuestion = viewModel::chooseQuestion,
            onDismissCheck = viewModel::dismissAnswerCheck,
            onCheckTest = viewModel::checkTest,
            onDismissSessionCheck = viewModel::dismissSessionCheck,
            onAssignTest = viewModel::assignPagesToTest,
            onUndoAnswer = viewModel::undoAnswer,
            onClearQuestion = viewModel::clearQuestion,
            onOpenAnswerKey = onOpenAnswerKey,
        ),
        workspace = ViewerWorkspace(rotation, viewModel::setRotation, studyTimer),
    )
}

/** Answer marking state shown in the study panel. */
data class ViewerAnswers(
    val check: AnswerCheckUi? = null,
    /** Answers collected and not yet checked, the latest per question. */
    val recorded: List<AnswerAttempt> = emptyList(),
    val sessionCheck: SessionCheck? = null,
) {
    val answered: Int get() = recorded.size
}

class ViewerAnswerActions(
    val onChooseOption: (String) -> Unit = {},
    val onChooseQuestion: (Int) -> Unit = {},
    val onDismissCheck: () -> Unit = {},
    val onCheckTest: () -> Unit = {},
    val onDismissSessionCheck: () -> Unit = {},
    val onAssignTest: (String, List<Int>) -> Unit = { _, _ -> },
    val onUndoAnswer: (String) -> Unit = {},
    val onClearQuestion: (AnswerAttempt) -> Unit = {},
    /** Opens this document's answer key; null hides the action. */
    val onOpenAnswerKey: (() -> Unit)? = null,
)

/** How the pages are turned, and the study timer; a null timer hides it. */
class ViewerWorkspace(
    val rotation: Int = 0,
    val onRotationChange: (Int) -> Unit = {},
    val timer: StudyTimerController? = null,
)

@Composable
fun PdfViewerContent(
    state: ViewerUiState,
    initialViewport: () -> ViewportSnapshot,
    onViewportChanged: (ViewportSnapshot) -> Unit,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
    answers: ViewerAnswers = ViewerAnswers(),
    answerActions: ViewerAnswerActions = ViewerAnswerActions(),
    workspace: ViewerWorkspace = ViewerWorkspace(),
) = StudyWindowEffect {
    // Immersive: the bars are hidden, so safeDrawing only keeps a display cutout (or the
    // keyboard) clear; no strip is left where the bars were.
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (state) {
            ViewerUiState.Loading -> Column {
                ViewerTopBar(onBack = onBack) {}
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.viewer_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is ViewerUiState.Error -> Column {
                ViewerTopBar(onBack = onBack) {}
                ViewerErrorMessage(state.error, onBack)
            }
            is ViewerUiState.Ready -> ReadyViewer(state, initialViewport, onViewportChanged, onPageChanged, onBack, answers, answerActions, workspace)
        }
    }
}

/**
 * The pages on the left, every study tool on the right. Nothing is ever drawn over the pages:
 * on tablets and in landscape the right side is the study panel; on phones it is a slim tool
 * rail, and the full panel opens as a drawer.
 */
@Composable
private fun ReadyViewer(
    state: ViewerUiState.Ready,
    initialViewport: () -> ViewportSnapshot,
    onViewportChanged: (ViewportSnapshot) -> Unit,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
    answers: ViewerAnswers,
    actions: ViewerAnswerActions,
    workspace: ViewerWorkspace,
) {
    // Phone and side-panel layouts share this one state, so switching between them on
    // rotation keeps the position too.
    val viewport = rememberPdfViewportState(state.document.id, initialViewport, onViewportChanged)
    LaunchedEffect(viewport) { snapshotFlow { viewport.currentPage }.collect(onPageChanged) }
    val scope = rememberCoroutineScope()
    var askingPage by rememberSaveable { mutableStateOf(false) }
    var showingSheet by rememberSaveable { mutableStateOf(false) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val window = LocalMeowWindow.current
    val sidePanel = window.useTwoPanes || window.width == WidthClass.Expanded
    val pageCount = state.loader.pageCount
    val session = state.annotations
    val tools by session.tools.collectAsStateWithLifecycle()
    val editingNote by session.editingNote.collectAsStateWithLifecycle()
    val drawing = tools.tool != null
    val rotation = workspace.rotation

    // An undo on a page that's off screen scrolls there, so the change is never invisible.
    val toolActions = AnnotationActions.of(
        session,
        onUndo = { session.undo()?.let { page -> scope.launch { reveal(viewport, page) } } },
        onRedo = { session.redo()?.let { page -> scope.launch { reveal(viewport, page) } } },
    )
    val addNote = {
        drawerOpen = false
        session.addNote(viewport.currentPage)
        Unit
    }
    val panel = StudyPanelState(
        document = state.document,
        viewport = viewport,
        pageCount = pageCount,
        tools = tools,
        toolActions = toolActions,
        session = session,
        workspace = workspace,
        onGoToPage = { askingPage = true },
        onAddNote = addNote,
    )

    Column(Modifier.fillMaxSize()) {
        ViewerTopBar(onBack = onBack, title = {
            Column {
                Text(
                    state.document.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (!sidePanel) {
                    Text(
                        stringResource(R.string.viewer_page_indicator, viewport.currentPage + 1, pageCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { askingPage = true },
                    )
                }
            }
        }) {
            actions.onOpenAnswerKey?.let { openKey ->
                IconButton(onClick = openKey) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.answer_key_title))
                }
            }
        }
        if (!sidePanel) PhoneAnswerStrip(answers, actions, onShowAnswers = { showingSheet = true })
        Row(Modifier.fillMaxSize()) {
            PdfPagesView(
                loader = state.loader,
                state = viewport,
                modifier = Modifier.weight(1f).fillMaxHeight().pdfBackdrop(),
                drawingActive = drawing,
                rotation = rotation,
                onTap = { session.selectNote(null) },
                pageOverlay = { page ->
                    PageAnnotationLayer(page, session, tools)
                    PageNotesLayer(page, session, rotation, tools)
                },
            )
            if (sidePanel) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StudyPanel(
                    panel,
                    modifier = Modifier.width(if (window.shortHeight) 300.dp else 340.dp).fillMaxHeight(),
                    header = { PanelAnswers(answers, actions, onShowAnswers = { showingSheet = true }) },
                )
            } else {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AnnotationToolRail(
                    state = tools,
                    actions = toolActions,
                    onAddNote = addNote,
                    onOpenPanel = { drawerOpen = true },
                    modifier = Modifier.width(56.dp).fillMaxHeight(),
                )
            }
        }
    }

    if (!sidePanel) {
        PanelDrawer(open = drawerOpen, onClose = { drawerOpen = false }) {
            StudyPanel(panel, Modifier.fillMaxSize(), header = {})
        }
        // Phones: a question about a mark, or a result, asks in a dialog rather than over the page.
        val check = answers.check
        if (check != null && check !is AnswerCheckUi.Recorded) {
            Dialog(onDismissRequest = actions.onDismissCheck) {
                ImmersiveDialogWindow()
                AnswerCheckCard(
                    check = check,
                    onChoose = actions.onChooseOption,
                    onChooseQuestion = actions.onChooseQuestion,
                    onDismiss = actions.onDismissCheck,
                    onOpenKey = { actions.onDismissCheck(); actions.onOpenAnswerKey?.invoke() },
                    onUndo = actions.onUndoAnswer,
                )
            }
        }
    }

    editingNote?.let { note ->
        NoteEditDialog(
            note = note,
            onSave = { text -> session.updateNote(note.copy(text = text)); session.editNote(null) },
            onDelete = { session.deleteNote(note); session.editNote(null) },
            onDismiss = { session.editNote(null) },
        )
    }

    if (showingSheet && answers.recorded.isNotEmpty()) {
        AnswerSheetDialog(
            answers = answers.recorded,
            onClear = actions.onClearQuestion,
            onGoToPage = { page ->
                showingSheet = false
                scope.launch { viewport.goToPage(page) }
            },
            onCheck = { showingSheet = false; actions.onCheckTest() },
            onDismiss = { showingSheet = false },
        )
    }
    answers.sessionCheck?.let { check ->
        SessionCheckDialog(
            check = check,
            onDismiss = actions.onDismissSessionCheck,
            onOpenKey = { actions.onDismissSessionCheck(); actions.onOpenAnswerKey?.invoke() },
            onGoToPage = { page ->
                actions.onDismissSessionCheck()
                scope.launch { viewport.goToPage(page) }
            },
            onAssignTest = actions.onAssignTest,
        )
    }

    if (askingPage) {
        GoToPageDialog(
            pageCount = pageCount,
            onDismiss = { askingPage = false },
            onGo = { page ->
                askingPage = false
                scope.launch { viewport.goToPage(page - 1) }
            },
        )
    }
}

/** Tablets: the latest answer reading and the "Testi kontrol et" bar, at the top of the panel. */
@Composable
private fun PanelAnswers(answers: ViewerAnswers, actions: ViewerAnswerActions, onShowAnswers: () -> Unit) {
    val check = answers.check
    if (check == null && answers.answered == 0) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        check?.let {
            AnswerCheckCard(
                check = it,
                onChoose = actions.onChooseOption,
                onChooseQuestion = actions.onChooseQuestion,
                onDismiss = actions.onDismissCheck,
                onOpenKey = { actions.onDismissCheck(); actions.onOpenAnswerKey?.invoke() },
                modifier = Modifier.padding(bottom = 8.dp),
                onUndo = actions.onUndoAnswer,
            )
        }
        if (answers.answered > 0 && check !is AnswerCheckUi.Ask) {
            // Stacked: the panel is too narrow for the phone strip's single row.
            TextButton(onClick = onShowAnswers) {
                Text(stringResource(R.string.session_answers, answers.answered), style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = actions.onCheckTest, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.session_check), maxLines = 1)
            }
        }
    }
}

/**
 * Phones: answers in a strip docked under the top bar, beside nothing and over nothing; it
 * pushes the pages down. Shown once there are answers: the latest reading with "Geri al", and
 * "Testi kontrol et".
 */
@Composable
private fun PhoneAnswerStrip(answers: ViewerAnswers, actions: ViewerAnswerActions, onShowAnswers: () -> Unit) {
    val recorded = answers.check as? AnswerCheckUi.Recorded
    if (recorded == null && answers.answered == 0) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            recorded?.let {
                AnswerCheckCard(it, {}, {}, actions.onDismissCheck, {}, Modifier.padding(bottom = 4.dp), onUndo = actions.onUndoAnswer)
            }
            if (answers.answered > 0) SessionBar(answers.answered, actions.onCheckTest, onShowAnswers = onShowAnswers)
        }
    }
}

/** Phones: the full study panel sliding in from the right edge, over a scrim. */
@Composable
private fun PanelDrawer(open: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = open, onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            open,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().widthIn(max = 360.dp).fillMaxWidth(0.88f),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.study_panel), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.study_panel_close)) }
                    }
                    content()
                }
            }
        }
    }
}

private suspend fun reveal(viewport: PdfViewportState, page: Int) {
    if (!viewport.isPageVisible(page)) viewport.goToPage(page)
}

@Composable
private fun Modifier.pdfBackdrop() = this.then(
    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
)

@Composable
private fun ViewerTopBar(onBack: () -> Unit, title: @Composable () -> Unit = {}, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Box(Modifier.weight(1f)) { title() }
        actions()
    }
}

@Composable
private fun NoteEditDialog(note: StickyNote, onSave: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(note.id) { mutableStateOf(note.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_edit)) },
        text = {
            ImmersiveDialogWindow()
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NOTE_LENGTH) },
                label = { Text(stringResource(R.string.note_text_label)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.note_save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.note_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private const val MAX_NOTE_LENGTH = 1_000

@Composable
private fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_go_to_page)) },
        text = {
            ImmersiveDialogWindow()
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.viewer_go_to_page_label, pageCount)) },
                singleLine = true,
                isError = text.isNotEmpty() && page == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { page?.let(onGo) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { page?.let(onGo) }, enabled = page != null) { Text(stringResource(R.string.viewer_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ViewerErrorMessage(error: ViewerError, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = MeowWidths.Readable), horizontalAlignment = Alignment.CenterHorizontally) {
            MeowCat(MascotMood.Encouraging, Modifier.size(140.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(
                    when (error) {
                        ViewerError.MISSING -> R.string.viewer_error_missing
                        ViewerError.PASSWORD_PROTECTED -> R.string.viewer_error_password
                        ViewerError.DAMAGED -> R.string.viewer_error_damaged
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            MeowPrimaryButton(stringResource(R.string.action_back), onClick = onBack, style = MeowButtonStyle.Ink, showArrow = false)
        }
    }
}
