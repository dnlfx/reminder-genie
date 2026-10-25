package com.dnlfx.remindergenie.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dnlfx.remindergenie.AppGraph
import com.dnlfx.remindergenie.graph
import com.dnlfx.remindergenie.ui.add.AddItemScreen
import com.dnlfx.remindergenie.ui.detail.ItemDetailScreen
import com.dnlfx.remindergenie.ui.editor.EditorMode
import com.dnlfx.remindergenie.ui.editor.ItemEditorScreen
import com.dnlfx.remindergenie.ui.list.ItemListScreen
import com.dnlfx.remindergenie.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import java.time.Instant

enum class NotificationAccess {
    ON,

    /** Android 13+ before the app has asked. */
    NOT_ASKED,
    OFF,
}

/** What every screen needs from the app: the shared objects, the clock and notification access. */
@Stable
class UiEnv(val graph: AppGraph, private val context: Context) {
    /** Refreshed every minute and whenever the app comes back, so countdowns roll over at midnight. */
    var now: Instant by mutableStateOf(Instant.now())
    var notificationAccess: NotificationAccess by mutableStateOf(NotificationAccess.ON)
        private set
    var exactAlarms: Boolean by mutableStateOf(true)
        private set

    internal var launchPermissionRequest: () -> Unit = {}

    fun refresh() {
        now = Instant.now()
        exactAlarms = graph.reminders.canScheduleExactAlarms()
        notificationAccess = when {
            graph.reminders.canPostNotifications() -> NotificationAccess.ON
            Build.VERSION.SDK_INT >= 33 && !graph.settings.current.askedForNotifications -> NotificationAccess.NOT_ASKED
            else -> NotificationAccess.OFF
        }
    }

    /** Shows Android's permission prompt the first time; after that, opens the system settings. */
    fun requestNotifications() {
        when (notificationAccess) {
            NotificationAccess.ON -> Unit
            NotificationAccess.NOT_ASKED -> {
                graph.settings.setAskedForNotifications()
                launchPermissionRequest()
            }
            NotificationAccess.OFF -> openNotificationSettings()
        }
    }

    /** Asks only if Android hasn't asked yet, so saving an item never jumps to settings. */
    fun requestNotificationsIfNotAsked() {
        if (notificationAccess == NotificationAccess.NOT_ASKED) requestNotifications()
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

object Routes {
    const val LIST = "list"
    const val ADD = "add"
    const val NEW = "new?template={template}&name={name}"
    const val EDIT = "edit/{id}"
    const val DETAIL = "detail/{id}"
    const val SETTINGS = "settings"

    fun new(template: String? = null, name: String? = null) =
        "new?template=${Uri.encode(template.orEmpty())}&name=${Uri.encode(name.orEmpty())}"

    fun edit(id: String) = "edit/${Uri.encode(id)}"
    fun detail(id: String) = "detail/${Uri.encode(id)}"
}

@Composable
fun AppRoot(itemToOpen: String?, onItemOpened: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val env = remember { UiEnv(graph, context.applicationContext) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        env.refresh()
        graph.reminders.rebuild()
    }
    env.launchPermissionRequest = {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        env.refresh()
        // Picks up time zone changes, edits made from notifications and anything missed while closed.
        graph.reminders.rebuild()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            env.now = Instant.now()
        }
    }

    val loadError = graph.store.loadError
    if (loadError != null) {
        StoreErrorScreen(loadError)
    } else {
        AppNavigation(env, itemToOpen, onItemOpened)
    }
}

@Composable
private fun AppNavigation(env: UiEnv, itemToOpen: String?, onItemOpened: () -> Unit) {
    val graph = env.graph
    val nav = rememberNavController()

    LaunchedEffect(itemToOpen) {
        val id = itemToOpen ?: return@LaunchedEffect
        if (graph.store.item(id) != null) {
            nav.navigate(Routes.detail(id)) {
                popUpTo(Routes.LIST)
                launchSingleTop = true
            }
        }
        onItemOpened()
    }

    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            ItemListScreen(
                env = env,
                onOpenItem = { nav.navigate(Routes.detail(it)) },
                onAdd = { nav.navigate(Routes.ADD) },
                onAddFromTemplate = { nav.navigate(Routes.new(template = it.name)) },
                onEdit = { nav.navigate(Routes.edit(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.ADD) {
            AddItemScreen(
                onBack = { nav.popBackStack() },
                onTemplate = { nav.navigate(Routes.new(template = it.name)) },
                onCustom = { nav.navigate(Routes.new(name = it)) },
            )
        }
        composable(
            Routes.NEW,
            arguments = listOf(
                navArgument("template") { type = NavType.StringType; defaultValue = "" },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            ItemEditorScreen(
                env = env,
                mode = EditorMode.Create(
                    templateName = entry.arguments?.getString("template").orEmpty(),
                    name = entry.arguments?.getString("name").orEmpty(),
                ),
                onClose = { nav.popBackStack() },
                onSaved = { nav.popBackStack(Routes.LIST, inclusive = false) },
            )
        }
        composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ItemEditorScreen(
                env = env,
                mode = EditorMode.Edit(id),
                onClose = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ItemDetailScreen(
                env = env,
                itemId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(env = env, onBack = { nav.popBackStack() })
        }
    }
}
