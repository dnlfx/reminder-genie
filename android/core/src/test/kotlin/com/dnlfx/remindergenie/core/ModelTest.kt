package com.dnlfx.remindergenie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Items, their history, the editor draft and the built-in templates. */
class ModelTest {
    private val now: Instant = date(2026, 10, 20, 12)

    private fun daysAgo(days: Long): Instant = now.atZone(LA).minusDays(days).toInstant()

    private fun item(start: Instant, days: Int = 14, spares: Int? = null) = TrackedItem(
        name = "Lenses",
        symbolName = "eye",
        intervalValue = days,
        startDate = start,
        tracksSpares = spares != null,
        spareCount = spares ?: 0,
    )

    // Replacing

    @Test fun replacingStartsANewCycleAndUsesASpare() {
        val before = item(daysAgo(15), spares = 2).copy(snoozedUntil = now.plusSeconds(3600))
        val after = before.markedReplaced(now)
        assertEquals(now, after.startDate)
        assertEquals(1, after.spareCount)
        assertNull(after.snoozedUntil)
        assertEquals(1, after.logs.size)
    }

    @Test fun replacingWithNoSparesLeftStaysAtZero() {
        val after = item(daysAgo(15), spares = 0).markedReplaced(now)
        assertEquals(0, after.spareCount)
        assertTrue(after.isLowOnSpares)
    }

    @Test fun backdatedEntryOnlyFillsInHistory() {
        val start = daysAgo(3)
        val after = item(start, spares = 2).markedReplaced(daysAgo(20))
        assertEquals(start, after.startDate)
        assertEquals(2, after.spareCount)
        assertEquals(1, after.logs.size)
    }

    @Test fun lowOnSparesAtTheThreshold() {
        val base = item(now, spares = 2).copy(lowStockThreshold = 1)
        assertFalse(base.isLowOnSpares)
        assertTrue(base.copy(spareCount = 1).isLowOnSpares)
        assertFalse(base.copy(spareCount = 1, tracksSpares = false).isLowOnSpares)
    }

    // History

    @Test fun removingTheLatestEntryRewindsTheCountdown() {
        val replaced = item(daysAgo(30)).markedReplaced(daysAgo(30)).markedReplaced(daysAgo(2), logId = "latest")
        assertEquals(daysAgo(2), replaced.startDate)

        val rewound = replaced.withoutLog("latest")
        assertEquals(daysAgo(30), rewound.startDate)
        assertEquals(1, rewound.logs.size)
    }

    @Test fun removingAnOlderEntryLeavesTheCountdownAlone() {
        val replaced = item(daysAgo(30)).markedReplaced(daysAgo(30), logId = "older").markedReplaced(daysAgo(2))
        val after = replaced.withoutLog("older")
        assertEquals(daysAgo(2), after.startDate)
        assertEquals(1, after.logs.size)
    }

    @Test fun removingTheOnlyEntryKeepsTheCurrentCycle() {
        val start = daysAgo(5)
        val after = item(start).markedReplaced(start, logId = "only").withoutLog("only")
        assertEquals(start, after.startDate)
        assertTrue(after.logs.isEmpty())
    }

    // Editor draft

    @Test fun draftNeedsANameAndAValidLink() {
        var draft = ItemDraft()
        assertFalse(draft.isValid)
        draft = draft.copy(name = "   ")
        assertFalse(draft.isValid)
        draft = draft.copy(name = "Filter")
        assertTrue(draft.isValid)
        draft = draft.copy(reorderLink = "not a link")
        assertFalse(draft.isValid)
        draft = draft.copy(reorderLink = "amazon.com/filters")
        assertTrue(draft.isValid)
    }

    @Test fun draftRoundTripsThroughAnItem() {
        val original = item(daysAgo(4), spares = 3).copy(notes = "Acuvue, -2.25", reorderLink = "example.com", reminderHour = 20, reminderMinute = 30)
        val draft = ItemDraft.fromItem(original)
        assertEquals("Lenses", draft.name)
        assertEquals(3, draft.spareCount)
        assertEquals(20, draft.reminderHour)
        assertEquals(30, draft.reminderMinute)

        val edited = draft.copy(name = "  Daily lenses  ").applyTo(original)
        assertEquals("Daily lenses", edited.name)
        assertEquals("Acuvue, -2.25", edited.notes)
        assertEquals(original.id, edited.id)
    }

    @Test fun changingTheStartDateInTheEditorClearsTheSnooze() {
        val snoozed = item(daysAgo(20)).copy(snoozedUntil = now.plusSeconds(3600))
        assertNotNull("Saving without changing the date keeps the snooze", ItemDraft.fromItem(snoozed).applyTo(snoozed).snoozedUntil)
        assertNull(ItemDraft.fromItem(snoozed).copy(startDate = daysAgo(1)).applyTo(snoozed).snoozedUntil)
    }

    @Test fun changingTheUnitKeepsTheIntervalInRange() {
        val draft = ItemDraft(intervalValue = 30).withUnit(IntervalUnit.MONTHS)
        assertEquals(24, draft.intervalValue)
        assertEquals(IntervalUnit.MONTHS, draft.intervalUnit)
    }

    @Test fun templateDraftUsesTheDefaultReminderTime() {
        val template = ItemTemplate.all.first { it.name == "Toothbrush" }
        val draft = ItemDraft.fromTemplate(template, 7, 45)
        assertEquals("Toothbrush", draft.name)
        assertEquals(IntervalUnit.MONTHS, draft.intervalUnit)
        assertEquals(7, draft.reminderHour)
        assertEquals(45, draft.reminderMinute)
    }

    @Test fun newItemsStartWithAHistoryEntry() {
        val created = ItemDraft(name = "Filter", startDate = daysAgo(3)).toNewItem(now = now)
        assertEquals(listOf("Started tracking"), created.logs.map { it.note })
        assertEquals(daysAgo(3), created.logs.single().date)
        assertEquals(now, created.createdAt)
    }

    // Templates and icons

    @Test fun templatesAreUniqueAndSensible() {
        val names = ItemTemplate.all.map { it.name }
        assertEquals("Template names must be unique", names.size, names.toSet().size)
        for (template in ItemTemplate.all) {
            assertTrue(template.name, template.intervalValue in template.intervalUnit.allowedRange)
        }
        assertEquals("Every starter name should match a template", 4, ItemTemplate.starters.size)
    }

    @Test fun everyTemplateIconIsInThePicker() {
        val pickable = ItemTemplate.symbols.map { it.name }.toSet()
        val missing = ItemTemplate.all.map { it.symbolName }.filter { it !in pickable }
        assertTrue("Missing from the picker: $missing", missing.isEmpty())
    }

    @Test fun snoozeIsOnlyActiveUntilItEnds() {
        val snoozed = item(daysAgo(20)).copy(snoozedUntil = now.plus(Duration.ofHours(1)))
        assertTrue(snoozed.isSnoozed(now))
        assertFalse(snoozed.isSnoozed(now.plus(Duration.ofHours(2))))
    }
}
