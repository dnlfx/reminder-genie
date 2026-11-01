package com.dnlfx.remindergenie

import android.app.Application
import android.content.Context
import android.util.Log
import com.dnlfx.remindergenie.data.AppSettings
import com.dnlfx.remindergenie.data.AtomicFileStorage
import com.dnlfx.remindergenie.reminders.Reminders
import com.dnlfx.remindergenie.core.ItemStore
import java.io.File

class ReminderGenieApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** The app's shared objects, used by the UI, the notification actions and the alarm receivers. */
class AppGraph(context: Context) {
    val settings = AppSettings(context)
    val store = ItemStore(AtomicFileStorage(File(context.filesDir, "items.json")))
    val reminders = Reminders(context, store)
    val actions = ItemActions(store, reminders)

    init {
        store.loadError?.let { Log.e(TAG, "Couldn't open items.json: $it") }
        reminders.createChannels()
        // Any change to an item clears its delivered reminder (it describes the old state) and
        // rebuilds the alarms for every item in one pass.
        store.onChange = { ids -> reminders.onItemsChanged(ids) }
    }

    companion object {
        const val TAG = "ReminderGenie"
    }
}

val Context.graph: AppGraph get() = (applicationContext as ReminderGenieApp).graph
