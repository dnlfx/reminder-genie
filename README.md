# Reminder Genie

An Android app for the things you replace or redo on a schedule: contact lenses, toothbrushes, water filters, furnace filters, razor blades, medication refills, and anything else you'd otherwise track in a notes app.

Tell it when you started the current one and how often it needs replacing. It shows a countdown, reminds you before and on the due day, and lets you log the replacement straight from the notification.

## Install

Every merge to `main` publishes a signed APK as a [GitHub release](https://github.com/dnlfx/reminder-genie/releases). On your phone, open the [latest release](https://github.com/dnlfx/reminder-genie/releases/latest), tap **ReminderGenie.apk**, and allow your browser to install unknown apps when Android asks. Sideloaded apps don't expire, and a newer APK installs over the old one and keeps your items.

## Features

- **Countdown list** grouped into Overdue, Due soon, Upcoming and Paused, with a progress ring per item, a summary of what needs attention, and search.
- **Quick actions**: swipe right to log a replacement (with Undo), swipe left to delete, or long-press a row to snooze, pause, reorder, edit or delete.
- **Reminders** on the minute: an optional heads-up N days before, the due day at the time you choose, and daily follow-ups if it's overdue. *Replaced it*, *Snooze 1 hour* and *Remind me tomorrow* work right from the notification.
- **Templates** for common items, or a custom item.
- **History** per item, with a chart of how long each cycle lasted against the schedule. Backdate a replacement you forgot to log.
- **Spares** with a "running low" notification and a per-item reorder link.
- **Backup** to a JSON file, to move to a new phone.
- Everything stays on the phone. No account, no network.

## Code

The app lives in [`android/`](android). See [android/README.md](android/README.md) for building, testing, the signing key setup and how the code is organized.

The original SwiftUI iPhone app was removed after the Android version replaced it. It's still in the git history before this change if you ever need it.
