package com.dnlfx.remindergenie.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dnlfx.remindergenie.ItemActions
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.ItemStatus
import com.dnlfx.remindergenie.core.ItemTemplate
import com.dnlfx.remindergenie.core.TrackedItem
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.ui.NotificationAccess
import com.dnlfx.remindergenie.ui.UiEnv
import com.dnlfx.remindergenie.ui.components.IconBadge
import com.dnlfx.remindergenie.ui.components.ItemIcons
import com.dnlfx.remindergenie.ui.components.NotificationsOffBanner
import com.dnlfx.remindergenie.ui.components.ProgressRing
import com.dnlfx.remindergenie.ui.components.StatusPill
import com.dnlfx.remindergenie.ui.components.SummaryCard
import com.dnlfx.remindergenie.ui.theme.StatusColors
import com.dnlfx.remindergenie.ui.theme.color
import com.dnlfx.remindergenie.ui.theme.ringTint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

private enum class SectionKind(val title: String, val icon: ImageVector, val tint: Color?) {
    OVERDUE("Overdue", Icons.Outlined.ErrorOutline, StatusColors.Red),
    DUE_SOON("Due soon", Icons.Outlined.Schedule, StatusColors.Orange),
    UPCOMING("Upcoming", Icons.Outlined.CalendarMonth, null),
    PAUSED("Paused", Icons.Outlined.PauseCircle, null),
}

