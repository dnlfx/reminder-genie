package com.dnlfx.remindergenie

import com.dnlfx.remindergenie.core.ItemStore
import com.dnlfx.remindergenie.core.ReplacementUndo
import com.dnlfx.remindergenie.reminders.Reminders
import java.time.Instant

/** Item changes that also touch notifications, shared by the UI and the notification actions. */
class ItemActions(private val store: ItemStore, private val reminders: Reminders) {
    /** Logs a replacement, nudging about spares if they're running low, and returns its undo. */
    fun markReplaced(id: String, date: Instant = Instant.now(), note: String = ""): ReplacementUndo? {
        val undo = store.markReplaced(id, date, note) ?: return null
        store.item(id)?.let { reminders.notifyLowStockIfNeeded(it) }
        return undo
    }

    fun undo(undo: ReplacementUndo) {
        store.undo(undo)
        reminders.cancelLowStock(undo.itemId)
    }

    fun snooze(id: String, forMillis: Long) = store.snooze(id, forMillis)

    fun setPaused(id: String, paused: Boolean) = store.setPaused(id, paused)

    fun delete(id: String) {
        store.delete(id)
        reminders.cancelLowStock(id)
    }

    companion object {
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
    }
}
