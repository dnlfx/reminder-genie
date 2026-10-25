package com.dnlfx.remindergenie.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
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
            ReminderGenieTheme {
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
