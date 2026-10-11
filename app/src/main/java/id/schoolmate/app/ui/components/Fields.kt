package id.schoolmate.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.ui.model.FieldError
import id.schoolmate.app.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** A read-only text field that opens a picker when tapped. */
@Composable
fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = { Icon(Icons.Outlined.ExpandMore, contentDescription = null) },
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so the whole field is one tap target (incl. TalkBack).
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    label: String,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        value = Formatters.dateLong(date),
        label = label,
        onClick = { open = true },
        leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
        modifier = modifier,
    )
    if (open) {
        // DatePicker works in UTC milliseconds.
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/**
 * Time field with 24-hour picker. When [onClear] is provided the time is optional and
 * [emptyLabel] is shown while it's null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    time: LocalTime?,
    label: String,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    emptyLabel: String = "",
    onClear: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        value = time?.let(Formatters::time) ?: emptyLabel,
        label = label,
        onClick = { open = true },
        leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
        isError = isError,
        modifier = modifier,
    )
    if (open) {
        val initial = time ?: LocalTime.of(7, 0)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
            confirmButton = {
                TextButton(onClick = { onTimeChange(LocalTime.of(state.hour, state.minute)); open = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                Row {
                    if (onClear != null && time != null) {
                        TextButton(onClick = { onClear(); open = false }) { Text(stringResource(R.string.action_clear)) }
                    }
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
}

/** Subject selector; [allowNone] adds a "Tanpa mapel" option. */
@Composable
fun SubjectField(
    subjects: List<Subject>,
    selectedId: Long?,
    label: String,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = true,
    error: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = subjects.firstOrNull { it.id == selectedId }
    PickerField(
        value = selected?.name ?: if (allowNone) stringResource(R.string.no_subject) else "",
        label = label,
        onClick = { open = true },
        leadingIcon = { SubjectDot(selected?.let { Color(it.colorArgb) }, size = 14.dp) },
        isError = error != null,
        supportingText = error,
        modifier = modifier,
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                    if (allowNone) {
                        SubjectOption(null, stringResource(R.string.no_subject), selectedId == null) { onSelect(null); open = false }
                    }
                    subjects.forEach { s ->
                        SubjectOption(Color(s.colorArgb), s.name, s.id == selectedId) { onSelect(s.id); open = false }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SubjectOption(color: Color?, name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        SubjectDot(color, size = 12.dp)
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun fieldErrorText(error: FieldError?, maxLength: Int = 0): String? = when (error) {
    null -> null
    FieldError.REQUIRED -> stringResource(R.string.field_required)
    FieldError.TOO_LONG -> stringResource(R.string.field_too_long, maxLength)
    FieldError.NOT_FOUND -> stringResource(R.string.field_subject_missing)
    FieldError.INVALID -> stringResource(R.string.field_required)
}
