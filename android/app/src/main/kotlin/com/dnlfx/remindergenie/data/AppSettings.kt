package com.dnlfx.remindergenie.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide preferences. Each item can still have its own reminder time. */
class AppSettings(context: Context) {
    data class Values(
        val defaultHour: Int = 9,
        val defaultMinute: Int = 0,
        val dueSoonDays: Int = 3,
        /** Whether the app has shown Android's notification permission prompt yet. */
        val askedForNotifications: Boolean = false,
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _values = MutableStateFlow(read())
    val values: StateFlow<Values> = _values.asStateFlow()
    val current: Values get() = _values.value

    private fun read() = Values(
        defaultHour = prefs.getInt(DEFAULT_HOUR, 9),
        defaultMinute = prefs.getInt(DEFAULT_MINUTE, 0),
        dueSoonDays = prefs.getInt(DUE_SOON_DAYS, 3),
        askedForNotifications = prefs.getBoolean(ASKED_FOR_NOTIFICATIONS, false),
    )

    fun setDefaultTime(hour: Int, minute: Int) = edit { putInt(DEFAULT_HOUR, hour).putInt(DEFAULT_MINUTE, minute) }

    fun setDueSoonDays(days: Int) = edit { putInt(DUE_SOON_DAYS, days.coerceIn(1, 14)) }

    fun setAskedForNotifications() = edit { putBoolean(ASKED_FOR_NOTIFICATIONS, true) }

    private fun edit(block: SharedPreferences.Editor.() -> SharedPreferences.Editor) {
        prefs.edit().block().apply()
        _values.value = read()
    }

    private companion object {
        const val DEFAULT_HOUR = "defaultReminderHour"
        const val DEFAULT_MINUTE = "defaultReminderMinute"
        const val DUE_SOON_DAYS = "dueSoonDays"
        const val ASKED_FOR_NOTIFICATIONS = "askedForNotifications"
    }
}
