package com.dnlfx.remindergenie.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/** The unit an item's replacement interval is measured in. */
@Serializable
enum class IntervalUnit {
    @SerialName("days") DAYS,
    @SerialName("weeks") WEEKS,
    @SerialName("months") MONTHS,
    @SerialName("years") YEARS;

    fun label(value: Int): String {
        val singular = when (this) {
            DAYS -> "day"
            WEEKS -> "week"
            MONTHS -> "month"
            YEARS -> "year"
        }
        return if (value == 1) singular else singular + "s"
    }

    val displayName: String get() = label(2).replaceFirstChar { it.uppercase() }

    /** Sensible bounds for the editor's stepper, so nobody ends up with "every 365 years". */
    val allowedRange: IntRange
        get() = when (this) {
            DAYS -> 1..365
            WEEKS -> 1..52
            MONTHS -> 1..24
            YEARS -> 1..10
        }

    /** Adds whole units on the calendar. Months clamp to the end of a shorter month. */
    fun addTo(dateTime: ZonedDateTime, amount: Long): ZonedDateTime = when (this) {
        DAYS -> dateTime.plusDays(amount)
        WEEKS -> dateTime.plusWeeks(amount)
        MONTHS -> dateTime.plusMonths(amount)
        YEARS -> dateTime.plusYears(amount)
    }
}

/** "1 day", "3 days". Keeps pluralization in one place. */
fun pluralized(count: Int, singular: String, plural: String? = null): String =
    "$count ${if (count == 1) singular else plural ?: (singular + "s")}"

/** Where an item sits relative to its due date. Declared most urgent first. */
enum class ItemStatus { OVERDUE, DUE_TODAY, DUE_SOON, OK }

/** Pure date math for a recurring item, kept free of Android so it can be unit tested. */
data class Schedule(
    val startDate: Instant,
    val intervalValue: Int,
    val intervalUnit: IntervalUnit,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** The moment the current item is due to be replaced. */
    val dueDate: Instant
        get() = intervalUnit.addTo(startDate.atZone(zone), max(1, intervalValue).toLong()).toInstant()

    /** Fraction of the interval that has elapsed, clamped to 0..1. */
    fun progress(now: Instant): Double {
        val total = dueDate.toEpochMilli() - startDate.toEpochMilli()
        if (total <= 0) return 1.0
        return min(1.0, max(0.0, (now.toEpochMilli() - startDate.toEpochMilli()).toDouble() / total))
    }

    /** Whole calendar days until the due date. Negative when overdue, 0 on the due day. */
    fun daysRemaining(now: Instant): Int =
        ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), dueDate.atZone(zone).toLocalDate()).toInt()

    /** Due today or overdue: what the badge counts. */
    fun needsReplacing(now: Instant): Boolean = daysRemaining(now) <= 0

    fun status(now: Instant, dueSoonDays: Int): ItemStatus {
        val days = daysRemaining(now)
        return when {
            days < 0 -> ItemStatus.OVERDUE
            days == 0 -> ItemStatus.DUE_TODAY
            days <= dueSoonDays -> ItemStatus.DUE_SOON
            else -> ItemStatus.OK
        }
    }

    /** The due day at the user's preferred reminder time. */
    fun reminderDate(hour: Int, minute: Int): Instant =
        dueDate.atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone).toInstant()
}

/** One notification the app intends to show for an item. */
data class PlannedReminder(val kind: Kind, val fireDate: Instant) {
    sealed interface Kind {
        data object HeadsUp : Kind
        data object Due : Kind
        data class Overdue(val day: Int) : Kind
    }

    val identifierSuffix: String
        get() = when (kind) {
            Kind.HeadsUp -> "lead"
            Kind.Due -> "due"
            is Kind.Overdue -> "nag-${kind.day}"
        }

    companion object {
        fun kindFromSuffix(suffix: String): Kind? = when {
            suffix == "lead" -> Kind.HeadsUp
            suffix == "due" -> Kind.Due
            suffix.startsWith("nag-") -> suffix.removePrefix("nag-").toIntOrNull()?.let { Kind.Overdue(it) }
            else -> null
        }
    }
}

/** One item's reminder settings, copied out of the store so scheduling can be tested on its own. */
data class ReminderInput(
    val id: String,
    val schedule: Schedule,
    val hour: Int,
    val minute: Int,
    val leadDays: Int,
    val remindDailyWhenOverdue: Boolean,
    val snoozedUntil: Instant?,
    val isPaused: Boolean,
)

/** A reminder to schedule, with the badge count it should show when it's delivered. */
data class ScheduledReminder(val itemId: String, val reminder: PlannedReminder, val badge: Int)

object ReminderPlanner {
    /** How many daily follow-ups to send after the due date when an item is still not replaced. */
    const val OVERDUE_FOLLOW_UPS = 3

    /** Upper bound on alarms set at once. Android allows 500 per app; the schedule is rebuilt often. */
    const val PENDING_LIMIT = 200

