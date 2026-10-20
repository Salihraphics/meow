package com.salihraphics.meow.feature.viewer

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import com.salihraphics.meow.feature.annotation.AnnotationToolbar
import com.salihraphics.meow.feature.annotation.PageAnnotationLayer
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWidths
import com.salihraphics.meow.core.designsystem.adaptive.WidthClass
import com.salihraphics.meow.core.designsystem.component.MeowButtonStyle
import com.salihraphics.meow.core.designsystem.component.MeowPrimaryButton
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.domain.model.PdfDocument
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun PdfViewerRoute(viewModel: PdfViewerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PdfViewerContent(
        state = state,
        initialViewport = viewModel::initialViewport,
        onViewportChanged = viewModel::onViewportChanged,
        onPageChanged = viewModel::onPageChanged,
        onBack = onBack,
    )
}

@Composable
fun PdfViewerContent(
    state: ViewerUiState,
    initialViewport: () -> ViewportSnapshot,
    onViewportChanged: (ViewportSnapshot) -> Unit,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (state) {
            ViewerUiState.Loading -> {
                ViewerTopBar(title = "", onBack = onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.viewer_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is ViewerUiState.Error -> {
                ViewerTopBar(title = "", onBack = onBack)
                ViewerErrorMessage(state.error, onBack)
            }
            is ViewerUiState.Ready -> ReadyViewer(state, initialViewport, onViewportChanged, onPageChanged, onBack)
        }
    }
}

@Composable
private fun ReadyViewer(
    state: ViewerUiState.Ready,
    initialViewport: () -> ViewportSnapshot,
    onViewportChanged: (ViewportSnapshot) -> Unit,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // Phone and side-panel layouts share this one state, so switching between them on
    // rotation keeps the position too.
    val viewport = rememberPdfViewportState(state.document.id, initialViewport, onViewportChanged)
    LaunchedEffect(viewport) { snapshotFlow { viewport.currentPage }.collect(onPageChanged) }
    val scope = rememberCoroutineScope()
    var askingPage by rememberSaveable { mutableStateOf(false) }
    val window = LocalMeowWindow.current
    val sidePanel = window.useTwoPanes || window.width == WidthClass.Expanded
    val pageCount = state.loader.pageCount
    val tools by state.annotations.tools.collectAsStateWithLifecycle()
    val drawing = tools.tool != null

    val pages = @Composable { modifier: Modifier ->
        PdfPagesView(
            loader = state.loader,
            state = viewport,
            modifier = modifier.pdfBackdrop(),
            drawingActive = drawing,
            pageOverlay = { page -> PageAnnotationLayer(page, state.annotations, tools) },
        )
    }
    val toolbar = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TwoFingerHint(visible = drawing)
            AnnotationToolbar(
                state = tools,
                onTool = state.annotations::selectTool,
                onColor = state.annotations::selectColor,
                onWidth = state.annotations::selectWidth,
                // An undo on a page that's off screen scrolls there, so the change is never invisible.
                onUndo = { state.annotations.undo()?.let { page -> scope.launch { reveal(viewport, page) } } },
                onRedo = { state.annotations.redo()?.let { page -> scope.launch { reveal(viewport, page) } } },
            )
        }
    }

    ViewerTopBar(
        title = state.document.displayName,
        onBack = onBack,
        actions = {
            if (!sidePanel) ZoomButtons(viewport)
        },
    )
    if (sidePanel) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                pages(Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) { toolbar() }
            }
            SidePanel(
                document = state.document,
                viewport = viewport,
                pageCount = pageCount,
                onGoToPage = { askingPage = true },
                modifier = Modifier.width(if (window.shortHeight) 280.dp else 340.dp).fillMaxHeight(),
            )
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            pages(Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    onClick = { askingPage = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ) {
                    Text(
                        stringResource(R.string.viewer_page_indicator, viewport.currentPage + 1, pageCount),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                toolbar()
            }
        }
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

private suspend fun reveal(viewport: PdfViewportState, page: Int) {
    if (!viewport.isPageVisible(page)) viewport.goToPage(page)
}

/** One finger draws while a tool is active, so say how to move around; shown briefly. */
@Composable
private fun TwoFingerHint(visible: Boolean) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        show = visible
        if (visible) {
            delay(3_500)
            show = false
        }
    }
    AnimatedVisibility(show) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(
                stringResource(R.string.annotation_two_finger_hint),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Modifier.pdfBackdrop() = this.then(
    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
)

@Composable
private fun ViewerTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

@Composable
private fun ZoomButtons(viewport: PdfViewportState) {
    val scope = rememberCoroutineScope()
    val zoomOut = stringResource(R.string.viewer_zoom_out)
    val zoomIn = stringResource(R.string.viewer_zoom_in)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { scope.launch { viewport.zoomOut() } }, enabled = viewport.zoom > PdfViewportState.MIN_ZOOM) {
            Text("−", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = zoomOut })
        }
        Text(
            stringResource(R.string.viewer_zoom_level, (viewport.zoom * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { scope.launch { viewport.zoomIn() } }, enabled = viewport.zoom < PdfViewportState.MAX_ZOOM) {
            Text("+", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = zoomIn })
        }
    }
}

@Composable
private fun SidePanel(
    document: PdfDocument,
    viewport: PdfViewportState,
    pageCount: Int,
    onGoToPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val page = viewport.currentPage
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.viewer_info_heading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(document.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.viewer_info_pages, pageCount, Formatter.formatShortFileSize(context, document.sizeBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.viewer_info_imported, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(document.importedAt))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            stringResource(R.string.viewer_page_indicator, page + 1, pageCount),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { viewport.goToPage(page - 1) } }, enabled = page > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.viewer_previous_page))
            }
            IconButton(onClick = { scope.launch { viewport.goToPage(page + 1) } }, enabled = page < pageCount - 1) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.viewer_next_page))
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onGoToPage) { Text(stringResource(R.string.viewer_go_to_page)) }
        }
        Spacer(Modifier.height(8.dp))
        ZoomButtons(viewport)

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // Honest placeholder: describes what comes next, offers no controls that don't work yet.
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.viewer_next_steps_heading), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.viewer_next_steps_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_go_to_page)) },
        text = {
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
