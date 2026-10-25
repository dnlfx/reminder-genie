package com.dnlfx.remindergenie.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dnlfx.remindergenie.AppGraph
import com.dnlfx.remindergenie.R
import com.dnlfx.remindergenie.core.ItemStore
import com.dnlfx.remindergenie.core.PlannedReminder
import com.dnlfx.remindergenie.core.ReminderPlanner
import com.dnlfx.remindergenie.core.TrackedItem
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.ui.MainActivity
import java.time.Instant

/**
 * Schedules an alarm for every planned reminder and shows the notification when it fires, with
 * "Replaced it" and snooze buttons so items can be updated without opening the app.
 */
class Reminders(
    private val context: Context,
    private val store: ItemStore,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun createChannels() {
        notifications.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Reminders")
                    .setDescription("When something is due, a heads-up before, and follow-ups if it's overdue")
                    .setShowBadge(true)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_INVENTORY, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Running low")
                    .setDescription("When you're about to run out of spares")
                    .setShowBadge(false)
                    .build(),
            ),
        )
    }

    /** Whether reminders can actually appear (the permission on Android 13+, and the app's switch). */
    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return notifications.areNotificationsEnabled()
    }

    /** Whether alarms fire at the exact minute. Without it Android may deliver them a little late. */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun onItemsChanged(ids: Set<String>) {
        ids.forEach { notifications.cancel(notificationId(it)) }
        rebuild()
    }

    // Scheduling

    /**
     * Replaces every pending alarm in one pass, soonest first and within the cap. Called whenever
     * an item changes, the app opens, the device restarts or the clock changes.
     */
    @Synchronized
    fun rebuild(now: Instant = Instant.now()) {
        val planned = ReminderPlanner.schedule(store.all.map { it.reminderInput() }, now)
        val keys = LinkedHashSet<String>()
        for (scheduled in planned) {
            val key = alarmKey(scheduled.itemId, scheduled.reminder.identifierSuffix)
            keys += key
            setAlarm(alarmIntent(key, scheduled.itemId, scheduled.reminder.identifierSuffix), scheduled.reminder.fireDate)
        }
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        for (stale in previous - keys) cancelAlarm(stale)
        prefs.edit().putStringSet(KEY_SCHEDULED, keys).apply()
    }

    /** The next reminder for an item, for the detail screen. */
    fun nextReminderDate(item: TrackedItem, now: Instant = Instant.now()): Instant? =
        if (item.isPaused) null else ReminderPlanner.plan(item.reminderInput(), now).minOfOrNull { it.fireDate }

    private fun alarmKey(itemId: String, suffix: String) = "${Uri.encode(itemId)}/$suffix"

    private fun alarmIntent(key: String, itemId: String?, suffix: String?) =
        Intent(context, AlarmReceiver::class.java)
            .setData(Uri.parse("remindergenie://alarm/$key"))
            .putExtra(EXTRA_ITEM_ID, itemId)
            .putExtra(EXTRA_SUFFIX, suffix)

    private fun setAlarm(intent: Intent, at: Instant) {
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val millis = at.toEpochMilli()
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        } catch (e: SecurityException) {
            Log.e(AppGraph.TAG, "Couldn't set an exact alarm, falling back", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    private fun cancelAlarm(key: String) {
        val pending = PendingIntent.getBroadcast(
            context, 0, alarmIntent(key, null, null), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    // Showing

    /** Called by [AlarmReceiver]. The text is built from the item as it is now. */
    fun showReminder(itemId: String, suffix: String, firedAt: Instant = Instant.now()) {
        val item = store.item(itemId) ?: return
        if (item.isPaused) return
        val kind = PlannedReminder.kindFromSuffix(suffix) ?: return

        val (title, body) = when (kind) {
            PlannedReminder.Kind.HeadsUp -> {
                // Counted from now, since a snoozed heads-up fires later than planned.
                val days = item.schedule().daysRemaining(firedAt)
                val title = when {
                    days <= 0 -> "${item.name} is due today"
                    days == 1 -> "${item.name} is due tomorrow"
                    else -> "${item.name} is due in $days days"
                }
                val body = when {
                    item.tracksSpares && item.spareCount == 0 -> "You're out of spares, so now's a good time to reorder."
                    item.isLowOnSpares -> "Only ${pluralized(item.spareCount, "spare")} left. Worth reordering before the due day."
                    else -> "Just a heads-up so you're ready."
                }
                title to body
            }
            PlannedReminder.Kind.Due ->
                "Time to replace: ${item.name}" to
                    "It's been ${pluralized(item.intervalValue, item.intervalUnit.label(1))}. Tap Replaced it once you're done."
            is PlannedReminder.Kind.Overdue ->
                "${item.name} is overdue" to "Overdue by ${pluralized(kind.day, "day")}. Don't forget to swap it out."
        }

        val badge = ReminderPlanner.badgeCount(store.all.map { it.reminderInput() }, firedAt)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(badge)
            .setAutoCancel(true)
            .setContentIntent(openItemIntent(item.id))
            .addAction(0, "Replaced it", actionIntent(ActionReceiver.ACTION_REPLACED, item.id))
            .addAction(0, "Snooze 1 hour", actionIntent(ActionReceiver.ACTION_SNOOZE_HOUR, item.id))
            .addAction(0, "Remind me tomorrow", actionIntent(ActionReceiver.ACTION_SNOOZE_DAY, item.id))
            .build()
        post(notificationId(item.id), notification)
    }

    fun notifyLowStockIfNeeded(item: TrackedItem) {
        if (!item.isLowOnSpares) return
        val body = if (item.spareCount == 0) {
            "That was your last spare. Time to reorder."
        } else {
            "Only ${pluralized(item.spareCount, "spare")} left. Time to reorder?"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_INVENTORY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Running low: ${item.name}")
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openItemIntent(item.id))
        item.reorderUrl?.let { url ->
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            builder.addAction(
                0,
                "Reorder",
                PendingIntent.getActivity(context, lowStockId(item.id), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        post(lowStockId(item.id), builder.build())
    }

    fun cancelLowStock(itemId: String) = notifications.cancel(lowStockId(itemId))

    /** A sample reminder a few seconds from now, so people can check sounds and banners. */
    fun scheduleTestNotification() {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setData(Uri.parse("remindergenie://alarm/test"))
            .putExtra(EXTRA_TEST, true)
        setAlarm(intent, Instant.now().plusSeconds(5))
    }

    fun showTestNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder Genie is all set")
            .setContentText("This is what a reminder looks like. Real ones have buttons to log a replacement or snooze.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("This is what a reminder looks like. Real ones have buttons to log a replacement or snooze."),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, TEST_ID, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        post(TEST_ID, notification)
    }

    @SuppressLint("MissingPermission") // Checked by canPostNotifications().
    private fun post(id: Int, notification: Notification) {
        if (!canPostNotifications()) return
        notifications.notify(id, notification)
    }

    private fun openItemIntent(itemId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("remindergenie://item/${Uri.encode(itemId)}"))
            .putExtra(EXTRA_ITEM_ID, itemId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, notificationId(itemId), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun actionIntent(action: String, itemId: String): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("remindergenie://action/${Uri.encode(itemId)}"))
            .putExtra(EXTRA_ITEM_ID, itemId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_INVENTORY = "inventory"
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_SUFFIX = "suffix"
        const val EXTRA_TEST = "test"
        private const val KEY_SCHEDULED = "scheduled"
        private const val TEST_ID = 1

        /** One notification per item, so a new reminder replaces the last one. */
        fun notificationId(itemId: String) = itemId.hashCode()
        fun lowStockId(itemId: String) = "low-stock-$itemId".hashCode()
    }
}
