package com.dnlfx.remindergenie.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.Schedule
import com.dnlfx.remindergenie.core.TrackedItem
import com.dnlfx.remindergenie.ui.components.LabeledRow
import com.dnlfx.remindergenie.ui.components.PastDatePickerDialog
import com.dnlfx.remindergenie.ui.components.PickerRow
import com.dnlfx.remindergenie.ui.components.TimePickerDialog
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Logs a replacement, optionally backdated, with an optional note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplaceSheet(item: TrackedItem, onDismiss: () -> Unit, onSave: (Instant, String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val opened = remember { Instant.now().atZone(zone) }
    var day by remember { mutableStateOf(opened.toLocalDate()) }
    var hour by remember { mutableStateOf(opened.hour) }
    var minute by remember { mutableStateOf(opened.minute) }
    var note by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Never in the future.
    val date = minOf(day.atTime(hour, minute).atZone(zone).toInstant(), Instant.now())

    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            then()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { close() }) { Text("Cancel") }
                Text(
                    "Replace ${item.name}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Button(
                    onClick = {
                        if (!saving) {
                            saving = true
                            val chosen = date
                            val text = note.trim()
                            close { onSave(chosen, text) }
                        }
                    },
                    modifier = Modifier.testTag("save-replacement"),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(8.dp))
            PickerRow(label = "Replaced on", value = DueText.mediumDate(date), onClick = { pickingDate = true })
            PickerRow(label = "At", value = DueText.time(date), onClick = { pickingTime = true }, icon = Icons.Outlined.Schedule)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("replacement-note"),
            )
            Text(
                "Forgot to log it? Set the date it actually happened and the countdown starts from there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (date >= item.startDate) {
                val nextDue = Schedule(date, item.intervalValue, item.intervalUnit, zone).dueDate
                LabeledRow("Next due", DueText.mediumDate(nextDue))
                if (item.tracksSpares) LabeledRow("Spares after this", "${maxOf(0, item.spareCount - 1)}")
            } else {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Start) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "This is before the current cycle, so it's added to history without changing the countdown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (pickingDate) {
        PastDatePickerDialog(
            initial = day,
            maxDate = LocalDate.now(zone),
            onDismiss = { pickingDate = false },
            onPick = { day = it },
        )
    }
    if (pickingTime) {
        TimePickerDialog(
            hour = hour,
            minute = minute,
            onDismiss = { pickingTime = false },
            onPick = { h, m -> hour = h; minute = m },
        )
    }
}
