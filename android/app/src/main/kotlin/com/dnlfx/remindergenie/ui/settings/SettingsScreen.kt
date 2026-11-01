package com.dnlfx.remindergenie.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dnlfx.remindergenie.BuildConfig
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.ImportException
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.data.ThemeMode
import com.dnlfx.remindergenie.ui.NotificationAccess
import com.dnlfx.remindergenie.ui.UiEnv
import com.dnlfx.remindergenie.ui.components.ListRow
import com.dnlfx.remindergenie.ui.components.PickerRow
import com.dnlfx.remindergenie.ui.components.SectionCard
import com.dnlfx.remindergenie.ui.components.StepperRow
import com.dnlfx.remindergenie.ui.components.SwitchRow
import com.dnlfx.remindergenie.ui.components.TimePickerDialog
import com.dnlfx.remindergenie.ui.components.readableWidth
import com.dnlfx.remindergenie.ui.theme.StatusColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(env: UiEnv, onBack: () -> Unit) {
    val graph = env.graph
    val context = LocalContext.current
    val settings by graph.settings.values.collectAsStateWithLifecycle()
    val items by graph.store.items.collectAsStateWithLifecycle()
    var sentTest by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(graph.store.exportJson().toByteArray()) }
            say("Saved ${pluralized(items.size, "item")}")
        } catch (e: Exception) {
            say("Couldn't save the backup: ${e.message}")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val result = graph.store.importJson(text)
            say(
                when {
                    result.added + result.updated == 0 -> "That backup has no items"
                    result.updated == 0 -> "Added ${pluralized(result.added, "item")}"
                    else -> "Added ${result.added}, updated ${result.updated}"
                },
            )
        } catch (e: ImportException) {
            say(e.message ?: "Couldn't import that file")
        } catch (e: Exception) {
            say("Couldn't read that file: ${e.message}")
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp).readableWidth(),
        ) {
            SectionCard(
                title = "Notifications",
                footer = "Reminders are scheduled on this phone, so they work offline. Tap Replaced it or Snooze right on the notification.",
            ) {
                val (statusText, statusColor) = when (env.notificationAccess) {
                    NotificationAccess.ON -> "On" to StatusColors.Green
                    NotificationAccess.OFF -> "Off" to StatusColors.Red
                    NotificationAccess.NOT_ASKED -> "Not set up" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                ListRow(
                    title = "Notifications",
                    icon = Icons.Outlined.Notifications,
                    trailing = {
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                            modifier = Modifier.testTag("notification-status"),
                        )
                    },
                )
                when (env.notificationAccess) {
                    NotificationAccess.NOT_ASKED -> ListRow(
                        "Turn on notifications",
                        icon = Icons.Outlined.NotificationsActive,
                        iconTint = MaterialTheme.colorScheme.primary,
                        titleColor = MaterialTheme.colorScheme.primary,
                        onClick = { env.requestNotifications() },
                    )
                    NotificationAccess.OFF -> ListRow(
                        "Open notification settings",
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        iconTint = MaterialTheme.colorScheme.primary,
                        titleColor = MaterialTheme.colorScheme.primary,
                        onClick = { env.openNotificationSettings() },
                    )
                    NotificationAccess.ON -> ListRow(
                        if (sentTest) "Test sent" else "Send a test notification",
                        subtitle = if (sentTest) "It'll arrive in a few seconds." else "See how a reminder looks and sounds.",
                        icon = if (sentTest) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.Send,
                        iconTint = MaterialTheme.colorScheme.primary,
                        enabled = !sentTest,
                        onClick = {
                            graph.reminders.scheduleTestNotification()
                            sentTest = true
                        },
                    )
                }
                if (!env.exactAlarms && Build.VERSION.SDK_INT >= 31) {
                    ListRow(
                        "Allow exact alarms",
                        subtitle = "Reminders may arrive a few minutes late until you do.",
                        icon = Icons.Outlined.Alarm,
                        iconTint = StatusColors.Orange,
                        onClick = { env.openExactAlarmSettings() },
                    )
                }
            }

            SectionCard(title = "Reminders", footer = "The default time is used for new items. Each item can have its own time.") {
                PickerRow(
                    label = "Default reminder time",
                    value = DueText.timeOfDay(settings.defaultHour, settings.defaultMinute),
                    onClick = { pickingTime = true },
                    icon = Icons.Outlined.Schedule,
                )
                StepperRow(
                    label = "\"Due soon\" within ${pluralized(settings.dueSoonDays, "day")}",
                    value = settings.dueSoonDays,
                    range = 1..14,
                    onValueChange = graph.settings::setDueSoonDays,
                    testTag = "due soon",
                )
            }

            SectionCard(title = "Appearance") {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { graph.settings.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    SwitchRow(
                        "Use wallpaper colors",
                        checked = settings.dynamicColor,
                        icon = Icons.Outlined.Palette,
                        subtitle = "Match the colors of your phone's wallpaper instead of Reminder Genie's.",
                    ) { graph.settings.setDynamicColor(it) }
                }
            }

            SectionCard(
                title = "Backup",
                footer = "Save your items and their history to a file, for a new phone or safekeeping. " +
                    "Importing adds the file's items and updates ones you already have; nothing is deleted.",
            ) {
                ListRow(
                    "Export items",
                    subtitle = if (items.isEmpty()) "Nothing to export yet" else "Save ${pluralized(items.size, "item")} to a file",
                    icon = Icons.Outlined.FileUpload,
                    enabled = items.isNotEmpty(),
                    onClick = { exporter.launch("reminder-genie-backup-${LocalDate.now()}.json") },
                )
                ListRow(
                    "Import items",
                    subtitle = "Add items from a backup file",
                    icon = Icons.Outlined.FileDownload,
                    onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }

            SectionCard(title = "About") {
                ListRow("Version", icon = Icons.Outlined.Info, trailing = {
                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                })
                ListRow(
                    "Private by design",
                    subtitle = "Your data stays on this phone. No account, no tracking.",
                    icon = Icons.Outlined.Lock,
                )
            }
        }
    }

    if (pickingTime) {
        TimePickerDialog(
            hour = settings.defaultHour,
            minute = settings.defaultMinute,
            onDismiss = { pickingTime = false },
            onPick = { hour, minute -> graph.settings.setDefaultTime(hour, minute) },
        )
    }
}
