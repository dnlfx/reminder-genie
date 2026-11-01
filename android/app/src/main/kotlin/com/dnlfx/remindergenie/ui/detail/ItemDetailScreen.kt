package com.dnlfx.remindergenie.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.HistoryStats
import com.dnlfx.remindergenie.core.ItemStatus
import com.dnlfx.remindergenie.core.ReorderLink
import com.dnlfx.remindergenie.core.TrackedItem
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.ui.UiEnv
import com.dnlfx.remindergenie.ui.components.ItemIcons
import com.dnlfx.remindergenie.ui.components.LabeledRow
import com.dnlfx.remindergenie.ui.components.ProgressRing
import com.dnlfx.remindergenie.ui.components.SectionCard
import com.dnlfx.remindergenie.ui.components.StatusPill
import com.dnlfx.remindergenie.ui.components.StepperRow
import com.dnlfx.remindergenie.ui.list.DeleteDialog
import com.dnlfx.remindergenie.ui.list.SnoozeMenuItems
import com.dnlfx.remindergenie.ui.theme.StatusColors
import com.dnlfx.remindergenie.ui.theme.color
import com.dnlfx.remindergenie.ui.theme.ringTint
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(env: UiEnv, itemId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val graph = env.graph
    val items by graph.store.items.collectAsStateWithLifecycle()
    val settings by graph.settings.values.collectAsStateWithLifecycle()
    val item = items.firstOrNull { it.id == itemId }
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val now = env.now
    var replacing by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = onEdit, modifier = Modifier.testTag("edit")) { Text("Edit") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
                .testTag("detail"),
        ) {
            val status = item.schedule().status(now, settings.dueSoonDays)
            Header(
                item = item,
                now = now,
                status = status,
                onReplace = { replacing = true },
                onSnooze = { graph.actions.snooze(item.id, it) },
                onTogglePause = { graph.actions.setPaused(item.id, !item.isPaused) },
            )
            ScheduleCard(item, nextReminder = graph.reminders.nextReminderDate(item, now))
            if (item.tracksSpares || item.reorderUrl != null) {
                InventoryCard(item, onSpareCount = { graph.store.setSpareCount(item.id, it) })
            }
            if (item.notes.isNotBlank()) {
                SectionCard(title = "Notes") {
                    Text(item.notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                }
            }
            HistoryCard(item, onDeleteLog = { graph.store.deleteLog(item.id, it) })
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { confirmingDelete = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("delete-item"),
            ) { Text("Delete item", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (replacing) {
        ReplaceSheet(
            item = item,
            onDismiss = { replacing = false },
            onSave = { date, note -> graph.actions.markReplaced(item.id, date, note) },
        )
    }
    if (confirmingDelete) {
        DeleteDialog(
            name = item.name,
            onConfirm = {
                confirmingDelete = false
                // Leave the screen first so it never shows a deleted item.
                onBack()
                graph.actions.delete(item.id)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun Header(
    item: TrackedItem,
    now: Instant,
    status: ItemStatus,
    onReplace: () -> Unit,
    onSnooze: (Long) -> Unit,
    onTogglePause: () -> Unit,
) {
    val schedule = item.schedule()
    val days = schedule.daysRemaining(now)
    val tint = ringTint(item.color, status, item.isPaused)
    val caption = when {
        item.isPaused -> "paused"
        days < 0 -> if (days == -1) "day overdue" else "days overdue"
        days == 0 -> "due"
        days == 1 -> "day left"
        else -> "days left"
    }
    val numberColor = if (status == ItemStatus.OVERDUE && !item.isPaused) StatusColors.Red else MaterialTheme.colorScheme.onSurface
    var snoozeMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val summary = DueText.short(days) + if (item.isPaused) ". Reminders paused." else ""
        Box(
            Modifier.testTag("countdown").clearAndSetSemantics { contentDescription = summary },
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(
                progress = if (item.isPaused) 0.0 else schedule.progress(now),
                tint = tint,
                size = 190.dp,
                lineWidth = 16.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(ItemIcons.forName(item.symbolName), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                if (days == 0) {
                    Text("Today", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = numberColor)
                } else {
                    Text("${abs(days)}", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = numberColor)
                }
                Text(caption, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Due ${DueText.fullDate(schedule.dueDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            val snoozedUntil = item.snoozedUntil
            if (item.isPaused) {
                StatusPill("Reminders paused", Icons.Outlined.PauseCircle, MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (snoozedUntil != null && item.isSnoozed(now)) {
                StatusPill(DueText.snoozed(snoozedUntil, now), Icons.Outlined.Snooze, StatusColors.Indigo)
            }
        }

        Button(
            onClick = onReplace,
            colors = ButtonDefaults.buttonColors(containerColor = item.color.color),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("mark-replaced"),
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Mark as replaced", style = MaterialTheme.typography.titleMedium)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Nothing to snooze until it's nearly due.
            if (!item.isPaused && status != ItemStatus.OK) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { snoozeMenu = true }, modifier = Modifier.fillMaxWidth().testTag("snooze")) {
                        Icon(Icons.Outlined.Snooze, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Snooze")
                    }
                    DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                        SnoozeMenuItems(onSnooze = { onSnooze(it); snoozeMenu = false })
                    }
                }
            }
            OutlinedButton(onClick = onTogglePause, modifier = Modifier.weight(1f).testTag("pause")) {
                Icon(if (item.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (item.isPaused) "Resume" else "Pause")
            }
        }
    }
}

@Composable
private fun ScheduleCard(item: TrackedItem, nextReminder: Instant?) {
    SectionCard(title = "Schedule") {
        LabeledRow("Repeats", item.intervalDescription)
        LabeledRow("Current one started", DueText.mediumDate(item.startDate))
        LabeledRow("Due", DueText.mediumDate(item.dueDate()))
        LabeledRow(
            "Next reminder",
            when {
                nextReminder != null -> DueText.reminder(nextReminder)
                item.isPaused -> "Paused"
                else -> "None scheduled"
            },
        )
        if (item.leadDays > 0) LabeledRow("Heads-up", "${pluralized(item.leadDays, "day")} before")
    }
}

@Composable
private fun InventoryCard(item: TrackedItem, onSpareCount: (Int) -> Unit) {
    val uriHandler = LocalUriHandler.current
    SectionCard(
        title = "Inventory",
        footer = if (item.tracksSpares) {
            "One spare is used each time you mark this replaced. You'll get a nudge at ${item.lowStockThreshold} or fewer."
        } else {
            null
        },
    ) {
        if (item.tracksSpares) {
            StepperRow(
                label = "Spares on hand: ${item.spareCount}",
                value = item.spareCount,
                range = 0..999,
                onValueChange = onSpareCount,
                testTag = "spares",
            )
            if (item.isLowOnSpares) {
                val tint = if (item.spareCount == 0) StatusColors.Red else StatusColors.Orange
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = tint)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (item.spareCount == 0) "You're out of spares. Time to reorder." else "Running low. Time to reorder?",
                        color = tint,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item.reorderUrl?.let { url ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(url) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("reorder"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Reorder", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(ReorderLink.host(url), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun HistoryCard(item: TrackedItem, onDeleteLog: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val logs = item.sortedLogs
    val gaps = HistoryStats.gaps(logs.map { it.date }, item.intervalValue, item.intervalUnit)
    SectionCard(
        title = "History",
        footer = if (logs.isEmpty()) {
            null
        } else {
            "Deleting the latest entry rewinds the countdown to the one before. To log one you forgot, tap Mark as replaced and change the date."
        },
    ) {
        if (logs.isEmpty()) {
            Text(
                "No replacements logged yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (gaps.isNotEmpty()) {
            StatsRow(logCount = logs.size, gaps = gaps)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        if (gaps.size >= 2) {
            HistoryChart(gaps = gaps, tint = item.color.color, modifier = Modifier.padding(16.dp))
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        logs.forEachIndexed { index, log ->
            val gap = logs.getOrNull(index + 1)?.let { older ->
                ChronoUnit.DAYS.between(older.date.atZone(zone).toLocalDate(), log.date.atZone(zone).toLocalDate()).toInt()
            }
            val date = DueText.dateTime(log.date)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("history-entry"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(date, style = MaterialTheme.typography.bodyLarge)
                    if (log.note.isNotBlank()) {
                        Text(log.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (gap != null) {
                    Text(pluralized(gap, "day"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDeleteLog(log.id) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete entry from $date", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(logCount: Int, gaps: List<HistoryStats.Gap>) {
    val average = gaps.sumOf { it.days }.toDouble() / gaps.size
    val averageText = if (average % 1.0 == 0.0) "${average.toInt()}" else String.format("%.1f", average)
    val onTime = ((HistoryStats.onTimeRate(gaps) ?: 0.0) * 100).toInt()
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Stat("$logCount", "Logged", Modifier.weight(1f))
        Stat(averageText, "Avg. days", Modifier.weight(1f))
        Stat("$onTime%", "On time", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
