package com.salihraphics.meow.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWidths
import com.salihraphics.meow.core.designsystem.component.MeowPrimaryButton
import com.salihraphics.meow.core.designsystem.icon.MeowIcons
import com.salihraphics.meow.core.designsystem.theme.MeowTokens
import com.salihraphics.meow.core.designsystem.theme.cardBorder
import com.salihraphics.meow.core.designsystem.theme.cardColor
import com.salihraphics.meow.core.designsystem.theme.insetColor
import com.salihraphics.meow.core.mascot.MascotEvent
import com.salihraphics.meow.core.mascot.MeowCat
import com.salihraphics.meow.core.mascot.MascotState
import com.salihraphics.meow.core.mascot.rememberMascotState
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.PdfDocument
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.feature.exam.ExamCountdownCard
import com.salihraphics.meow.feature.exam.ExamGoalDialog
import com.salihraphics.meow.feature.exam.ExamGoalPrompt
import com.salihraphics.meow.feature.exam.rememberTickingNow
import com.salihraphics.meow.feature.library.ThumbnailLoader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Everything the dashboard can do; each one leads somewhere real. */
data class HomeActions(
    val onOpenSettings: () -> Unit = {},
    val onOpenLibrary: () -> Unit = {},
    /** Opens the library and its PDF picker. */
    val onAddPdf: () -> Unit = {},
    /** Opens a PDF where the student left off. */
    val onOpenDocument: (PdfDocument) -> Unit = {},
    /** Opens a PDF at a page, e.g. where a note is. */
    val onOpenPage: (documentId: String, pageIndex: Int) -> Unit = { _, _ -> },
    val onSaveExamGoal: (ExamGoal?) -> Unit = {},
)

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    profile: StudyProfile,
    thumbnails: ThumbnailLoader,
    actions: HomeActions,
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val examGoal by viewModel.examGoal.collectAsStateWithLifecycle()
    HomeContent(
        profile = profile,
        documents = documents,
        notes = notes,
        summary = summary,
        examGoal = examGoal,
        thumbnails = thumbnails,
        actions = actions.copy(onSaveExamGoal = viewModel::setExamGoal),
    )
}

/**
 * The home dashboard. Every number on it comes from what the student did in MEOW (documents,
 * notes, timer sessions, checked tests); with nothing recorded it says so instead of guessing.
 */
@Composable
fun HomeContent(
    profile: StudyProfile,
    documents: HomeDocuments?,
    notes: List<HomeNote>,
    summary: StudySummary?,
    examGoal: ExamGoal?,
    thumbnails: ThumbnailLoader,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
    /** False freezes the countdown at [clock]'s first value (screenshots). */
    ticking: Boolean = true,
) {
    val twoColumns = LocalMeowWindow.current.useTwoColumns
    var editingGoal by rememberSaveable { mutableStateOf(false) }

    val exam = @Composable { m: Modifier ->
        if (examGoal != null && examGoal.enabled) {
            ExamCountdownCard(examGoal, rememberTickingNow(clock, ticking), onEdit = { editingGoal = true }, modifier = m)
        } else {
            ExamGoalPrompt(
                goal = examGoal,
                onSetUp = { editingGoal = true },
                onTurnOn = { examGoal?.let { actions.onSaveExamGoal(it.copy(enabled = true)) } },
                modifier = m,
            )
        }
    }
    val studyLists = @Composable {
        if (!documents?.pinned.isNullOrEmpty()) {
            Section(stringResource(R.string.home_pinned_heading))
            PinnedRow(documents!!.pinned, actions.onOpenDocument)
        }
        if (!documents?.recent.isNullOrEmpty()) {
            Section(stringResource(R.string.home_recent_heading))
            RecentList(documents!!.recent, documents.folderNames, clock(), actions.onOpenDocument)
        }
    }
    val study = @Composable { showLists: Boolean ->
        when {
            documents == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            documents.continueWith == null -> FirstPdf(actions.onAddPdf)
            else -> ContinueCard(documents.continueWith, thumbnails, documents.folderNames, onOpen = { actions.onOpenDocument(documents.continueWith) })
        }
        if (showLists) studyLists()
    }
    val notesSection = @Composable {
        if (notes.isNotEmpty()) {
            Section(stringResource(R.string.home_notes_heading))
            NotesList(notes, actions.onOpenPage)
        }
    }
    val side = @Composable {
        Section(stringResource(R.string.home_quick_heading), top = 0.dp)
        QuickActions(documents?.count, actions, onExam = { editingGoal = true })
        Section(stringResource(R.string.home_summary_heading))
        SummaryCard(summary)
        notesSection()
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (twoColumns) MeowWidths.Wide else 640.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (twoColumns) 32.dp else 20.dp),
        ) {
            Greeting(profile, twoColumns, actions.onOpenSettings)
            Spacer(Modifier.height(16.dp))
            if (twoColumns) {
                // Study first, on the left from the very top; the countdown heads the right
                // column, in the top corner, with the actions and the week under it.
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1.25f)) {
                        Section(stringResource(R.string.home_continue_heading), top = 0.dp)
                        study(true)
                    }
                    Column(Modifier.weight(1f)) {
                        exam(Modifier)
                        Spacer(Modifier.height(20.dp))
                        side()
                    }
                }
            } else {
                // Phones: the next study step first, then the countdown, then everything else.
                Section(stringResource(R.string.home_continue_heading), top = 0.dp)
                study(false)
                Spacer(Modifier.height(16.dp))
                QuickActions(documents?.count, actions, onExam = { editingGoal = true })
                Spacer(Modifier.height(16.dp))
                exam(Modifier)
                studyLists()
                Section(stringResource(R.string.home_summary_heading))
                SummaryCard(summary)
                notesSection()
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editingGoal) {
        ExamGoalDialog(
            current = examGoal,
            onDismiss = { editingGoal = false },
            onSave = {
                editingGoal = false
                actions.onSaveExamGoal(it)
            },
        )
    }
}

