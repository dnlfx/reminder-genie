package com.dnlfx.remindergenie.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.dnlfx.remindergenie.ui.components.BrandMark
import com.dnlfx.remindergenie.ui.components.IconBadge
import com.dnlfx.remindergenie.ui.components.ItemIcons
import com.dnlfx.remindergenie.ui.components.NotificationsOffBanner
import com.dnlfx.remindergenie.ui.components.ProgressRing
import com.dnlfx.remindergenie.ui.components.StatusPill
import com.dnlfx.remindergenie.ui.components.SummaryCard
import com.dnlfx.remindergenie.ui.components.readableWidth
import com.dnlfx.remindergenie.ui.components.segmentShape
import com.dnlfx.remindergenie.ui.theme.StatusColors
import com.dnlfx.remindergenie.ui.theme.color
import com.dnlfx.remindergenie.ui.theme.contentColorOn
import com.dnlfx.remindergenie.ui.theme.ringTint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

private enum class SectionKind(val title: String, val icon: ImageVector) {
    OVERDUE("Overdue", Icons.Outlined.ErrorOutline),
    DUE_SOON("Due soon", Icons.Outlined.Schedule),
    UPCOMING("Upcoming", Icons.Outlined.CalendarMonth),
    PAUSED("Paused", Icons.Outlined.PauseCircle),
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    /** Shows a message with Undo for a few seconds; [onUndo] runs if it's tapped. */
    fun offerUndo(message: String, onUndo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(6_000) {
                snackbar.showSnackbar(message = message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    fun replace(item: TrackedItem) {
        val undo = graph.actions.markReplaced(item.id) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        offerUndo("${undo.itemName} replaced. Next due ${DueText.weekdayMonthDay(undo.nextDueDate)}") { graph.actions.undo(undo) }
    }

    fun delete(item: TrackedItem) {
        val index = graph.store.all.indexOfFirst { it.id == item.id }
        if (index < 0) return
        graph.actions.delete(item.id)
        offerUndo("${item.name} deleted") { graph.actions.restore(item, index) }
    }

    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (searching) {
                SearchBar(query = query, onQueryChange = { query = it }, onClose = { searching = false; query = "" })
            } else {
                LargeTopAppBar(
                    title = { Text("Reminder Genie", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        if (items.isNotEmpty()) {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search items") }
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            if (items.isNotEmpty() && !searching) {
                ExtendedFloatingActionButton(
                    text = { Text("Add item") },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    onClick = onAdd,
                    expanded = fabExpanded,
                    modifier = Modifier.testTag("add-item"),
                )
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
        val upcoming = active.size - overdue - dueSoon
        val next = active.filter { status(it) != ItemStatus.OVERDUE }.minByOrNull { it.dueDate() }

        val showBanner = query.isBlank() && env.notificationAccess == NotificationAccess.OFF
        val showSummary = query.isBlank()
        // Where each section's header sits in the list, so the summary's counts can jump to it.
        val headerIndex = buildMap {
            var index = (if (showBanner) 1 else 0) + (if (showSummary) 1 else 0)
            for (section in sections) {
                put(section.kind, index)
                index += 1 + section.items.size
            }
        }
        fun jumpTo(kind: SectionKind) {
            val index = headerIndex[kind] ?: return
            scope.launch { listState.animateScrollToItem(index) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).readableWidth().testTag("item-list"),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            if (showBanner) {
                item(key = "banner") { NotificationsOffBanner(onTurnOn = env::openNotificationSettings) }
            }
            if (showSummary) {
                item(key = "summary") {
                    SummaryCard(
                        overdue = overdue,
                        dueSoon = dueSoon,
                        upcoming = upcoming,
                        nextName = next?.name,
                        nextDays = next?.schedule()?.daysRemaining(now),
                        onShowOverdue = { jumpTo(SectionKind.OVERDUE) },
                        onShowDueSoon = { jumpTo(SectionKind.DUE_SOON) },
                        onShowUpcoming = { jumpTo(SectionKind.UPCOMING) },
                    )
                }
            } else if (sections.isEmpty()) {
                item(key = "no-results") { NoResults(query.trim()) }
            }

            for (section in sections) {
                item(key = "header-${section.kind}") { SectionHeader(section) }
                itemsIndexed(section.items, key = { _, item -> item.id }) { index, item ->
                    SwipeableRow(
                        item = item,
                        now = now,
                        dueSoonDays = dueSoonDays,
                        shape = segmentShape(index, section.items.size),
                        onClick = { onOpenItem(item.id) },
                        onReplace = { replace(item) },
                        onDelete = { delete(item) },
                        onEdit = { onEdit(item.id) },
                        onSnooze = { millis -> graph.actions.snooze(item.id, millis) },
                        onTogglePause = { graph.actions.setPaused(item.id, !item.isPaused) },
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 1.dp),
                    )
                }
            }
        }
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
                placeholder = { Text("Search your items") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
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
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
            }
        },
    )
}

@Composable
private fun NoResults(query: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text("No results for “$query”", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "Try a different name, or a word from the item's notes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHeader(section: ListSection) {
    val tint = when (section.kind) {
        SectionKind.OVERDUE -> StatusColors.Red
        SectionKind.DUE_SOON -> StatusColors.Orange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { heading() }
            .testTag("section-${section.kind}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.kind.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(section.kind.title, style = MaterialTheme.typography.titleSmall, color = tint, modifier = Modifier.weight(1f))
        Text(
            "${section.items.size}",
            style = MaterialTheme.typography.labelLarge,
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
    shape: Shape,
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

    // Swiping right logs a replacement, swiping left deletes (with Undo). Either way the row springs
    // back. It takes a deliberate swipe, 40% of the row, so a scroll doesn't trigger it.
    // Acting on the settled value (not in confirmValueChange) runs each action once per swipe.
    // Plain remember, not rememberSwipeToDismissBoxState: that one is saved per list key, so an
    // item brought back by Undo would come back already swiped and delete itself again.
    // If the spring back is interrupted (the row can move to another section mid-animation), the
    // state would stay swiped and ignore the next swipe, so the row gets a fresh, settled one.
    val density = LocalDensity.current
    var swipeGeneration by remember(item.id) { mutableIntStateOf(0) }
    val swipe = remember(item.id, swipeGeneration) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
            positionalThreshold = { distance -> distance * 0.4f },
        )
    }
    val latestReplace by rememberUpdatedState(onReplace)
    val latestDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(swipe, swipe.currentValue) {
        when (swipe.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> latestReplace()
            SwipeToDismissBoxValue.EndToStart -> latestDelete()
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        try {
            swipe.reset()
        } finally {
            if (swipe.currentValue != SwipeToDismissBoxValue.Settled) swipeGeneration++
        }
    }

    Box(modifier) {
        SwipeToDismissBox(
            state = swipe,
            modifier = Modifier.clip(shape),
            enableDismissFromStartToEnd = !item.isPaused,
            backgroundContent = { SwipeBackground(swipe) },
        ) {
            ItemRow(
                item = item,
                now = now,
                status = status,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
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

/** What shows under a row as it's swiped: muted until the swipe would count, then the action's color. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(swipe: SwipeToDismissBoxState) {
    val direction = swipe.dismissDirection
    if (direction == SwipeToDismissBoxValue.Settled) return
    val replacing = direction == SwipeToDismissBoxValue.StartToEnd
    val armed = swipe.targetValue == direction
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val actionColor = if (replacing) StatusColors.Green else StatusColors.Red
    val background by animateColorAsState(
        if (armed) actionColor else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "swipe-background",
    )
    val foreground = if (armed) contentColorOn(actionColor) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconScale by animateFloatAsState(if (armed) 1.15f else 0.9f, label = "swipe-icon")
    Box(
        Modifier.fillMaxSize().background(background).padding(horizontal = 24.dp),
        contentAlignment = if (replacing) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (replacing) Icons.Outlined.CheckCircle else Icons.Outlined.Delete,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.scale(iconScale),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (replacing) "Replaced" else "Delete",
                color = foreground,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

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
        item.isPaused -> MaterialTheme.colorScheme.onSurfaceVariant
        item.isSnoozed(now) -> StatusColors.Indigo
        status == ItemStatus.OVERDUE -> StatusColors.Red
        status == ItemStatus.DUE_TODAY -> StatusColors.Orange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(
            progress = if (item.isPaused) 0.0 else schedule.progress(now),
            tint = ringTint(item.color, status, item.isPaused),
            icon = ItemIcons.forName(item.symbolName),
            size = 48.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text("Delete $name?", textAlign = TextAlign.Center) },
        text = { Text("Its history and reminders will be removed too.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit, onAddFromTemplate: (ItemTemplate) -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.readableWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(size = 96.dp)
            Spacer(Modifier.height(24.dp))
            Text("Nothing to track yet", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add the things you replace on a schedule, like contact lenses, filters or a toothbrush, and Reminder Genie will tell you when it's time.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Add your first item")
            }
            Spacer(Modifier.height(36.dp))
            Text(
                "Or start with a popular one",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
            )
            val starters = ItemTemplate.starters
            starters.forEachIndexed { index, template ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .clip(segmentShape(index, starters.size))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(onClickLabel = "Add ${template.name}") { onAddFromTemplate(template) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("starter-${template.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(ItemIcons.forName(template.symbolName), template.color.color)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        Text(template.intervalDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
