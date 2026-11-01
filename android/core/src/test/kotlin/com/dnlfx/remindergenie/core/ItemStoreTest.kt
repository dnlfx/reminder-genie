package com.dnlfx.remindergenie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/** Saving, undo, history rewinds and backups, on in-memory storage. */
class ItemStoreTest {
    private var now: Instant = date(2026, 10, 20, 12)
    private val storage = InMemoryStorage()
    private val store = ItemStore(storage) { now }

    private fun addLenses(spares: Int = 2): TrackedItem = store.add(
        ItemDraft(name = "Lenses", startDate = now.minusSeconds(15 * 86_400), tracksSpares = true, spareCount = spares),
    )

    @Test fun changesAreSavedAndReloaded() {
        val item = addLenses()
        store.markReplaced(item.id, note = "New box")

        val reopened = ItemStore(InMemoryStorage(storage.text)) { now }
        val loaded = reopened.item(item.id)!!
        assertEquals("Lenses", loaded.name)
        assertEquals(now, loaded.startDate)
        assertEquals(1, loaded.spareCount)
        assertEquals(listOf("New box", "Started tracking"), loaded.sortedLogs.map { it.note })
    }

    @Test fun undoRestoresTheCycleSparesAndHistory() {
        val item = addLenses()
        store.snooze(item.id, 3_600_000)
        val snoozedUntil = store.item(item.id)!!.snoozedUntil
        val undo = store.markReplaced(item.id)!!
        assertEquals("Lenses", undo.itemName)

        store.undo(undo)
        val restored = store.item(item.id)!!
        assertEquals(item.startDate, restored.startDate)
        assertEquals(2, restored.spareCount)
        assertEquals(snoozedUntil, restored.snoozedUntil)
        assertEquals(1, restored.logs.size)
    }

    @Test fun undoAfterDeletingDoesNothing() {
        val item = addLenses()
        val undo = store.markReplaced(item.id)!!
        store.delete(item.id)
        store.undo(undo)
        assertTrue(store.all.isEmpty())
    }

    @Test fun deletingTheLatestLogRewindsTheCountdown() {
        val item = addLenses()
        store.markReplaced(item.id)
        val latest = store.item(item.id)!!.sortedLogs.first()
        store.deleteLog(item.id, latest.id)
        assertEquals(item.startDate, store.item(item.id)!!.startDate)
    }

    @Test fun pausingClearsTheSnooze() {
        val item = addLenses()
        store.snooze(item.id, 3_600_000)
        store.setPaused(item.id, true)
        val paused = store.item(item.id)!!
        assertTrue(paused.isPaused)
        assertNull(paused.snoozedUntil)
    }

    @Test fun changesAreReportedWithTheItemId() {
        val changed = mutableListOf<Set<String>>()
        store.onChange = { changed += it }
        val item = addLenses()
        store.setSpareCount(item.id, 5000)
        assertEquals(999, store.item(item.id)!!.spareCount)
        store.delete(item.id)
        assertEquals(listOf(setOf(item.id), setOf(item.id), setOf(item.id)), changed)
    }

    @Test fun unreadableFileIsLeftAlone() {
        val broken = InMemoryStorage("{ this is not json")
        val damaged = ItemStore(broken) { now }
        assertNotNull(damaged.loadError)
        damaged.add(ItemDraft(name = "Filter"))
        assertEquals("{ this is not json", broken.text)
        assertTrue(damaged.all.isEmpty())
    }

    @Test fun backupsRoundTripAndMergeById() {
        val lenses = addLenses()
        val backup = store.exportJson()

        store.update(lenses.id, ItemDraft.fromItem(lenses).copy(name = "Renamed"))
        store.add(ItemDraft(name = "Filter"))

        val result = store.importJson(backup)
        assertEquals(ImportResult(added = 0, updated = 1), result)
        assertEquals(listOf("Lenses", "Filter"), store.all.map { it.name })

        val fresh = ItemStore(InMemoryStorage()) { now }
        assertEquals(ImportResult(added = 1, updated = 0), fresh.importJson(backup))
        assertEquals(lenses, fresh.item(lenses.id))
    }

    @Test fun importRejectsOtherFiles() {
        try {
            store.importJson("""{"hello": "world", "items": 3}""")
            fail("Expected an ImportException")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("isn't a Reminder Genie backup"))
        }
        try {
            store.importJson("""{"version": 99, "items": []}""")
            fail("Expected an ImportException")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("newer version"))
        }
    }

    @Test fun importsAMinimalHandWrittenFile() {
        // Only the name and start date are required, so a backup written by another app (or by hand) works.
        val result = store.importJson(
            """
            {"items": [{"id": "A1", "name": "Toothbrush", "startDate": "2026-09-01T16:00:00Z",
                        "intervalValue": 3, "intervalUnit": "months", "color": "teal", "someFutureField": true}]}
            """.trimIndent(),
        )
        assertEquals(1, result.added)
        val toothbrush = store.item("A1")!!
        assertEquals(IntervalUnit.MONTHS, toothbrush.intervalUnit)
        assertEquals(ItemColor.TEAL, toothbrush.color)
        assertEquals(Instant.parse("2026-09-01T16:00:00Z"), toothbrush.createdAt)
    }
}
