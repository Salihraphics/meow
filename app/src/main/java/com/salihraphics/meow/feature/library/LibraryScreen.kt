package com.salihraphics.meow.feature.library

import com.salihraphics.meow.domain.model.LibrarySort
import com.salihraphics.meow.domain.model.LibraryFolder
import com.salihraphics.meow.core.designsystem.theme.MeowTokens
import com.salihraphics.meow.core.designsystem.icon.MeowIcons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import android.text.format.Formatter
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
    /** Folder names offered when the student has none yet, e.g. their subjects. */
    suggestedFolders: List<String> = emptyList(),
    /** Opens the PDF picker once on arrival (Home's "PDF ekle"). */
    openPickerOnStart: Boolean = false,
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val keyStatuses by viewModel.keyStatuses.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val untitled = stringResource(R.string.library_untitled)
    // System picker: no storage permission needed, and the student sees only what they choose.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onDocumentPicked(uri, untitled)
    }
    var pickerShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openPickerOnStart) {
        if (openPickerOnStart && !pickerShown) {
            pickerShown = true
            picker.launch(arrayOf("application/pdf"))
        }
    }
    LibraryContent(
        documents = documents,
        state = state,
        thumbnails = thumbnails,
        keyStatuses = keyStatuses,
        organization = LibraryOrganization(visible, folders, sort, missing, suggestedFolders),
        actions = LibraryActions(
            onAdd = { picker.launch(arrayOf("application/pdf")) },
            onOpen = onOpen,
            onAnswerKey = onAnswerKey,
            onRename = viewModel::rename,
            onRemove = viewModel::remove,
            onBack = onBack,
            onMessageShown = viewModel::messageShown,
            onQuery = viewModel::setQuery,
            onFilter = viewModel::setFilter,
            onSort = viewModel::setSort,
            onCreateFolder = viewModel::createFolder,
            onCreateFolders = viewModel::createFolders,
            onRenameFolder = viewModel::renameFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onMove = viewModel::moveToFolder,
            onTogglePin = viewModel::togglePinned,
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
    val onQuery: (String) -> Unit = {},
    val onFilter: (LibraryFilter) -> Unit = {},
    val onSort: (LibrarySort) -> Unit = {},
    val onCreateFolder: (String, PdfDocument?) -> Unit = { _, _ -> },
    val onCreateFolders: (List<String>) -> Unit = {},
    val onRenameFolder: (LibraryFolder, String) -> Unit = { _, _ -> },
    val onDeleteFolder: (LibraryFolder) -> Unit = {},
    val onMove: (PdfDocument, LibraryFolder?) -> Unit = { _, _ -> },
    val onTogglePin: (PdfDocument) -> Unit = {},
)

/** What organises the list: the arranged documents, the folders, the sort, missing files. */
data class LibraryOrganization(
    /** The documents to show; null means "all of them, as given". */
    val visible: List<PdfDocument>? = null,
    val folders: List<LibraryFolder> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENT,
    val missing: Set<String> = emptySet(),
    val suggestedFolders: List<String> = emptyList(),
)

@Composable
fun LibraryContent(
    documents: List<PdfDocument>?,
    state: LibraryUiState,
    thumbnails: ThumbnailLoader,
    actions: LibraryActions,
    keyStatuses: Map<String, KeyStatus> = emptyMap(),
    organization: LibraryOrganization = LibraryOrganization(),
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
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var renamingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val folders = organization.folders
    val shown = organization.visible ?: documents

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
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!documents.isNullOrEmpty()) SortMenu(organization.sort, actions.onSort)
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
                    else -> Column(Modifier.fillMaxSize()) {
                    OrganizeBar(
                        state = state,
                        folders = folders,
                        anyPinned = documents.any { it.pinned },
                        suggested = organization.suggestedFolders,
                        actions = actions,
                        onNewFolder = { creatingFolder = true },
                        onRenameFolder = { renamingFolder = it.id },
                        onDeleteFolder = { deletingFolder = it.id },
                    )
                    if (shown.isNullOrEmpty()) {
                        NothingHere(state)
                    } else LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown, key = { it.id }) { document ->
                            DocumentCard(
                                document = document,
                                thumbnails = thumbnails,
                                keyStatus = keyStatuses[document.id],
                                folderName = folders.firstOrNull { it.id == document.folderId }?.name
                                    ?.takeIf { state.filter !is LibraryFilter.Folder },
                                missing = document.id in organization.missing,
                                onOpen = { actions.onOpen(document) },
                                onAnswerKey = { actions.onAnswerKey(document) },
                                onRename = { renaming = document.id },
                                onRemove = { removing = document.id },
                                onMove = { moving = document.id },
                                onTogglePin = { actions.onTogglePin(document) },
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
    }

    if (creatingFolder) {
        FolderNameDialog(
            title = stringResource(R.string.folder_new),
            initial = "",
            onDismiss = { creatingFolder = false },
            onSave = { name -> creatingFolder = false; actions.onCreateFolder(name, null) },
        )
    }
    folders.firstOrNull { it.id == renamingFolder }?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.folder_rename),
            initial = folder.name,
            onDismiss = { renamingFolder = null },
            onSave = { name -> renamingFolder = null; actions.onRenameFolder(folder, name) },
        )
    }
    folders.firstOrNull { it.id == deletingFolder }?.let { folder ->
        val count = documents.orEmpty().count { it.folderId == folder.id }
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(stringResource(R.string.folder_delete_title, folder.name)) },
            text = {
                Text(
                    if (count == 0) stringResource(R.string.folder_delete_empty)
                    else stringResource(R.string.folder_delete_body, count),
                )
            },
            confirmButton = {
                TextButton(onClick = { deletingFolder = null; actions.onDeleteFolder(folder) }) {
                    Text(stringResource(if (count == 0) R.string.folder_delete_confirm_empty else R.string.folder_delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    documents?.firstOrNull { it.id == moving }?.let { document ->
        MoveToFolderDialog(
            document = document,
            folders = folders,
            onDismiss = { moving = null },
            onMove = { folder -> moving = null; actions.onMove(document, folder) },
            onNewFolder = { name -> moving = null; actions.onCreateFolder(name, document) },
        )
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
    is LibraryMessage.FolderCreated -> stringResource(R.string.folder_msg_created, message.name)
    LibraryMessage.FolderNameTaken -> stringResource(R.string.folder_msg_taken)
    LibraryMessage.FolderNameBlank -> stringResource(R.string.folder_msg_blank)
    is LibraryMessage.FolderDeleted -> if (message.moved == 0) stringResource(R.string.folder_msg_deleted_empty, message.name)
        else stringResource(R.string.folder_msg_deleted, message.name, message.moved)
    is LibraryMessage.Moved -> message.folder?.let { stringResource(R.string.folder_msg_moved, message.name, it) }
        ?: stringResource(R.string.folder_msg_unfiled, message.name)
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
    folderName: String?,
    missing: Boolean,
    onOpen: () -> Unit,
    onAnswerKey: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onMove: () -> Unit,
    onTogglePin: () -> Unit,
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
            if (missing) {
                // The copy is gone: say so plainly; opening shows the same message in full.
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.library_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (current != null) {
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
                if (document.pinned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MeowIcons.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.library_pinned),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
                Text(
                    document.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (document.lastOpenedAt != null && document.lastPageIndex > 0) {
                    Text(
                        stringResource(R.string.library_last_page, document.lastPageIndex + 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val context = LocalContext.current
                Text(
                    stringResource(R.string.library_meta, document.pageCount, Formatter.formatShortFileSize(context, document.sizeBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                folderName?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(MeowIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                    }
                }
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
                    DropdownMenuItem(
                        text = { Text(stringResource(if (document.pinned) R.string.library_unpin else R.string.library_pin)) },
                        onClick = {
                            menuOpen = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.folder_move)) }, onClick = {
                        menuOpen = false
                        onMove()
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

@Composable
private fun SortMenu(sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(MeowIcons.Sort, contentDescription = stringResource(R.string.library_sort)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    trailingIcon = { if (option == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { open = false; onSort(option) },
                )
            }
        }
    }
}

private val LibrarySort.labelRes: Int
    get() = when (this) {
        LibrarySort.RECENT -> R.string.library_sort_recent
        LibrarySort.NAME -> R.string.library_sort_name
        LibrarySort.ADDED -> R.string.library_sort_added
    }

/**
 * Search, and the parts of the library as chips: all, pinned, "Klasörsüz", each folder, and a
 * new folder. With a folder chosen, its rename and delete are right there.
 */
@Composable
private fun OrganizeBar(
    state: LibraryUiState,
    folders: List<LibraryFolder>,
    anyPinned: Boolean,
    suggested: List<String>,
    actions: LibraryActions,
    onNewFolder: () -> Unit,
    onRenameFolder: (LibraryFolder) -> Unit,
    onDeleteFolder: (LibraryFolder) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            placeholder = { Text(stringResource(R.string.library_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { actions.onQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.library_search_clear))
                    }
                }
            },
            singleLine = true,
            shape = MeowTokens.ControlShape,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item { FilterChip(state.filter == LibraryFilter.All, { actions.onFilter(LibraryFilter.All) }, { Text(stringResource(R.string.library_filter_all)) }) }
            if (anyPinned || state.filter == LibraryFilter.Pinned) {
                item {
                    FilterChip(
                        state.filter == LibraryFilter.Pinned,
                        { actions.onFilter(LibraryFilter.Pinned) },
                        { Text(stringResource(R.string.library_filter_pinned)) },
                        leadingIcon = { Icon(MeowIcons.Pin, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
            if (folders.isNotEmpty()) {
                item { FilterChip(state.filter == LibraryFilter.Unfiled, { actions.onFilter(LibraryFilter.Unfiled) }, { Text(stringResource(R.string.library_filter_unfiled)) }) }
            }
            items(folders, key = { it.id }) { folder ->
                FilterChip(
                    state.filter == LibraryFilter.Folder(folder.id),
                    { actions.onFilter(LibraryFilter.Folder(folder.id)) },
                    { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(MeowIcons.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
            item {
                AssistChip(
                    onClick = onNewFolder,
                    label = { Text(stringResource(R.string.folder_new)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        val folder = (state.filter as? LibraryFilter.Folder)?.let { f -> folders.firstOrNull { it.id == f.id } }
        if (folder != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onRenameFolder(folder) }) { Text(stringResource(R.string.folder_rename)) }
                TextButton(onClick = { onDeleteFolder(folder) }) { Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error) }
            }
        } else if (folders.isEmpty() && suggested.isNotEmpty()) {
            // A useful start, never forced: one folder per subject from the student's profile.
            TextButton(onClick = { actions.onCreateFolders(suggested) }) {
                Icon(MeowIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.folder_from_subjects, suggested.joinToString(", ")),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/** No document matches the search, or the chosen part of the library is empty. */
@Composable
private fun NothingHere(state: LibraryUiState) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            when {
                state.query.isNotBlank() -> stringResource(R.string.library_no_results, state.query.trim())
                state.filter is LibraryFilter.Folder -> stringResource(R.string.folder_empty)
                state.filter == LibraryFilter.Pinned -> stringResource(R.string.library_no_pinned)
                else -> stringResource(R.string.library_no_unfiled)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FolderNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val valid = LibraryFolder.sanitizeName(value) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(LibraryFolder.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.folder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Where a document goes: "Klasörsüz", an existing folder, or a new one. Its file stays put. */
@Composable
private fun MoveToFolderDialog(
    document: PdfDocument,
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onMove: (LibraryFolder?) -> Unit,
    onNewFolder: (String) -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var naming by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_move_title, document.displayName), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FolderOption(stringResource(R.string.library_filter_unfiled), selected = document.folderId == null) { onMove(null) }
                folders.forEach { folder -> FolderOption(folder.name, selected = document.folderId == folder.id) { onMove(folder) } }
                Spacer(Modifier.height(8.dp))
                // The name field appears on request, so the list of folders stays the focus.
                if (!naming) {
                    TextButton(onClick = { naming = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(LibraryFolder.MAX_NAME_LENGTH) },
                        label = { Text(stringResource(R.string.folder_new_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { onNewFolder(newName) }, enabled = LibraryFolder.sanitizeName(newName) != null) {
                        Text(stringResource(R.string.folder_create_and_move))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FolderOption(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
