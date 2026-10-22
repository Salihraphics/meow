package com.salihraphics.meow.feature.exam

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.salihraphics.meow.R
import com.salihraphics.meow.domain.model.ExamDate
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.ExamGoalType
import com.salihraphics.meow.domain.model.ExamSession

/**
 * Choose YKS, LGS or nothing; check or change each sitting's date and time; show or hide the
 * countdown. Opened from Home and from Settings. [onSave] gets null for "Hedef yok".
 */
@Composable
fun ExamGoalDialog(current: ExamGoal?, onDismiss: () -> Unit, onSave: (ExamGoal?) -> Unit) {
    var type by rememberSaveable { mutableStateOf(current?.type) }
    // Dates the student entered, kept per session even while switching exams in the dialog;
    // only the chosen exam's are saved.
    var dates by remember { mutableStateOf(current?.dates.orEmpty()) }
    var enabled by rememberSaveable { mutableStateOf(current?.enabled ?: true) }
    var editing by remember { mutableStateOf<ExamSession?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_set_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ExamGoalType.entries.forEach { option ->
                    Choice(stringResource(option.labelRes), selected = type == option) { type = option }
                }
                Choice(stringResource(R.string.exam_none), selected = type == null) { type = null }
                type?.let { chosen ->
                    val goal = ExamGoal(chosen, dates.filterKeys { it.goal == chosen }, enabled)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.exam_dialog_dates), style = MaterialTheme.typography.titleSmall)
                    goal.sessions.forEach { session ->
                        SessionDate(
                            name = stringResource(session.labelRes),
                            date = goal.dateOf(session),
                            estimated = goal.isEstimated(session),
                            onChange = { editing = session },
                            onReset = { dates = dates - session },
                        )
                    }
                    Text(
                        stringResource(R.string.exam_dialog_estimate_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(top = 4.dp)
                            .toggleable(value = enabled, role = Role.Switch, onValueChange = { enabled = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.exam_show_countdown), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(type?.let { ExamGoal(it, dates.filterKeys { s -> s.goal == it }, enabled) })
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    editing?.let { session ->
        val goal = ExamGoal(session.goal, dates)
        ExamDateTimePicker(
            initial = goal.dateOf(session),
            onDismiss = { editing = null },
            onPicked = { picked ->
                dates = dates + (session to picked)
                editing = null
            },
        )
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SessionDate(name: String, date: ExamDate, estimated: Boolean, onChange: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("$name · " + stringResource(if (estimated) R.string.exam_estimated else R.string.exam_custom), style = MaterialTheme.typography.labelLarge)
        Text(formatExamDate(date), style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(onClick = onChange) { Text(stringResource(R.string.exam_change_date)) }
            if (!estimated) TextButton(onClick = onReset) { Text(stringResource(R.string.exam_reset_date)) }
        }
    }
}

/** A date, then the start time. The picker's day is read as a calendar day, never shifted by zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDateTimePicker(initial: ExamDate, onDismiss: () -> Unit, onPicked: (ExamDate) -> Unit) {
    var pickedDay by remember { mutableStateOf<Long?>(null) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis(), yearRange = 2024..2040)
    val day = pickedDay
    if (day == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickedDay = dateState.selectedDateMillis }, enabled = dateState.selectedDateMillis != null) {
                    Text(stringResource(R.string.action_next))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(dateState) }
    } else {
        val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.exam_pick_time)) },
            text = { TimeInput(timeState) },
            confirmButton = {
                TextButton(onClick = { onPicked(ExamDate.fromPickerMillis(day, timeState.hour, timeState.minute)) }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
