# Reminder Genie

An iPhone app for the things you replace or redo on a schedule: contact lenses, toothbrushes, water filters, furnace filters, razor blades, medication refills, and anything else you'd otherwise track in the Notes app.

Tell it when you started the current one and how often it needs replacing. It shows a countdown, nudges you with a notification before and on the due day, and lets you log the replacement straight from the notification.

## Features

- **Countdown list** grouped into Overdue, Due soon, Upcoming and Paused, with a progress ring per item.
- **Local notifications**: an optional heads-up N days before, a reminder on the due day at the time you choose, and daily follow-ups if it's overdue.
- **Act from the notification**: *Replaced it*, *Snooze 1 hour* or *Remind me tomorrow* without opening the app.
- **Templates** for common items (2-week and monthly lenses, lens case, toothbrush and brush heads, razor blades, HVAC filter, fridge and pitcher filters, smoke detector batteries, oil change, and more), or a fully custom item.
- **Replacement history** for every item, with the gap between replacements and your real average interval. Backdate a replacement if you forgot to log it.
- **Spares inventory**: count boxes of lenses or spare filters. One is used each time you log a replacement, and you get a "running low" nudge at your threshold.
- **Snooze**, **pause**, per-item reminder time, notes (brand, prescription, where to buy), icons and colors.
- **App badge** shows how many items are overdue.
- Everything stays on the device (SwiftData). No account, no network.

## Requirements

- Xcode 16 or later (the project uses Xcode 16's folder-synced groups)
- iOS 17 or later

## Build and run

1. Clone the repo and open `ReminderGenie.xcodeproj` in Xcode.
2. Select the **ReminderGenie** target, open **Signing & Capabilities**, and pick your Team. Change the bundle identifier (`com.dnlfx.ReminderGenie`) if Xcode says it's taken.
3. Choose an iPhone simulator or your own iPhone and press **Run** (⌘R).
4. Add an item and allow notifications when asked.

To try a reminder quickly, add a custom item with a 1-day interval, set "Last replaced" to yesterday and the reminder time a couple of minutes from now, then lock the device or background the app.

Run the unit tests with **Product › Test** (⌘U). They cover the date math and the reminder plan.

If you prefer [XcodeGen](https://github.com/yonaskolb/XcodeGen), `project.yml` regenerates an equivalent project with `xcodegen generate`.

## How it's organized

```
ReminderGenie/
  ReminderGenieApp.swift          App entry, SwiftData container
  Logic/Schedule.swift            Pure date math: due dates, status, reminder plan, history stats
  Models/TrackedItem.swift        SwiftData models: TrackedItem, ReplacementLog
  Models/ItemDraft.swift          Editable copy used by the editor
  Models/ItemTemplate.swift       Built-in templates and icon choices
  Services/NotificationManager.swift  Scheduling, notification actions, badge
  Views/                          List, detail, editor, add-from-template, settings
ReminderGenieTests/               Swift Testing unit tests for Logic/
```

## Notes

- iOS keeps at most 64 pending local notifications per app. Each item uses up to five (heads-up, due, three follow-ups), and the app rebuilds them every time it opens, so with many items the soonest ones are always the ones scheduled.
- Notifications are rebuilt when the app becomes active, so time zone changes and edits are picked up automatically.

## Ideas for next steps

- Home Screen and Lock Screen widgets showing what's due next
- iCloud sync across devices (SwiftData + CloudKit; the models already use CloudKit-friendly defaults)
- Count by days worn instead of calendar days (handy for daily-wear lenses you skip some days)
- Separate left/right eye tracking for lenses with different prescriptions
- Siri and Shortcuts: "I put in new contacts"
- Reorder link per item for one-tap restocking
