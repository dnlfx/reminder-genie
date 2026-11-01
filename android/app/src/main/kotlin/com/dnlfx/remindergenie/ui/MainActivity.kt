package com.dnlfx.remindergenie.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dnlfx.remindergenie.data.ThemeMode
import com.dnlfx.remindergenie.graph
import com.dnlfx.remindergenie.reminders.Reminders
import com.dnlfx.remindergenie.ui.theme.ReminderGenieTheme

class MainActivity : ComponentActivity() {
    /** Set when a notification is tapped; the app then opens that item. */
    private val itemToOpen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            itemToOpen.value = intent.getStringExtra(Reminders.EXTRA_ITEM_ID)
        }
        setContent {
            val settings by graph.settings.values.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keeps the status and navigation bar icons readable when the app's theme differs from the system's.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ReminderGenieTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AppRoot(
                    itemToOpen = itemToOpen.value,
                    onItemOpened = { itemToOpen.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Reminders.EXTRA_ITEM_ID)?.let { itemToOpen.value = it }
    }
}