    /** Every reminder suffix an item can have, so they can all be cancelled at once. */
    fun allIdentifierSuffixes(): List<String> = listOf("lead", "due") + (1..OVERDUE_FOLLOW_UPS).map { "nag-$it" }

    /** Works out which reminders should be pending for an item right now. */
    fun plan(
        schedule: Schedule,
        hour: Int,
        minute: Int,
        leadDays: Int,
        remindDailyWhenOverdue: Boolean,
        snoozedUntil: Instant?,
        now: Instant,
    ): List<PlannedReminder> {
        val zone = schedule.zone
        val result = mutableListOf<PlannedReminder>()

        val regular = schedule.reminderDate(hour, minute)
        val base: Instant
        if (snoozedUntil != null && snoozedUntil >= regular) {
            // Snoozed past the due reminder: the snooze becomes the due reminder and follow-ups
            // count from it, even after it has fired.
            base = snoozedUntil
        } else {
            base = regular
            if (snoozedUntil != null && snoozedUntil > now) {
                // Snoozed before the due day (say, "Remind me tomorrow" on a heads-up): the snooze
                // takes the heads-up's place and the due reminder stays where it was.
                result += PlannedReminder(PlannedReminder.Kind.HeadsUp, snoozedUntil)
            } else if (leadDays > 0) {
                val lead = base.atZone(zone).minusDays(leadDays.toLong()).toInstant()
                if (lead > now && lead > schedule.startDate) {
                    result += PlannedReminder(PlannedReminder.Kind.HeadsUp, lead)
                }
            }
        }

        if (base > now) {
            result += PlannedReminder(PlannedReminder.Kind.Due, base)
        }

        if (remindDailyWhenOverdue) {
            for (day in 1..OVERDUE_FOLLOW_UPS) {
                val nag = base.atZone(zone).plusDays(day.toLong()).toInstant()
                if (nag > now) result += PlannedReminder(PlannedReminder.Kind.Overdue(day), nag)
            }
        }
        return result
    }

    fun plan(input: ReminderInput, now: Instant): List<PlannedReminder> {
        if (input.isPaused) return emptyList()
        return plan(
            schedule = input.schedule,
            hour = input.hour,
            minute = input.minute,
            leadDays = input.leadDays,
            remindDailyWhenOverdue = input.remindDailyWhenOverdue,
            snoozedUntil = input.snoozedUntil,
            now = now,
        )
    }

    /**
     * Every item's reminders together, soonest first and capped at [limit], each carrying the
     * number of items that will need replacing when it's delivered.
     */
    fun schedule(inputs: List<ReminderInput>, now: Instant, limit: Int = PENDING_LIMIT): List<ScheduledReminder> =
        inputs
            .flatMap { input -> plan(input, now).map { input.id to it } }
            .sortedBy { it.second.fireDate }
            .take(limit)
            .map { (id, reminder) -> ScheduledReminder(id, reminder, badgeCount(inputs, reminder.fireDate)) }

    /** Items due today or overdue at a moment, ignoring paused ones. */
    fun badgeCount(inputs: List<ReminderInput>, at: Instant): Int =
        inputs.count { !it.isPaused && it.schedule.needsReplacing(at) }
}

/** Stats derived from an item's replacement history. */
object HistoryStats {
    /** The time between two consecutive replacements, compared with the item's schedule. */
    data class Gap(
        /** When the later replacement happened. */
        val replacedOn: Instant,
        val days: Int,
        /** How many days the schedule allowed for that cycle. */
        val targetDays: Int,
    ) {
        val isOnTime: Boolean get() = days <= targetDays
    }

    /** Gaps between consecutive replacements, oldest first. */
    fun gaps(
        dates: List<Instant>,
        intervalValue: Int,
        intervalUnit: IntervalUnit,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Gap> {
        val sorted = dates.sorted()
        if (sorted.size < 2) return emptyList()
        return sorted.zipWithNext { previous, next ->
            val start = previous.atZone(zone).toLocalDate()
            val startInstant = start.atStartOfDay(zone).toInstant()
            val due = Schedule(startInstant, intervalValue, intervalUnit, zone).dueDate.atZone(zone).toLocalDate()
            val target = ChronoUnit.DAYS.between(start, due).toInt()
            val days = ChronoUnit.DAYS.between(start, next.atZone(zone).toLocalDate()).toInt()
            Gap(replacedOn = next, days = days, targetDays = target)
        }
    }

    /** Share of cycles replaced on or before the due day, or null with no complete cycles. */
    fun onTimeRate(gaps: List<Gap>): Double? =
        if (gaps.isEmpty()) null else gaps.count { it.isOnTime }.toDouble() / gaps.size

    /** Average number of days between consecutive replacements, or null with fewer than two entries. */
    fun averageDaysBetween(dates: List<Instant>, zone: ZoneId = ZoneId.systemDefault()): Double? {
        val sorted = dates.sorted()
        if (sorted.size < 2) return null
        val total = sorted.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(a.atZone(zone).toLocalDate(), b.atZone(zone).toLocalDate())
        }.sum()
        return total.toDouble() / (sorted.size - 1)
    }
}
