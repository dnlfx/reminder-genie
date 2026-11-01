package com.dnlfx.remindergenie.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The short phrases used in rows, notifications and the detail screen. */
object DueText {
    /** "Due in 3 days", "Due today", "Overdue by 2 days". */
    fun short(daysRemaining: Int): String = when {
        daysRemaining < 0 -> "Overdue by ${pluralized(-daysRemaining, "day")}"
        daysRemaining == 0 -> "Due today"
        daysRemaining == 1 -> "Due tomorrow"
        else -> "Due in $daysRemaining days"
    }

    fun time(date: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(date.atZone(zone))

    /** "Oct 15". */
    fun monthDay(date: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("MMM d", locale).format(date.atZone(zone))

    /** "Wed, Oct 15". */
    fun weekdayMonthDay(date: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(date.atZone(zone))

    /** "Oct 15, 2026". */
    fun mediumDate(date: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date.atZone(zone))

    /** "Wednesday, October 15, 2026". */
    fun fullDate(date: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date.atZone(zone))

    /** "Oct 15, 2026, 9:00 AM". */
    fun dateTime(date: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        "${mediumDate(date, zone, locale)}, ${time(date, zone, locale)}"

    /** "Snoozed until 3:00 PM", "Snoozed until tomorrow, 9:00 AM", "Snoozed until Wed, Oct 15". */
    fun snoozed(until: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val day = until.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when (day) {
            today -> "Snoozed until ${time(until, zone, locale)}"
            today.plusDays(1) -> "Snoozed until tomorrow, ${time(until, zone, locale)}"
            else -> "Snoozed until ${weekdayMonthDay(until, zone, locale)}"
        }
    }

    /** "Today at 9:00 AM", "Tomorrow at 9:00 AM", "Fri, Oct 24 at 9:00 AM". */
    fun reminder(date: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val day = date.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val time = time(date, zone, locale)
        return when (day) {
            today -> "Today at $time"
            today.plusDays(1) -> "Tomorrow at $time"
            else -> "${weekdayMonthDay(date, zone, locale)} at $time"
        }
    }

    /** "9:00 AM" for an hour and minute on any day. */
    fun timeOfDay(hour: Int, minute: Int, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(java.time.LocalTime.of(hour, minute))
}
