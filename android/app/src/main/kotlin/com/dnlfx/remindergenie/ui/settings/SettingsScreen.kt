package com.dnlfx.remindergenie.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dnlfx.remindergenie.BuildConfig
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.ImportException
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.ui.NotificationAccess
import com.dnlfx.remindergenie.ui.UiEnv
import com.dnlfx.remindergenie.ui.components.LabeledRow
import com.dnlfx.remindergenie.ui.components.PickerRow
import com.dnlfx.remindergenie.ui.components.SectionCard
import com.dnlfx.remindergenie.ui.components.StepperRow
import com.dnlfx.remindergenie.ui.components.TimePickerDialog
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
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Notifications", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(statusText, color = statusColor, modifier = Modifier.testTag("notification-status"))
                }
                when (env.notificationAccess) {
                    NotificationAccess.NOT_ASKED -> ActionRow("Turn on notifications", Icons.Outlined.Notifications) { env.requestNotifications() }
                    NotificationAccess.OFF -> ActionRow("Open notification settings", Icons.Outlined.Notifications) { env.openNotificationSettings() }
                    NotificationAccess.ON -> ActionRow(
                        if (sentTest) "Test sent. Check in a few seconds" else "Send a test notification",
                        if (sentTest) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.Send,
                        enabled = !sentTest,
                    ) {
                        graph.reminders.scheduleTestNotification()
                        sentTest = true
                    }
                }
                if (!env.exactAlarms && Build.VERSION.SDK_INT >= 31) {
                    Text(
                        "Reminders may arrive a few minutes late because exact alarms are off.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusColors.Orange,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    ActionRow("Allow exact alarms", Icons.Outlined.Schedule) { env.openExactAlarmSettings() }
                }
            }

            SectionCard(title = "Defaults", footer = "The default time is used for new items. Each item can have its own time.") {
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

            SectionCard(
                title = "Backup",
                footer = "Save your items and their history to a file, for a new phone or safekeeping. " +
                    "Importing adds the file's items and updates ones you already have; nothing is deleted.",
            ) {
                ActionRow("Export items", Icons.Outlined.FileUpload, enabled = items.isNotEmpty()) {
                    exporter.launch("reminder-genie-backup-${LocalDate.now()}.json")
                }
                ActionRow("Import items", Icons.Outlined.FileDownload) {
                    importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }

            SectionCard(title = "About") {
                LabeledRow("Version", BuildConfig.VERSION_NAME)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Your data stays on this phone. No account, no tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

@Composable
private fun ActionRow(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}