/**
 * The greeting: the cat as a small companion beside it (tap it, it reacts), and what it has to
 * say as one quiet line. Compact on purpose: the study actions come right under it.
 */
@Composable
private fun Greeting(profile: StudyProfile, wide: Boolean, onOpenSettings: () -> Unit) {
    val cat = rememberMascotState(MascotState())
    val speech = cat.state.speech?.let { stringResource(it) } ?: stringResource(R.string.home_subtitle)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MeowCat(
            mood = cat.state.mood,
            modifier = Modifier.size(if (wide) 64.dp else 52.dp),
            onTap = { cat.onEvent(MascotEvent.Tapped) },
            contentDescription = stringResource(R.string.cd_cat_tap),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Text(
                profile.displayName?.let { stringResource(R.string.home_greeting_named, it) }
                    ?: stringResource(R.string.home_greeting),
                style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                speech,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_open_settings))
        }
    }
}

@Composable
private fun Section(title: String, top: Dp = 24.dp) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = top, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MeowTokens.CardShape,
        color = cardColor,
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .clip(MeowTokens.CardShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick) else Modifier),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun ContinueCard(document: PdfDocument, thumbnails: ThumbnailLoader, folders: Map<String, String>, onOpen: () -> Unit) {
    val started = document.lastOpenedAt != null
    Card(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(document, thumbnails, width = 64.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    document.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (started) stringResource(R.string.home_page_of, document.lastPageIndex + 1, document.pageCount)
                    else stringResource(R.string.home_not_started, document.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                document.folderId?.let(folders::get)?.let { FolderLabel(it) }
                if (started && document.pageCount > 0) {
                    LinearProgressIndicator(
                        progress = { ((document.lastPageIndex + 1).toFloat() / document.pageCount).coerceIn(0f, 1f) },
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MeowPrimaryButton(
            text = stringResource(if (started) R.string.home_continue else R.string.home_start),
            onClick = onOpen,
        )
    }
}

@Composable
private fun FolderLabel(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(MeowIcons.Folder, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun Thumbnail(document: PdfDocument, thumbnails: ThumbnailLoader, width: Dp) {
    var image by remember(document.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(document.id) { image = thumbnails(document) }
    Surface(
        shape = MeowTokens.ControlShape,
        color = insetColor,
        border = cardBorder,
        modifier = Modifier.width(width).height(width * 1.38f),
    ) {
        image?.let {
            androidx.compose.foundation.Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FirstPdf(onAdd: () -> Unit) {
    Card {
        Text(stringResource(R.string.home_first_pdf_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.home_first_pdf_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        MeowPrimaryButton(text = stringResource(R.string.home_action_add), onClick = onAdd)
    }
}

@Composable
private fun PinnedRow(pinned: List<PdfDocument>, onOpen: (PdfDocument) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
        items(pinned, key = { it.id }) { doc ->
            Card(Modifier.width(168.dp), onClick = { onOpen(doc) }) {
                Icon(MeowIcons.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    doc.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).heightIn(min = 40.dp),
                )
                Text(
                    if (doc.lastOpenedAt != null) stringResource(R.string.home_page_of, doc.lastPageIndex + 1, doc.pageCount)
                    else stringResource(R.string.home_not_started, doc.pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecentList(recent: List<PdfDocument>, folders: Map<String, String>, now: Long, onOpen: (PdfDocument) -> Unit) {
    Surface(shape = MeowTokens.CardShape, color = cardColor, border = cardBorder, modifier = Modifier.fillMaxWidth()) {
        Column {
            recent.forEachIndexed { i, doc ->
                val meta = listOfNotNull(
                    stringResource(R.string.home_page_of, doc.lastPageIndex + 1, doc.pageCount),
                    doc.folderId?.let(folders::get),
                    doc.lastOpenedAt?.let { whenText(it, now) },
                ).joinToString(" · ")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(doc) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(doc.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (i < recent.lastIndex) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(count: Int?, actions: HomeActions, onExam: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(Icons.Filled.Add, stringResource(R.string.home_action_add), actions.onAddPdf, primary = true)
        QuickAction(
            Icons.AutoMirrored.Filled.List,
            if (count != null && count > 0) stringResource(R.string.home_action_library_count, count) else stringResource(R.string.home_action_library),
            actions.onOpenLibrary,
        )
        QuickAction(Icons.Filled.DateRange, stringResource(R.string.home_action_exam), onExam)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, primary: Boolean = false) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
    if (primary) {
        FilledTonalButton(onClick = onClick, shape = MeowTokens.ControlShape, modifier = Modifier.heightIn(min = MeowTokens.TouchTarget), content = content)
    } else {
        OutlinedButton(onClick = onClick, shape = MeowTokens.ControlShape, modifier = Modifier.heightIn(min = MeowTokens.TouchTarget), content = content)
    }
}

@Composable
private fun SummaryCard(summary: StudySummary?) {
    Card {
        when {
            summary == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            summary.isEmpty -> Text(
                stringResource(R.string.home_summary_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(stringResource(R.string.home_summary_today), durationText(summary.todayMs), null)
                    Stat(stringResource(R.string.home_summary_week), durationText(summary.weekMs), null)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(
                        stringResource(R.string.home_summary_sessions),
                        summary.weekSessions.toString(),
                        stringResource(R.string.home_summary_completed, summary.weekCompleted),
                    )
                    Stat(
                        stringResource(R.string.home_summary_tests),
                        summary.weekTests.toString(),
                        if (summary.weekTests > 0) stringResource(R.string.home_summary_tests_detail, summary.weekCorrect, summary.weekIncorrect) else null,
                    )
                }
                Text(
                    stringResource(R.string.home_summary_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String, detail: String?) {
    Surface(shape = MeowTokens.ControlShape, color = insetColor, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun NotesList(notes: List<HomeNote>, onOpenPage: (String, Int) -> Unit) {
    Surface(shape = MeowTokens.CardShape, color = cardColor, border = cardBorder, modifier = Modifier.fillMaxWidth()) {
        Column {
            notes.forEachIndexed { i, item ->
                val note = item.note
                val open = stringResource(R.string.home_note_open, item.documentName, note.pageIndex + 1)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = open) { onOpenPage(note.documentId, note.pageIndex) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (note.text.isEmpty()) Icons.Filled.Edit else MeowIcons.StickyNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            note.text.ifEmpty { stringResource(R.string.home_note_ink) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.home_note_where, item.documentName, note.pageIndex + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (i < notes.lastIndex) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun durationText(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes < 60) stringResource(R.string.duration_minutes, minutes)
    else stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

/** "Bugün", "Dün", "3 gün önce" or a date, by local calendar days. */
@Composable
private fun whenText(time: Long, now: Long): String {
    val days = calendarDaysBetween(time, now)
    return when {
        days <= 0 -> stringResource(R.string.when_today)
        days == 1 -> stringResource(R.string.when_yesterday)
        days < 7 -> stringResource(R.string.when_days_ago, days)
        else -> SimpleDateFormat("d MMMM", Locale("tr", "TR")).format(time)
    }
}

internal fun calendarDaysBetween(from: Long, to: Long, zone: TimeZone = TimeZone.getDefault()): Int {
    val start = StudySummary.startOfDay(from, zone)
    val end = StudySummary.startOfDay(to, zone)
    val a = Calendar.getInstance(zone).apply { timeInMillis = start }
    var days = 0
    // Day by day, so 23- and 25-hour days still count as one; recent dates only, so it's short.
    while (a.timeInMillis < end && days < 400) {
        a.add(Calendar.DAY_OF_MONTH, 1)
        days++
    }
    return days
}
