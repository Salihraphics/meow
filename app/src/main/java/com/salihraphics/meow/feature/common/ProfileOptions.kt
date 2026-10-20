package com.salihraphics.meow.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.core.designsystem.component.SelectableChip
import com.salihraphics.meow.core.designsystem.component.SelectableOptionCard
import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.SessionLength
import com.salihraphics.meow.domain.model.StudyLevel
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.Subject

// Option lists shared by onboarding and Settings.

@Composable
fun ExamOptions(selected: ExamType?, onSelect: (ExamType) -> Unit) {
    ExamType.entries.forEach { exam ->
        SelectableOptionCard(
            title = stringResource(exam.labelRes),
            description = stringResource(exam.descriptionRes),
            selected = exam == selected,
            onClick = { onSelect(exam) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectOptions(available: List<Subject>, selected: Set<Subject>, onToggle: (Subject) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        available.forEach { subject ->
            SelectableChip(
                label = stringResource(subject.labelRes),
                selected = subject in selected,
                onToggle = { onToggle(subject) },
            )
        }
    }
}

@Composable
fun LevelOptions(selected: StudyLevel?, onSelect: (StudyLevel) -> Unit) {
    StudyLevel.entries.forEach { level ->
        SelectableOptionCard(
            title = stringResource(level.labelRes),
            selected = level == selected,
            onClick = { onSelect(level) },
        )
    }
}

@Composable
fun SessionOptions(selected: SessionLength?, onSelect: (SessionLength) -> Unit) {
    SessionLength.entries.forEach { length ->
        SelectableOptionCard(
            title = stringResource(R.string.session_minutes, length.minutes),
            description = stringResource(length.descriptionRes),
            selected = length == selected,
            onClick = { onSelect(length) },
        )
    }
}

@Composable
fun NameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(StudyProfile.MAX_NAME_LENGTH)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.name_label)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            onDone()
        }),
    )
}
