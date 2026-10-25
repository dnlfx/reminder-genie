package com.dnlfx.remindergenie.core

import java.time.Instant
import java.time.ZoneId

/** Editable copy of an item's settings, used by the editor so nothing is saved until the user taps Save. */
data class ItemDraft(
    val name: String = "",
    val symbolName: String = "clock",
    val color: ItemColor = ItemColor.BLUE,
    val intervalValue: Int = 14,
    val intervalUnit: IntervalUnit = IntervalUnit.DAYS,
    val startDate: Instant = Instant.now(),
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val leadDays: Int = 1,
    val remindDailyWhenOverdue: Boolean = true,
    val notes: String = "",
    val tracksSpares: Boolean = false,
    val spareCount: Int = 0,
    val lowStockThreshold: Int = 1,
    val reorderLink: String = "",
    val isPaused: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank() && intervalValue > 0 && isReorderLinkValid

    /** An empty link is fine; anything typed must look like a web address. */
    val isReorderLinkValid: Boolean get() = reorderLink.isBlank() || ReorderLink.url(reorderLink) != null

    fun previewSchedule(zone: ZoneId = ZoneId.systemDefault()) = Schedule(startDate, intervalValue, intervalUnit, zone)

    /** Keeps the interval inside the new unit's range when the unit changes. */
    fun withUnit(unit: IntervalUnit): ItemDraft =
        copy(intervalUnit = unit, intervalValue = intervalValue.coerceIn(unit.allowedRange))

    /** Copies the draft onto an existing item. Changing the start date clears any snooze. */
    fun applyTo(item: TrackedItem): TrackedItem = item.copy(
        name = name.trim(),
        symbolName = symbolName,
        color = color,
        intervalValue = intervalValue,
        intervalUnit = intervalUnit,
        startDate = startDate,
        snoozedUntil = if (item.startDate != startDate) null else item.snoozedUntil,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        leadDays = leadDays,
        remindDailyWhenOverdue = remindDailyWhenOverdue,
        notes = notes,
        tracksSpares = tracksSpares,
        spareCount = spareCount,
        lowStockThreshold = lowStockThreshold,
        reorderLink = reorderLink.trim(),
        isPaused = isPaused,
    )

    /** A new item. The first cycle counts as a replacement in the history. */
    fun toNewItem(id: String = newId(), now: Instant = Instant.now()): TrackedItem =
        applyTo(TrackedItem(id = id, name = name, startDate = startDate, createdAt = now))
            .copy(logs = listOf(ReplacementLog(date = startDate, note = "Started tracking")))

    companion object {
        fun fromTemplate(template: ItemTemplate, defaultHour: Int, defaultMinute: Int, now: Instant = Instant.now()) = ItemDraft(
            name = template.name,
            symbolName = template.symbolName,
            color = template.color,
            intervalValue = template.intervalValue,
            intervalUnit = template.intervalUnit,
            startDate = now,
            leadDays = template.leadDays,
            tracksSpares = template.tracksSpares,
            reminderHour = defaultHour,
            reminderMinute = defaultMinute,
        )

        fun fromItem(item: TrackedItem) = ItemDraft(
            name = item.name,
            symbolName = item.symbolName,
            color = item.color,
            intervalValue = item.intervalValue,
            intervalUnit = item.intervalUnit,
            startDate = item.startDate,
            reminderHour = item.reminderHour,
            reminderMinute = item.reminderMinute,
            leadDays = item.leadDays,
            remindDailyWhenOverdue = item.remindDailyWhenOverdue,
            notes = item.notes,
            tracksSpares = item.tracksSpares,
            spareCount = item.spareCount,
            lowStockThreshold = item.lowStockThreshold,
            reorderLink = item.reorderLink,
            isPaused = item.isPaused,
        )

        fun blank(name: String, defaultHour: Int, defaultMinute: Int, now: Instant = Instant.now()) =
            ItemDraft(name = name, startDate = now, reminderHour = defaultHour, reminderMinute = defaultMinute)
    }
}
