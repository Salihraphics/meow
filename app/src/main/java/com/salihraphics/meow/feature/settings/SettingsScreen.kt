package com.salihraphics.meow.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.CircleShape
import com.salihraphics.meow.BuildConfig
import com.salihraphics.meow.core.designsystem.adaptive.LocalMeowWindow
import com.salihraphics.meow.core.designsystem.adaptive.MeowWidths
import com.salihraphics.meow.core.designsystem.adaptive.WidthClass
import com.salihraphics.meow.domain.model.AccountState
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.SelectionIndicator
import com.salihraphics.meow.core.designsystem.component.SelectionKind
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.SubjectCatalog
import com.salihraphics.meow.domain.model.ThemeMode
import com.salihraphics.meow.domain.model.UserPreferences
import com.salihraphics.meow.feature.common.ExamOptions
import com.salihraphics.meow.feature.common.LevelOptions
import com.salihraphics.meow.feature.common.NameField
import com.salihraphics.meow.feature.common.SessionOptions
import com.salihraphics.meow.feature.common.SubjectOptions
import com.salihraphics.meow.feature.common.labelRes

private enum class Editor { NAME, EXAM, SUBJECTS, LEVEL, SESSION, RESET }

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit, onRestartOnboarding: () -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var editor by rememberSaveable { mutableStateOf<Editor?>(null) }
    val current = prefs ?: return
    val profile = current.profile
    val notSet = stringResource(R.string.settings_not_set)

    val account by viewModel.account.collectAsStateWithLifecycle()
    val twoColumns = LocalMeowWindow.current.width == WidthClass.Expanded

    // Sections are defined once and arranged in one column (phones, portrait tablets) or two.
    val primarySections = @Composable {
                Section(stringResource(R.string.settings_section_profile)) {
                    ValueRow(stringResource(R.string.settings_name), profile.displayName ?: notSet) { editor = Editor.NAME }
                    ValueRow(stringResource(R.string.settings_exam), profile.exam?.let { stringResource(it.labelRes) } ?: notSet) {
                        editor = Editor.EXAM
                    }
                    val subjects = profile.subjects.map { stringResource(it.labelRes) }
                    ValueRow(stringResource(R.string.settings_subjects), subjects.joinToString().ifEmpty { notSet }) {
                        editor = Editor.SUBJECTS
                    }
                    ValueRow(stringResource(R.string.settings_level), profile.level?.let { stringResource(it.labelRes) } ?: notSet) {
                        editor = Editor.LEVEL
                    }
                    ValueRow(
                        stringResource(R.string.settings_session),
                        profile.sessionLength?.let { stringResource(R.string.session_minutes, it.minutes) } ?: notSet,
                    ) { editor = Editor.SESSION }
                }
                Section(stringResource(R.string.settings_section_companion)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_animations),
                        description = stringResource(R.string.settings_animations_desc),
                        checked = current.mascotAnimationsEnabled,
                        onCheckedChange = viewModel::setMascotAnimations,
                    )
                }
                Section(stringResource(R.string.settings_section_solving)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_immediate_feedback),
                        description = stringResource(R.string.settings_immediate_feedback_desc),
                        checked = current.immediateFeedback,
                        onCheckedChange = viewModel::setImmediateFeedback,
                    )
                }
    }
    val secondarySections = @Composable {
                Section(stringResource(R.string.settings_section_appearance)) {
                    Text(
                        stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    ThemeChoice(current.themeMode, viewModel::setThemeMode)
                }
            Section(stringResource(R.string.settings_section_account)) {
                AccountSection(account, signInAvailable = viewModel.isSignInAvailable)
            }
                Section(stringResource(R.string.settings_section_data)) {
                    ActionRow(
                        stringResource(R.string.settings_restart_onboarding),
                        stringResource(R.string.settings_restart_onboarding_desc),
                    ) { viewModel.restartOnboarding(onRestartOnboarding) }
                    ActionRow(
                        stringResource(R.string.settings_reset_all),
                        stringResource(R.string.settings_reset_all_desc),
                    ) { editor = Editor.RESET }
                    Text(
                        stringResource(R.string.settings_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (twoColumns) MeowWidths.Wide else 640.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f)) { primarySections() }
                    Column(Modifier.weight(1f)) { secondarySections() }
                }
            } else {
                primarySections()
                secondarySections()
            }

            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }

    when (editor) {
        null -> Unit
        Editor.NAME -> NameDialog(profile.displayName.orEmpty(), onDismiss = { editor = null }) {
            viewModel.setDisplayName(it)
            editor = null
        }
        Editor.EXAM -> ChoiceDialog(stringResource(R.string.settings_exam), onDismiss = { editor = null }, onClear = {
            viewModel.setExam(null)
            editor = null
        }) {
            ExamOptions(profile.exam) {
                viewModel.setExam(it)
                editor = null
            }
        }
        Editor.SUBJECTS -> SubjectsDialog(current, onDismiss = { editor = null }) {
            viewModel.setSubjects(it)
            editor = null
        }
        Editor.LEVEL -> ChoiceDialog(stringResource(R.string.settings_level), onDismiss = { editor = null }, onClear = {
            viewModel.setLevel(null)
            editor = null
        }) {
            LevelOptions(profile.level) {
                viewModel.setLevel(it)
                editor = null
            }
        }
        Editor.SESSION -> ChoiceDialog(stringResource(R.string.settings_session), onDismiss = { editor = null }, onClear = {
            viewModel.setSessionLength(null)
            editor = null
        }) {
            SessionOptions(profile.sessionLength) {
                viewModel.setSessionLength(it)
                editor = null
            }
        }
        Editor.RESET -> AlertDialog(
            onDismissRequest = { editor = null },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    editor = null
                    viewModel.resetAll(onRestartOnboarding)
                }) { Text(stringResource(R.string.settings_reset_confirm_yes), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { editor = null }) { Text(stringResource(R.string.settings_reset_confirm_no)) }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
    )
    content()
    HorizontalDivider(Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ValueRow(title: String, value: String, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Account entry point. Until real Google sign-in exists, this explains the plan instead of showing
 * a button that does nothing.
 */
@Composable
private fun AccountSection(account: AccountState, signInAvailable: Boolean) {
    when (account) {
        is AccountState.SignedIn -> ValueRow(
            title = stringResource(R.string.settings_account_signed_in),
            value = account.email ?: account.displayName.orEmpty(),
            onClick = null,
        )
        AccountState.SignedOut -> Row(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_google_sign_in), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_google_sign_in_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!signInAvailable) {
                Spacer(Modifier.width(12.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        stringResource(R.string.badge_soon),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, description: String, onClick: () -> Unit) = ValueRow(title, description, onClick)

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ThemeChoice(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelect(mode) })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(mode.labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                SelectionIndicator(selected = mode == selected, kind = SelectionKind.Single)
            }
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    options: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { options() }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) } },
    )
}

@Composable
private fun SubjectsDialog(prefs: UserPreferences, onDismiss: () -> Unit, onSave: (Set<Subject>) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(prefs.profile.subjects.map { it.name }) }
    val selected = draft.mapNotNullTo(LinkedHashSet()) { n -> Subject.entries.firstOrNull { it.name == n } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_subjects_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SubjectOptions(SubjectCatalog.subjectsFor(prefs.profile.exam), selected) { subject ->
                    draft = if (subject.name in draft) draft - subject.name else draft + subject.name
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_name)) },
        text = { NameField(value, { value = it }, onDone = { onSave(value) }) },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
