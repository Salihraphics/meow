package com.salihraphics.meow.feature.library

import com.salihraphics.meow.core.designsystem.theme.cardBorder
import com.salihraphics.meow.core.designsystem.theme.cardColor
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.adaptive.MeowWidths
import com.salihraphics.meow.core.designsystem.component.MeowButtonStyle
import com.salihraphics.meow.core.designsystem.component.MeowPrimaryButton
import com.salihraphics.meow.core.mascot.MascotEvent
import com.salihraphics.meow.core.mascot.MascotMood
import com.salihraphics.meow.core.mascot.MascotState
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.core.mascot.rememberMascotState
import com.salihraphics.meow.domain.model.KeyStatus
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.domain.model.PdfImportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads a document's first-page thumbnail; null if there is none. */
typealias ThumbnailLoader = suspend (PdfDocument) -> ImageBitmap?

fun fileThumbnailLoader(fileFor: (String) -> File): ThumbnailLoader = { document ->
    withContext(Dispatchers.IO) {
        val file = fileFor(document.id)
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
    }
}

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    thumbnails: ThumbnailLoader,
    onOpen: (PdfDocument) -> Unit,
    onAnswerKey: (PdfDocument) -> Unit,
    onBack: () -> Unit,
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val keyStatuses by viewModel.keyStatuses.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val untitled = stringResource(R.string.library_untitled)
    // System picker: no storage permission needed, and the student sees only what they choose.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onDocumentPicked(uri, untitled)
    }
    LibraryContent(
        documents = documents,
        state = state,
        thumbnails = thumbnails,
        keyStatuses = keyStatuses,
        actions = LibraryActions(
            onAdd = { picker.launch(arrayOf("application/pdf")) },
            onOpen = onOpen,
            onAnswerKey = onAnswerKey,
            onRename = viewModel::rename,
            onRemove = viewModel::remove,
            onBack = onBack,
            onMessageShown = viewModel::messageShown,
        ),
    )
}