private data class ListSection(val kind: SectionKind, val items: List<TrackedItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    env: UiEnv,
    onOpenItem: (String) -> Unit,
    onAdd: () -> Unit,
    onAddFromTemplate: (ItemTemplate) -> Unit,
    onEdit: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val graph = env.graph
    val items by graph.store.items.collectAsStateWithLifecycle()
    val settings by graph.settings.values.collectAsStateWithLifecycle()
    val now = env.now
    val dueSoonDays = settings.dueSoonDays

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TrackedItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun replace(item: TrackedItem) {
        val undo = graph.actions.markReplaced(item.id) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(6_000) {
                snackbar.showSnackbar(
                    message = "${undo.itemName} replaced. Next due ${DueText.weekdayMonthDay(undo.nextDueDate)}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) graph.actions.undo(undo)
        }
    }

    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    Scaffold(
        topBar = {
            if (searching) {
                SearchBar(query = query, onQueryChange = { query = it }, onClose = { searching = false; query = "" })
            } else {
                TopAppBar(
                    title = { Text("Reminder Genie") },
                    actions = {
                        if (items.isNotEmpty()) {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search items") }
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                    },
                )
            }
        },
        floatingActionButton = {
            if (items.isNotEmpty()) {
                FloatingActionButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "Add item") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(Modifier.padding(padding), onAdd = onAdd, onAddFromTemplate = onAddFromTemplate)
            return@Scaffold
        }

        val sections = sections(items, query, now, dueSoonDays)
        val active = items.filter { !it.isPaused }
        fun status(item: TrackedItem) = item.schedule().status(now, dueSoonDays)
        val overdue = active.count { status(it) == ItemStatus.OVERDUE }
        val dueSoon = active.count { status(it) == ItemStatus.DUE_TODAY || status(it) == ItemStatus.DUE_SOON }
        val next = active.filter { status(it) != ItemStatus.OVERDUE }.minByOrNull { it.dueDate() }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("item-list"),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (query.isBlank()) {
                if (env.notificationAccess == NotificationAccess.OFF) {
                    item(key = "banner") { NotificationsOffBanner(onTurnOn = env::openNotificationSettings) }
                }
                item(key = "summary") {
                    SummaryCard(overdue, dueSoon, next?.name, next?.schedule()?.daysRemaining(now))
                }
            } else if (sections.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        "No results for “${query.trim()}”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }

            for (section in sections) {
                item(key = "header-${section.kind}") { SectionHeader(section) }
                items(section.items, key = { it.id }) { item ->
                    SwipeableRow(
                        item = item,
                        now = now,
                        dueSoonDays = dueSoonDays,
                        onClick = { onOpenItem(item.id) },
                        onReplace = { replace(item) },
                        onDelete = { pendingDelete = item },
                        onEdit = { onEdit(item.id) },
                        onSnooze = { millis -> graph.actions.snooze(item.id, millis) },
                        onTogglePause = { graph.actions.setPaused(item.id, !item.isPaused) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DeleteDialog(
            name = item.name,
            onConfirm = {
                graph.actions.delete(item.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun sections(items: List<TrackedItem>, query: String, now: Instant, dueSoonDays: Int): List<ListSection> {
    val q = query.trim()
    val visible = if (q.isEmpty()) items else items.filter { it.name.contains(q, ignoreCase = true) || it.notes.contains(q, ignoreCase = true) }
    val active = visible.filter { !it.isPaused }.sortedBy { it.dueDate() }
    val paused = visible.filter { it.isPaused }.sortedBy { it.name.lowercase() }
    fun matching(vararg statuses: ItemStatus) = active.filter { it.schedule().status(now, dueSoonDays) in statuses }
    return listOf(
        ListSection(SectionKind.OVERDUE, matching(ItemStatus.OVERDUE)),
        ListSection(SectionKind.DUE_SOON, matching(ItemStatus.DUE_TODAY, ItemStatus.DUE_SOON)),
        ListSection(SectionKind.UPCOMING, matching(ItemStatus.OK)),
        ListSection(SectionKind.PAUSED, paused),
    ).filter { it.items.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close search") }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search items") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search-field"),
            )
        },
    )
}

@Composable
private fun SectionHeader(section: ListSection) {
    val tint = section.kind.tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.kind.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(section.kind.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            "${section.items.size}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = pluralized(section.items.size, "item") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRow(
    item: TrackedItem,
    now: Instant,
    dueSoonDays: Int,
    onClick: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: (Long) -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var snoozeMenu by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val status = item.schedule().status(now, dueSoonDays)

    // Swiping right logs a replacement, swiping left asks to delete. Either way the row springs back.
    // Acting on the settled value (not in confirmValueChange) runs each action once per swipe.
    val swipe = rememberSwipeToDismissBoxState()
    val latestReplace by rememberUpdatedState(onReplace)
    val latestDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(swipe.currentValue) {
        when (swipe.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> latestReplace()
            SwipeToDismissBoxValue.EndToStart -> latestDelete()
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        swipe.reset()
    }

    Box(modifier) {
        SwipeToDismissBox(
            state = swipe,
            enableDismissFromStartToEnd = !item.isPaused,
            backgroundContent = {
                val direction = swipe.dismissDirection
                val (color, icon, label, alignment) = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Quad(StatusColors.Green, Icons.Outlined.CheckCircle, "Replaced", Alignment.CenterStart)
                    SwipeToDismissBoxValue.EndToStart -> Quad(StatusColors.Red, Icons.Outlined.Delete, "Delete", Alignment.CenterEnd)
                    SwipeToDismissBoxValue.Settled -> Quad(Color.Transparent, null, "", Alignment.Center)
                }
                Box(
                    Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                    contentAlignment = alignment,
                ) {
                    if (icon != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
        ) {
            ItemRow(
                item = item,
                now = now,
                status = status,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { snoozeMenu = false; menuOpen = true },
                        onLongClickLabel = "More actions",
                    )
                    .testTag("item-${item.name}"),
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (snoozeMenu) {
                SnoozeMenuItems(onSnooze = { onSnooze(it); menuOpen = false })
            } else {
                if (!item.isPaused) {
                    MenuItem("Mark as replaced", Icons.Outlined.CheckCircle) { menuOpen = false; onReplace() }
                    // Same rule as the detail screen: nothing to snooze until it's nearly due.
                    if (status != ItemStatus.OK) {
                        MenuItem("Snooze reminder…", Icons.Outlined.Snooze) { snoozeMenu = true }
                    }
                }
                MenuItem(
                    if (item.isPaused) "Resume reminders" else "Pause reminders",
                    if (item.isPaused) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle,
                ) { menuOpen = false; onTogglePause() }
                MenuItem("Edit", Icons.Outlined.Edit) { menuOpen = false; onEdit() }
                item.reorderUrl?.let { url ->
                    MenuItem("Reorder", Icons.Outlined.ShoppingCart) { menuOpen = false; uriHandler.openUri(url) }
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

private data class Quad(val color: Color, val icon: ImageVector?, val label: String, val alignment: Alignment)

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** The snooze choices, shared by the list's menu and the detail screen. */
@Composable
fun SnoozeMenuItems(onSnooze: (Long) -> Unit) {
    MenuItem("1 hour", Icons.Outlined.Schedule) { onSnooze(ItemActions.HOUR) }
    MenuItem("Tomorrow", Icons.Outlined.Snooze) { onSnooze(ItemActions.DAY) }
    MenuItem("3 days", Icons.Outlined.CalendarMonth) { onSnooze(3 * ItemActions.DAY) }
    MenuItem("1 week", Icons.Outlined.CalendarMonth) { onSnooze(7 * ItemActions.DAY) }
}

@Composable
fun ItemRow(item: TrackedItem, now: Instant, status: ItemStatus, modifier: Modifier = Modifier) {
    val schedule = item.schedule()
    val days = schedule.daysRemaining(now)
    val snoozedUntil = item.snoozedUntil
    val subtitle = when {
        item.isPaused -> "Reminders paused"
        snoozedUntil != null && item.isSnoozed(now) -> DueText.snoozed(snoozedUntil, now)
        else -> "${DueText.short(days)} · ${DueText.monthDay(schedule.dueDate)}"
    }
    val subtitleColor = when {
        item.isPaused || item.isSnoozed(now) -> MaterialTheme.colorScheme.onSurfaceVariant
        status == ItemStatus.OVERDUE -> StatusColors.Red
        status == ItemStatus.DUE_TODAY -> StatusColors.Orange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(
            progress = if (item.isPaused) 0.0 else schedule.progress(now),
            tint = ringTint(item.color, status, item.isPaused),
            icon = ItemIcons.forName(item.symbolName),
            size = 46.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
        }
        if (item.tracksSpares) {
            Spacer(Modifier.width(8.dp))
            val tint = when {
                item.spareCount == 0 -> StatusColors.Red
                item.isLowOnSpares -> StatusColors.Orange
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            StatusPill(
                text = if (item.spareCount == 0) "Out" else "${item.spareCount}",
                icon = Icons.Outlined.Inventory2,
                tint = tint,
                modifier = Modifier.semantics {
                    contentDescription = if (item.spareCount == 0) "No spares left" else "${pluralized(item.spareCount, "spare")} left"
                },
            )
        }
    }
}

@Composable
fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $name?") },
        text = { Text("Its history and reminders will be removed too. This can't be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit, onAddFromTemplate: (ItemTemplate) -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Nothing to track yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add the things you replace on a schedule, like contact lenses, filters or a toothbrush, and Reminder Genie will tell you when it's time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) { Text("Add your first item") }
        Spacer(Modifier.height(28.dp))
        Text(
            "POPULAR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
        )
        for (template in ItemTemplate.starters) {
            Card(
                onClick = { onAddFromTemplate(template) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("starter-${template.name}"),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(ItemIcons.forName(template.symbolName), template.color.color)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        Text(template.intervalDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
