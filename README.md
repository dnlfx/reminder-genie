# Reminder Genie

An iPhone app for the things you replace or redo on a schedule: contact lenses, toothbrushes, water filters, furnace filters, razor blades, medication refills, and anything else you'd otherwise track in the Notes app.

Tell it when you started the current one and how often it needs replacing. It shows a countdown, nudges you with a notification before and on the due day, and lets you log the replacement straight from the notification.

## Features

- **Countdown list** grouped into Overdue, Due soon, Upcoming and Paused, with a progress ring per item, a "what needs attention" summary at the top, and search.
- **Quick actions everywhere**: swipe right to log a replacement (with Undo), swipe left to edit or delete, or long-press a row to snooze, pause, reorder, edit or delete.
- **Local notifications**: an optional heads-up N days before, a reminder on the due day at the time you choose, and daily follow-ups if it's overdue.
- **Act from the notification**: *Replaced it*, *Snooze 1 hour* or *Remind me tomorrow* without opening the app.
- **Templates** for common items (2-week and monthly lenses, lens case, toothbrush and brush heads, razor blades, HVAC filter, fridge and pitcher filters, smoke detector batteries, oil change, and more), or a fully custom item.
- **Replacement history** for every item, with a chart of how long each cycle actually lasted against the schedule, your average interval and on-time rate. Backdate a replacement if you forgot to log it.
- **Spares inventory**: count boxes of lenses or spare filters. One is used each time you log a replacement, and you get a "running low" nudge at your threshold.
- **Reorder link** per item, so restocking is one tap from the item, the row's menu or the low-stock warning.
- **Siri and Shortcuts**: "Log a replacement in Reminder Genie", "Mark contact lenses replaced in Reminder Genie" or "What's due in Reminder Genie", no setup needed.
- **Snooze**, **pause**, per-item reminder time, notes (brand, prescription, where to buy), icons and colors.
- **App badge** shows how many items are due today or overdue, and stays current while the app is closed, and the list warns you if notifications are turned off. Settings can send a test notification.
- Everything stays on the device (SwiftData). No account, no network.

## Requirements

- Xcode 26 or later (Swift 6 language mode; the Undo toast uses Liquid Glass on iOS 26)
- iOS 17 or later

## Build and run

1. Clone the repo and open `ReminderGenie.xcodeproj` in Xcode.
2. Select the **ReminderGenie** target, open **Signing & Capabilities**, and pick your Team. Change the bundle identifier (`com.dnlfx.ReminderGenie`) if Xcode says it's taken.
3. Choose an iPhone simulator or your own iPhone and press **Run** (⌘R).
4. Add an item and allow notifications when asked.

To try a reminder quickly, add a custom item with a 1-day interval, set "Last replaced" to yesterday and the reminder time a couple of minutes from now, then lock the device or background the app.

Run the tests with **Product › Test** (⌘U), on a simulator or your connected iPhone:

- `ReminderGenieTests` (unit tests, a few seconds) cover the date math, each item's reminder plan, the whole-app schedule (soonest first, the 60-reminder cap, badge counts, pausing), replacing and spares, backdating, history rewinds, the editor's validation, and that every template and icon exists in SF Symbols.
- `ReminderGenieUITests` (about three minutes) drive the real app: adding from a template, the editor's name and link validation, swipe to replace and Undo, replacing from the detail screen, pausing and resuming, Snooze only once an item is nearly due, rewinding the countdown from history, deleting with confirmation, and a real reminder handled with **Replaced it** from the notification.

The UI tests launch the app with `-ui-testing`, which swaps in an empty in-memory database with sample data (`Services/UITestSupport.swift`, debug builds only), so they never touch your real items. To run only the unit tests, use the diamond next to `ScheduleTests` or ⌃⌥⌘U on that file.

If you prefer [XcodeGen](https://github.com/yonaskolb/XcodeGen), `project.yml` regenerates an equivalent project with `xcodegen generate`.

## How it's organized

```
ReminderGenie/
  ReminderGenieApp.swift          App entry, shared SwiftData container
  PrivacyInfo.xcprivacy           Privacy manifest (no tracking, no data collected)
  Logic/Schedule.swift            Pure date math: due dates, status, reminder plan, history stats
  Models/TrackedItem.swift        SwiftData models: TrackedItem, ReplacementLog
  Models/ItemDraft.swift          Editable copy used by the editor
  Models/ItemTemplate.swift       Built-in templates and icon choices
  Models/Schema.swift             Versioned schema and migration plan
  Services/NotificationManager.swift  Scheduling, notification actions, badge, undo
  Intents/ReminderGenieIntents.swift  Siri and Shortcuts (App Intents)
  Views/                          List, detail, editor, add-from-template, settings
ReminderGenieTests/               Swift Testing unit tests for Logic/
ReminderGenieUITests/             XCUITest smoke tests that drive the app
```

## Notes

- iOS keeps at most 64 pending local notifications per app. Each item uses up to five (heads-up, due, three follow-ups). The app plans all of them together and schedules the soonest 60, leaving room for the low-stock nudge and the test notification, and rebuilds them every time it opens or an item changes.
- Each reminder carries the badge count for the moment it's delivered, so the badge updates without opening the app.
- Errors that would otherwise be silent (saving, scheduling) are logged with `os.Logger` under the app's bundle identifier; filter Console.app by it when chasing a bug.
- The database uses a versioned schema (`Models/Schema.swift`). When a model changes, add a new schema version and migration stage there rather than editing the models in place. If the database ever fails to open, the app shows an explanation instead of crashing, and leaves the file alone.
- `Design/AppIcon.icon` is the layered Icon Composer source for the iOS 26 icon.
- See [LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md) for what's left before App Store submission.
- Notifications are rebuilt when the app becomes active, so time zone changes and edits are picked up automatically.

## Ideas for next steps

- Home Screen and Lock Screen widgets showing what's due next
- iCloud sync across devices (SwiftData + CloudKit; the models already use CloudKit-friendly defaults)
- Count by days worn instead of calendar days (handy for daily-wear lenses you skip some days)
- Separate left/right eye tracking for lenses with different prescriptions
- Interactive widgets and Live Activities (needs an App Group, so a signing change)
- Localization with String Catalogs (plural rules are already centralized in `pluralized`)
