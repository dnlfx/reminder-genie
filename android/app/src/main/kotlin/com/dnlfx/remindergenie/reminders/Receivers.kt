package com.dnlfx.remindergenie.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dnlfx.remindergenie.ItemActions
import com.dnlfx.remindergenie.graph

/** Fires at each planned reminder time and shows the notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = context.graph.reminders
        if (intent.getBooleanExtra(Reminders.EXTRA_TEST, false)) {
            reminders.showTestNotification()
            return
        }
        val itemId = intent.getStringExtra(Reminders.EXTRA_ITEM_ID) ?: return
        val suffix = intent.getStringExtra(Reminders.EXTRA_SUFFIX) ?: return
        reminders.showReminder(itemId, suffix)
        // Moves the window of scheduled alarms forward.
        reminders.rebuild()
    }
}

/** The buttons on a reminder: "Replaced it", "Snooze 1 hour" and "Remind me tomorrow". */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(Reminders.EXTRA_ITEM_ID) ?: return
        val actions = context.graph.actions
        // Each change clears the item's notification and reschedules (see AppGraph).
        when (intent.action) {
            ACTION_REPLACED -> actions.markReplaced(itemId)
            ACTION_SNOOZE_HOUR -> actions.snooze(itemId, ItemActions.HOUR)
            ACTION_SNOOZE_DAY -> actions.snooze(itemId, ItemActions.DAY)
        }
    }

    companion object {
        const val ACTION_REPLACED = "com.dnlfx.remindergenie.REPLACED"
        const val ACTION_SNOOZE_HOUR = "com.dnlfx.remindergenie.SNOOZE_HOUR"
        const val ACTION_SNOOZE_DAY = "com.dnlfx.remindergenie.SNOOZE_DAY"
    }
}

/** Alarms are cleared by a restart, an app update or a clock change; this puts them back. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> context.graph.reminders.rebuild()
        }
    }
}
