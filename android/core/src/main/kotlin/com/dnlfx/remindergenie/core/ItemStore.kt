package com.dnlfx.remindergenie.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** Where the store keeps its file. The app writes atomically to private storage; tests use memory. */
interface ItemStorage {
    /** The saved text, or null when nothing has been saved yet. */
    fun read(): String?
    fun write(text: String)
}

class InMemoryStorage(var text: String? = null) : ItemStorage {
    override fun read(): String? = text
    override fun write(text: String) {
        this.text = text
    }
}

/** The file layout, shared by the app's own file and backups. */
@Serializable
data class StoreFile(
    val version: Int = CURRENT_VERSION,
    val items: List<TrackedItem> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

class ImportException(message: String) : Exception(message)

data class ImportResult(val added: Int, val updated: Int)

/**
 * Every tracked item, saved as one small JSON file after each change.
 *
 * All changes go through here so the reminder schedule can be rebuilt in one place: set
 * [onChange] to hear which items changed (deleted ones included).
 */
class ItemStore(
    private val storage: ItemStorage,
    private val clock: () -> Instant = Instant::now,
) {
    private val lock = Any()
    private val _items = MutableStateFlow<List<TrackedItem>>(emptyList())
    val items: StateFlow<List<TrackedItem>> = _items.asStateFlow()

    /**
     * Set when the saved file couldn't be read. The store then stays empty and never writes, so
     * the file is left untouched for recovery instead of being overwritten.
     */
    var loadError: String? = null
        private set

    var onChange: ((Set<String>) -> Unit)? = null

    init {
        load()
    }

    private fun load() {
        val text = try {
            storage.read()
        } catch (e: Exception) {
            loadError = e.message ?: e.toString()
            return
        }
        if (text.isNullOrBlank()) return
        try {
            _items.value = decode(text).items
        } catch (e: Exception) {
            loadError = e.message ?: e.toString()
        }
    }

    val all: List<TrackedItem> get() = _items.value

    fun item(id: String): TrackedItem? = _items.value.firstOrNull { it.id == id }

    private inline fun mutate(ids: Set<String>, transform: (List<TrackedItem>) -> List<TrackedItem>) {
        synchronized(lock) {
            if (loadError != null) return
            val updated = transform(_items.value)
            storage.write(encode(StoreFile(items = updated)))
            _items.value = updated
        }
        onChange?.invoke(ids)
    }

    private fun updateItem(id: String, transform: (TrackedItem) -> TrackedItem) =
        mutate(setOf(id)) { list -> list.map { if (it.id == id) transform(it) else it } }

    fun add(draft: ItemDraft): TrackedItem {
        val item = draft.toNewItem(now = clock())
        mutate(setOf(item.id)) { it + item }
        return item
    }

    fun update(id: String, draft: ItemDraft) = updateItem(id) { draft.applyTo(it) }

    fun delete(id: String) = mutate(setOf(id)) { list -> list.filter { it.id != id } }

    /** Logs a replacement and returns what's needed to take it back. */
    fun markReplaced(id: String, date: Instant = clock(), note: String = ""): ReplacementUndo? {
        val before = item(id) ?: return null
        val logId = newId()
        val after = before.markedReplaced(date, note, logId)
        updateItem(id) { after }
        return ReplacementUndo(
            itemId = id,
            logId = logId,
            itemName = before.name,
            nextDueDate = after.dueDate(),
            previousStartDate = before.startDate,
            previousSnoozedUntil = before.snoozedUntil,
            previousSpareCount = before.spareCount,
        )
    }

    /** Reverts a replacement logged moments ago: restores the cycle and spares and drops the log entry. */
    fun undo(undo: ReplacementUndo) {
        if (item(undo.itemId) == null) return
        updateItem(undo.itemId) { item ->
            item.copy(
                startDate = undo.previousStartDate,
                snoozedUntil = undo.previousSnoozedUntil,
                spareCount = undo.previousSpareCount,
                logs = item.logs.filter { it.id != undo.logId },
            )
        }
    }

    /** Removes a history entry, rewinding the countdown if it was the latest. */
    fun deleteLog(itemId: String, logId: String) = updateItem(itemId) { it.withoutLog(logId) }

    fun snooze(id: String, forMillis: Long) =
        updateItem(id) { it.copy(snoozedUntil = clock().plusMillis(forMillis)) }

    fun setPaused(id: String, paused: Boolean) =
        updateItem(id) { it.copy(isPaused = paused, snoozedUntil = if (paused) null else it.snoozedUntil) }

    fun setSpareCount(id: String, count: Int) = updateItem(id) { it.copy(spareCount = count.coerceIn(0, 999)) }

    // Backups

    fun exportJson(): String = encode(StoreFile(items = _items.value))

    /**
     * Adds the items in a backup. An item already here (same id) is replaced by the backup's
     * copy; everything else is left alone, so importing the same file twice is harmless.
     */
    fun importJson(text: String): ImportResult {
        val file = try {
            decode(text)
        } catch (e: Exception) {
            throw ImportException("That file isn't a Reminder Genie backup.")
        }
        if (file.version > StoreFile.CURRENT_VERSION) {
            throw ImportException("That backup is from a newer version of Reminder Genie. Update the app and try again.")
        }
        if (loadError != null) throw ImportException("Your items couldn't be opened, so nothing can be imported.")
        val incoming = file.items.associateBy { it.id }
        val existing = _items.value.map { it.id }.toSet()
        val updated = incoming.keys.count { it in existing }
        mutate(incoming.keys) { list ->
            list.map { incoming[it.id] ?: it } + incoming.values.filter { it.id !in existing }
        }
        return ImportResult(added = incoming.size - updated, updated = updated)
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            explicitNulls = false
        }

        fun encode(file: StoreFile): String = json.encodeToString(StoreFile.serializer(), file)
        fun decode(text: String): StoreFile = json.decodeFromString(StoreFile.serializer(), text)
    }
}
