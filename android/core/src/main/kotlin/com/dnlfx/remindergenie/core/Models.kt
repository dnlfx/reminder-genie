package com.dnlfx.remindergenie.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Stores dates as ISO-8601 text ("2026-10-01T15:00:00Z"), readable in a backup file. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

fun newId(): String = UUID.randomUUID().toString()

/** The colors an item can be tagged with. Stored by name so the model stays UI-free. */
@Serializable
enum class ItemColor {
    @SerialName("blue") BLUE,
    @SerialName("teal") TEAL,
    @SerialName("green") GREEN,
    @SerialName("yellow") YELLOW,
    @SerialName("orange") ORANGE,
    @SerialName("red") RED,
    @SerialName("pink") PINK,
    @SerialName("purple") PURPLE,
    @SerialName("indigo") INDIGO,
    @SerialName("gray") GRAY;

    val displayName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/** One past replacement of an item. */
@Serializable
data class ReplacementLog(
    val id: String = newId(),
    @Serializable(with = InstantSerializer::class) val date: Instant,
    val note: String = "",
)

/** Something the user replaces or does on a schedule, such as contact lenses or a water filter. */
@Serializable
data class TrackedItem(
    val id: String = newId(),
    val name: String,
    /** Icon key, shared with the iOS app (SF Symbol names), mapped to Material icons on Android. */
    val symbolName: String = "clock",
    val color: ItemColor = ItemColor.BLUE,
    val intervalValue: Int = 14,
    val intervalUnit: IntervalUnit = IntervalUnit.DAYS,
    /** When the current one was started (put in, opened, last done). */
    @Serializable(with = InstantSerializer::class) val startDate: Instant,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    /** Send a heads-up this many days before the due date. 0 turns it off. */
    val leadDays: Int = 1,
    val remindDailyWhenOverdue: Boolean = true,
    val notes: String = "",
    val tracksSpares: Boolean = false,
    val spareCount: Int = 0,
    val lowStockThreshold: Int = 1,
    /** Where to buy more, as typed by the user. See [reorderUrl]. */
    val reorderLink: String = "",
    @Serializable(with = InstantSerializer::class) val snoozedUntil: Instant? = null,
    val isPaused: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = startDate,
    val logs: List<ReplacementLog> = emptyList(),
) {
    fun schedule(zone: ZoneId = ZoneId.systemDefault()) = Schedule(startDate, intervalValue, intervalUnit, zone)

    fun dueDate(zone: ZoneId = ZoneId.systemDefault()): Instant = schedule(zone).dueDate

    val intervalDescription: String get() = "Every $intervalValue ${intervalUnit.label(intervalValue)}"

    /** Newest first. */
    val sortedLogs: List<ReplacementLog> get() = logs.sortedByDescending { it.date }

    fun isSnoozed(now: Instant): Boolean = snoozedUntil?.let { it > now } ?: false

    val isLowOnSpares: Boolean get() = tracksSpares && spareCount <= lowStockThreshold

    /** The reorder link as a URL, adding https:// when the user typed a bare domain. */
    val reorderUrl: String? get() = ReorderLink.url(reorderLink)

    fun reminderInput(zone: ZoneId = ZoneId.systemDefault()) = ReminderInput(
        id = id,
        schedule = schedule(zone),
        hour = reminderHour,
        minute = reminderMinute,
        leadDays = leadDays,
        remindDailyWhenOverdue = remindDailyWhenOverdue,
        snoozedUntil = snoozedUntil,
        isPaused = isPaused,
    )

    /**
     * Records a replacement: starts a new cycle, logs it, clears any snooze and uses up a spare.
     * An entry older than the current cycle only fills in history; it doesn't rewind the countdown.
     */
    fun markedReplaced(date: Instant, note: String = "", logId: String = newId()): TrackedItem {
        val log = ReplacementLog(id = logId, date = date, note = note)
        if (date < startDate) return copy(logs = logs + log)
        return copy(
            startDate = date,
            snoozedUntil = null,
            spareCount = if (tracksSpares && spareCount > 0) spareCount - 1 else spareCount,
            logs = logs + log,
        )
    }

    /**
     * Removes a history entry. Removing the entry that started the current cycle rewinds the
     * countdown to the entry before it, so a mistaken log can be taken back after Undo has gone.
     */
    fun withoutLog(logId: String): TrackedItem {
        val log = logs.firstOrNull { it.id == logId } ?: return this
        val remaining = logs.filter { it.id != logId }
        val previous = remaining.maxByOrNull { it.date }
        return if (log.date == startDate && previous != null) {
            copy(logs = remaining, startDate = previous.date, snoozedUntil = null)
        } else {
            copy(logs = remaining)
        }
    }
}

object ReorderLink {
    /** A normalized http(s) link, or null when the text doesn't look like a web address. */
    fun url(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrEmpty()) return null
        return withScheme
    }

    fun host(url: String): String = try {
        URI(url).host.orEmpty().removePrefix("www.")
    } catch (e: Exception) {
        ""
    }
}

/** Everything needed to take back a replacement that was just logged. */
data class ReplacementUndo(
    val itemId: String,
    val logId: String,
    /** Copied so the confirmation can still be shown if the item is deleted meanwhile. */
    val itemName: String,
    val nextDueDate: Instant,
    val previousStartDate: Instant,
    val previousSnoozedUntil: Instant?,
    val previousSpareCount: Int,
    val id: String = newId(),
)
