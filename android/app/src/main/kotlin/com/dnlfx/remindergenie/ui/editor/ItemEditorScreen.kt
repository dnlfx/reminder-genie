package com.dnlfx.remindergenie.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.IntervalUnit
import com.dnlfx.remindergenie.core.ItemColor
import com.dnlfx.remindergenie.core.ItemDraft
import com.dnlfx.remindergenie.core.ItemTemplate
import com.dnlfx.remindergenie.core.ReminderPlanner
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.ui.UiEnv
import com.dnlfx.remindergenie.ui.components.IconBadge
import com.dnlfx.remindergenie.ui.components.ItemIcons
import com.dnlfx.remindergenie.ui.components.PastDatePickerDialog
import com.dnlfx.remindergenie.ui.components.PickerRow
import com.dnlfx.remindergenie.ui.components.SectionCard
import com.dnlfx.remindergenie.ui.components.StepperRow
import com.dnlfx.remindergenie.ui.components.TimePickerDialog
import com.dnlfx.remindergenie.ui.theme.color
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface EditorMode {
    data class Create(val templateName: String, val name: String) : EditorMode
    data class Edit(val itemId: String) : EditorMode
}

/** Keeps the draft through rotation. Nothing is saved until Add or Save. */
class EditorState(val original: ItemDraft) : ViewModel() {
    var draft by mutableStateOf(original)
    var saved = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorScreen(env: UiEnv, mode: EditorMode, onClose: () -> Unit, onSaved: () -> Unit) {
    val graph = env.graph
    val initial = remember(mode) {
        val settings = graph.settings.current
        when (mode) {
            is EditorMode.Create -> ItemTemplate.all.firstOrNull { it.name == mode.templateName }
                ?.let { ItemDraft.fromTemplate(it, settings.defaultHour, settings.defaultMinute) }
                ?: ItemDraft.blank(mode.name, settings.defaultHour, settings.defaultMinute)
            is EditorMode.Edit -> graph.store.item(mode.itemId)?.let { ItemDraft.fromItem(it) }
        }
    }
    if (initial == null) {
        // The item was deleted meanwhile.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val state = viewModel { EditorState(initial) }
    val draft = state.draft
    fun update(transform: (ItemDraft) -> ItemDraft) {
        state.draft = transform(state.draft)
    }

    val isEditing = mode is EditorMode.Edit
    val hasChanges = draft != state.original
    var confirmingDiscard by remember { mutableStateOf(false) }

    fun close() {
        if (hasChanges) confirmingDiscard = true else onClose()
    }

    fun save() {
        if (state.saved) return
        state.saved = true
        when (mode) {
            is EditorMode.Create -> {
                graph.store.add(draft)
                env.requestNotificationsIfNotAsked()
            }
            is EditorMode.Edit -> graph.store.update(mode.itemId, draft)
        }
        onSaved()
    }

    BackHandler(enabled = hasChanges) { confirmingDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit item" else "New item") },
                navigationIcon = { IconButton(onClick = ::close) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") } },
                actions = {
                    TextButton(onClick = ::save, enabled = draft.isValid, modifier = Modifier.testTag("save")) {
                        Text(if (isEditing) "Save" else "Add", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            NameAndLook(draft, focusName = !isEditing && draft.name.isEmpty(), onChange = ::update)
            ScheduleSection(draft, isEditing, onChange = ::update)
            RemindersSection(draft, isEditing, onChange = ::update)
            InventorySection(draft, onChange = ::update)
            SectionCard(title = "Notes") {
                TextField(
                    value = draft.notes,
                    onValueChange = { notes -> update { it.copy(notes = notes) } },
                    placeholder = { Text("Brand, size, prescription, where to buy…") },
                    minLines = 3,
                    maxLines = 8,
                    colors = transparentFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("notes-field"),
                )
            }
        }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text("Discard your changes?") },
            confirmButton = {
                TextButton(onClick = { confirmingDiscard = false; onClose() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameAndLook(draft: ItemDraft, focusName: Boolean, onChange: ((ItemDraft) -> ItemDraft) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // A blank custom item starts with the keyboard up.
        if (focusName) {
            delay(300)
            focus.requestFocus()
        }
    }
    SectionCard {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ItemIcons.forName(draft.symbolName), draft.color.color, size = 44.dp, filled = true)
            TextField(
                value = draft.name,
                onValueChange = { name -> onChange { it.copy(name = name) } },
                placeholder = { Text("Name, e.g. Contact lenses") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                colors = transparentFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("name-field"),
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        // Colors
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (color in ItemColor.entries) {
                val selected = draft.color == color
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton) { onChange { it.copy(color = color) } }
                        .semantics { contentDescription = color.displayName },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(color.color))
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        // Icons
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            val tint = draft.color.color
            for (symbol in ItemTemplate.symbols) {
                val selected = draft.symbolName == symbol.name
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (selected) tint.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .then(if (selected) Modifier.border(BorderStroke(2.dp, tint), CircleShape) else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton) { onChange { it.copy(symbolName = symbol.name) } }
                        .semantics { contentDescription = symbol.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        ItemIcons.forName(symbol.name),
                        contentDescription = null,
                        tint = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(draft: ItemDraft, isEditing: Boolean, onChange: ((ItemDraft) -> ItemDraft) -> Unit) {
    val zone = ZoneId.systemDefault()
    var pickingDate by remember { mutableStateOf(false) }
    SectionCard(title = "How often", footer = "Next due ${DueText.fullDate(draft.previewSchedule().dueDate)}.") {
        StepperRow(
            label = "Every ${pluralized(draft.intervalValue, draft.intervalUnit.label(1))}",
            value = draft.intervalValue,
            range = draft.intervalUnit.allowedRange,
            onValueChange = { value -> onChange { it.copy(intervalValue = value) } },
            testTag = "interval",
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val units = IntervalUnit.entries
            units.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = draft.intervalUnit == unit,
                    onClick = { onChange { it.withUnit(unit) } },
                    shape = SegmentedButtonDefaults.itemShape(index, units.size),
                    icon = {},
                ) { Text(unit.displayName, maxLines = 1) }
            }
        }
        PickerRow(
            label = if (isEditing) "Current one started" else "Last replaced",
            value = DueText.mediumDate(draft.startDate),
            onClick = { pickingDate = true },
        )
    }
    if (pickingDate) {
        val today = LocalDate.now(zone)
        PastDatePickerDialog(
            initial = draft.startDate.atZone(zone).toLocalDate(),
            maxDate = today,
            onDismiss = { pickingDate = false },
            onPick = { date ->
                onChange { current ->
                    // Keep the time of day, as the iOS date picker does.
                    val time = current.startDate.atZone(zone).toLocalTime()
                    val picked = date.atTime(time).atZone(zone).toInstant()
                    current.copy(startDate = minOf(picked, Instant.now()))
                }
            },
        )
    }
}

@Composable
private fun RemindersSection(draft: ItemDraft, isEditing: Boolean, onChange: ((ItemDraft) -> ItemDraft) -> Unit) {
    var pickingTime by remember { mutableStateOf(false) }
    val time = DueText.timeOfDay(draft.reminderHour, draft.reminderMinute)
    val summary = buildString {
        append("You'll be reminded on the due day at $time")
        if (draft.leadDays > 0) append(", with a heads-up ${pluralized(draft.leadDays, "day")} before")
        append(if (draft.remindDailyWhenOverdue) ", then daily for up to ${ReminderPlanner.OVERDUE_FOLLOW_UPS} days if it's overdue." else ".")
    }
    SectionCard(title = "Reminders", footer = summary) {
        PickerRow(label = "Remind me at", value = time, onClick = { pickingTime = true }, icon = Icons.Outlined.Schedule)
        StepperRow(
            label = if (draft.leadDays == 0) "No heads-up" else "Heads-up ${pluralized(draft.leadDays, "day")} before",
            value = draft.leadDays,
            range = 0..30,
            onValueChange = { days -> onChange { it.copy(leadDays = days) } },
            testTag = "heads-up",
        )
        SwitchRow("Keep reminding daily if overdue", draft.remindDailyWhenOverdue) { on -> onChange { it.copy(remindDailyWhenOverdue = on) } }
        if (isEditing) {
            SwitchRow("Pause reminders", draft.isPaused) { on -> onChange { it.copy(isPaused = on) } }
        }
    }
    if (pickingTime) {
        TimePickerDialog(
            hour = draft.reminderHour,
            minute = draft.reminderMinute,
            onDismiss = { pickingTime = false },
            onPick = { hour, minute -> onChange { it.copy(reminderHour = hour, reminderMinute = minute) } },
        )
    }
}

@Composable
private fun InventorySection(draft: ItemDraft, onChange: ((ItemDraft) -> ItemDraft) -> Unit) {
    val footer = if (draft.isReorderLinkValid) {
        "Count how many you have left, like boxes of lenses or spare filters, and get a nudge to reorder. Add a link to reorder in one tap."
    } else {
        "That doesn't look like a web address. Try something like amazon.com/your-item."
    }
    SectionCard(
        title = "Inventory",
        footer = footer,
        footerColor = if (draft.isReorderLinkValid) Color.Unspecified else MaterialTheme.colorScheme.error,
    ) {
        SwitchRow("Track spares", draft.tracksSpares) { on -> onChange { it.copy(tracksSpares = on) } }
        if (draft.tracksSpares) {
            StepperRow(
                label = "Spares on hand: ${draft.spareCount}",
                value = draft.spareCount,
                range = 0..999,
                onValueChange = { count -> onChange { it.copy(spareCount = count) } },
                testTag = "spares",
            )
            StepperRow(
                label = "Warn at ${draft.lowStockThreshold} or fewer",
                value = draft.lowStockThreshold,
                range = 0..50,
                onValueChange = { count -> onChange { it.copy(lowStockThreshold = count) } },
                testTag = "low stock",
            )
        }
        OutlinedTextField(
            value = draft.reorderLink,
            onValueChange = { link -> onChange { it.copy(reorderLink = link) } },
            label = { Text("Reorder link (optional)") },
            singleLine = true,
            isError = !draft.isReorderLinkValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("reorder-field"),
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