data class LibraryActions(
    val onAdd: () -> Unit = {},
    val onOpen: (PdfDocument) -> Unit = {},
    val onAnswerKey: (PdfDocument) -> Unit = {},
    val onRename: (PdfDocument, String) -> Unit = { _, _ -> },
    val onRemove: (PdfDocument) -> Unit = {},
    val onBack: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun LibraryContent(
    documents: List<PdfDocument>?,
    state: LibraryUiState,
    thumbnails: ThumbnailLoader,
    actions: LibraryActions,
    keyStatuses: Map<String, KeyStatus> = emptyMap(),
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.library_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        floatingActionButton = {
            if (!documents.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { if (!state.isImporting) actions.onAdd() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_add)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MeowWidths.Wide).fillMaxSize()) {
                if (state.isImporting) ImportingBanner()
                when {
                    documents == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    documents.isEmpty() -> EmptyLibrary(isImporting = state.isImporting, onAdd = actions.onAdd)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(documents, key = { it.id }) { document ->
                            DocumentCard(
                                document = document,
                                thumbnails = thumbnails,
                                keyStatus = keyStatuses[document.id],
                                onOpen = { actions.onOpen(document) },
                                onAnswerKey = { actions.onAnswerKey(document) },
                                onRename = { renaming = document.id },
                                onRemove = { removing = document.id },
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.library_copy_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    documents?.firstOrNull { it.id == renaming }?.let { document ->
        RenameDialog(
            initial = document.displayName,
            onDismiss = { renaming = null },
            onSave = {
                actions.onRename(document, it)
                renaming = null
            },
        )
    }
    documents?.firstOrNull { it.id == removing }?.let { document ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.library_remove_title)) },
            text = { Text(stringResource(R.string.library_remove_body, document.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onRemove(document)
                    removing = null
                }) { Text(stringResource(R.string.library_remove_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun messageText(message: LibraryMessage): String = when (message) {
    is LibraryMessage.Imported -> stringResource(R.string.library_msg_imported, message.name)
    is LibraryMessage.AlreadyInLibrary -> stringResource(R.string.library_msg_duplicate, message.name)
    is LibraryMessage.Removed -> stringResource(R.string.library_msg_removed, message.name)
    LibraryMessage.RenameFailed -> stringResource(R.string.library_msg_rename_failed)
    is LibraryMessage.ImportFailed -> stringResource(
        when (message.error) {
            PdfImportError.NOT_A_PDF -> R.string.import_error_not_pdf
            PdfImportError.EMPTY -> R.string.import_error_empty
            PdfImportError.PASSWORD_PROTECTED -> R.string.import_error_password
            PdfImportError.DAMAGED -> R.string.import_error_damaged
            PdfImportError.NO_ACCESS -> R.string.import_error_no_access
            PdfImportError.STORAGE -> R.string.import_error_storage
        },
    )
}

@Composable
private fun ImportingBanner() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.library_importing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        // A visible track in both themes, not just the moving bar.
        LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EmptyLibrary(isImporting: Boolean, onAdd: () -> Unit) {
    val cat = rememberMascotState(MascotState(MascotMood.Curious))
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = MeowWidths.Readable), horizontalAlignment = Alignment.CenterHorizontally) {
            MeowCat(
                mood = cat.state.mood,
                onTap = { cat.onEvent(MascotEvent.Tapped) },
                contentDescription = stringResource(R.string.cd_cat_tap),
                modifier = Modifier.size(160.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            MeowPrimaryButton(
                text = stringResource(R.string.library_add),
                onClick = onAdd,
                enabled = !isImporting,
                style = MeowButtonStyle.Brand,
                showArrow = false,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.library_copy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DocumentCard(
    document: PdfDocument,
    thumbnails: ThumbnailLoader,
    keyStatus: KeyStatus?,
    onOpen: () -> Unit,
    onAnswerKey: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var thumbnail by remember(document.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(document.id) { thumbnail = thumbnails(document) }
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Surface(
            onClick = onOpen,
            shape = MaterialTheme.shapes.medium,
            color = cardColor,
            border = cardBorder,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
        ) {
            val current = thumbnail
            if (current != null) {
                Image(
                    bitmap = current,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().background(Color.White),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text("PDF", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    document.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (document.lastOpenedAt != null && document.lastPageIndex > 0) {
                        stringResource(R.string.library_last_page, document.lastPageIndex + 1)
                    } else {
                        stringResource(R.string.library_pages, document.pageCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (keyStatus != null) KeyStatusLabel(keyStatus, onAnswerKey)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_more, document.displayName))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.answer_key_title)) }, onClick = {
                        menuOpen = false
                        onAnswerKey()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.library_rename)) }, onClick = {
                        menuOpen = false
                        onRename()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.library_remove)) }, onClick = {
                        menuOpen = false
                        onRemove()
                    })
                }
            }
        }
    }
}

/** One line under a document: where its answer key stands. Tapping it opens the key. */
@Composable
private fun KeyStatusLabel(status: KeyStatus, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (text, color) = when (status) {
        is KeyStatus.Searching -> stringResource(R.string.key_status_searching) to scheme.onSurfaceVariant
        is KeyStatus.Found -> if (status.confirmed) {
            stringResource(R.string.key_status_confirmed) to scheme.primary
        } else {
            stringResource(R.string.key_status_found, status.tests) to scheme.primary
        }
        KeyStatus.NotFound -> stringResource(R.string.key_status_not_found) to scheme.onSurfaceVariant
        KeyStatus.Failed -> stringResource(R.string.key_status_failed) to scheme.error
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 2,
        modifier = Modifier
            .padding(top = 2.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val valid = PdfDocument.sanitizeName(value) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(PdfDocument.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.library_rename_label)) },
                singleLine = true,
                isError = !valid,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
