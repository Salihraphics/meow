package com.salihraphics.meow.feature.viewer

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.icon.MeowIcons
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.feature.annotation.AnnotationActions
import com.salihraphics.meow.feature.annotation.AnnotationSession
import com.salihraphics.meow.feature.annotation.AnnotationToolState
import com.salihraphics.meow.feature.annotation.AnnotationToolsSection
import com.salihraphics.meow.feature.study.StudyTimerSection
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Everything the study panel shows and does. */
class StudyPanelState(
    val document: PdfDocument,
    val viewport: PdfViewportState,
    val pageCount: Int,
    val tools: AnnotationToolState,
    val toolActions: AnnotationActions,
    val session: AnnotationSession,
    val workspace: ViewerWorkspace,
    val onGoToPage: () -> Unit,
    val onAddNote: () -> Unit,
)

/**
 * The study panel: answers ([header], fixed at the top), then tools, timer and cat, notes,
 * view (page, zoom, rotation) and the document, scrolling when they don't fit.
 */
@Composable
fun StudyPanel(state: StudyPanelState, modifier: Modifier = Modifier, header: @Composable ColumnScope.() -> Unit) {
    val panelLabel = stringResource(R.string.study_panel)
    Column(modifier.semantics { contentDescription = panelLabel }) {
        Column(Modifier.padding(horizontal = 12.dp)) { header() }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            PanelHeading(stringResource(R.string.panel_tools_heading))
            AnnotationToolsSection(state.tools, state.toolActions)

            state.workspace.timer?.let { timer ->
                PanelDivider()
                PanelHeading(stringResource(R.string.timer_heading))
                StudyTimerSection(timer, state.document.id)
            }

            PanelDivider()
            NotesSection(state)

            PanelDivider()
            ViewSection(state)

            PanelDivider()
            DocumentSection(state.document, state.pageCount)
        }
    }
}

@Composable
private fun PanelHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp).semantics { heading() },
    )
}

@Composable
private fun PanelDivider() = HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun NotesSection(state: StudyPanelState) {
    val page = state.viewport.currentPage
    LaunchedEffect(page) { state.session.load(page) }
    val notes by remember(page) { state.session.notes(page) }.collectAsState()
    val strokes by remember(page) { state.session.strokes(page) }.collectAsState()
    val inked = strokes.orEmpty().mapNotNull { it.noteId }.toSet()
    PanelHeading(stringResource(R.string.notes_heading))
    FilledTonalButton(onClick = state.onAddNote) {
        Icon(MeowIcons.StickyNote, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.note_add))
    }
    val list = notes.orEmpty()
    if (list.isEmpty()) {
        Text(
            stringResource(R.string.notes_on_page_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    } else {
        list.forEach { note ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.text.ifBlank { stringResource(if (note.id in inked) R.string.note_handwritten else R.string.note_empty) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { state.session.selectNote(note.id); state.session.editNote(note) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.note_edit))
                }
                IconButton(onClick = { state.session.deleteNote(note) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.note_delete))
                }
            }
        }
    }
    Text(
        stringResource(R.string.notes_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ViewSection(state: StudyPanelState) {
    val scope = rememberCoroutineScope()
    val viewport = state.viewport
    val page = viewport.currentPage
    PanelHeading(stringResource(R.string.panel_view_heading))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.viewer_page_indicator, page + 1, state.pageCount), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { scope.launch { viewport.goToPage(page - 1) } }, enabled = page > 0) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.viewer_previous_page))
        }
        IconButton(onClick = { scope.launch { viewport.goToPage(page + 1) } }, enabled = page < state.pageCount - 1) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.viewer_next_page))
        }
    }
    TextButton(onClick = state.onGoToPage) { Text(stringResource(R.string.viewer_go_to_page)) }

    val zoomOut = stringResource(R.string.viewer_zoom_out)
    val zoomIn = stringResource(R.string.viewer_zoom_in)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { scope.launch { viewport.zoomOut() } }, enabled = viewport.zoom > PdfViewportState.MIN_ZOOM + 0.001f) {
            Text("−", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = zoomOut })
        }
        Text(
            stringResource(R.string.viewer_zoom_level, (viewport.zoom * 100).roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(52.dp),
        )
        IconButton(onClick = { scope.launch { viewport.zoomIn() } }, enabled = viewport.zoom < PdfViewportState.MAX_ZOOM - 0.001f) {
            Text("+", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = zoomIn })
        }
        Spacer(Modifier.weight(1f))
        val resetLabel = stringResource(R.string.view_zoom_reset_cd)
        TextButton(onClick = { scope.launch { viewport.zoomTo(1f) } }, modifier = Modifier.semantics { contentDescription = resetLabel }) {
            Text(stringResource(R.string.view_zoom_reset), maxLines = 1)
        }
    }

    val rotation = state.workspace.rotation
    Text(
        stringResource(R.string.view_rotation_value, rotation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { state.workspace.onRotationChange(PageRotation.normalize(rotation - 90)) }) {
            Icon(MeowIcons.RotateLeft, contentDescription = stringResource(R.string.view_rotate_left))
        }
        IconButton(onClick = { state.workspace.onRotationChange(PageRotation.normalize(rotation + 90)) }) {
            Icon(MeowIcons.RotateRight, contentDescription = stringResource(R.string.view_rotate_right))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { state.workspace.onRotationChange(0) }, enabled = rotation != 0) {
            Text(stringResource(R.string.view_rotate_reset), maxLines = 1)
        }
    }
}

@Composable
private fun DocumentSection(document: PdfDocument, pageCount: Int) {
    val context = LocalContext.current
    PanelHeading(stringResource(R.string.viewer_info_heading))
    Text(document.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
    Spacer(Modifier.height(12.dp))
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
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
