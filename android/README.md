# Reminder Genie for Android

The Android version of Reminder Genie, in Kotlin and Jetpack Compose (Material 3). It does what the iPhone app does: countdowns for the things you replace on a schedule, reminders you can act on from the notification, spares, history and templates.

## Install it on your phone (no Play Store, no expiry)

Every merge to `main` publishes a signed APK as a [GitHub release](../../releases). Pull requests publish a preview build to the `android-preview` release.

1. On your phone, open the [latest release](../../releases/latest) and tap **ReminderGenie.apk** under Assets.
2. Open the download. The first time, Android asks you to allow your browser to install unknown apps: allow it, go back, and tap **Install**.
3. Open Reminder Genie and allow notifications when it asks.

Updating is the same: install a newer APK over the old one and your items stay. Sideloaded apps don't expire, so there's nothing to renew.

### Signing key (one-time setup)

Android only installs an update over an existing app when both are signed with the same key, so CI signs every APK with one key kept in the repository's Actions secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Its password (the key uses the same one) |

Add them under **Settings › Secrets and variables › Actions › New repository secret**. Without them, CI still builds an installable APK with a temporary key and says so in the release notes, but you'd have to uninstall it before installing a properly signed build.

Keep a copy of the keystore and password somewhere safe. If they're lost, future builds can't update the installed app; you'd export a backup, uninstall, install the new build and import the backup.

## Features

- **Countdown list** grouped into Overdue, Due soon, Upcoming and Paused, with a progress ring per item, a summary of what needs attention, and search.
- **Quick actions**: swipe right to log a replacement (with Undo), swipe left to delete, or long-press a row to snooze, pause, reorder, edit or delete.
- **Reminders**: an optional heads-up N days before, a reminder on the due day at the time you choose, and daily follow-ups if it's overdue. They're exact alarms, so they arrive on the minute, and they're rebuilt after a restart, an update or a time zone change.
- **Act from the notification**: *Replaced it*, *Snooze 1 hour* or *Remind me tomorrow* without opening the app.
- **Templates** for common items (lenses, toothbrush heads, razor blades, HVAC and water filters, smoke detector batteries, oil changes and more), or a custom item.
- **History** per item, with a chart of how long each cycle lasted against the schedule, average interval and on-time rate. Backdate a replacement you forgot to log; delete the latest entry to rewind the countdown.
- **Spares**: one is used each time you log a replacement, with a "running low" notification (with a Reorder button when the item has a link).
- **Backup**: export everything to a JSON file and import it on another phone (Settings › Backup).
- Notification counts show on launchers that support badges. Everything stays on the phone: no account, no network.

## Build and test

Requirements: JDK 17 and the Android SDK (Android Studio Ladybug or later includes both). Open the `android/` folder in Android Studio, or from the command line:

```sh
cd android
./gradlew :core:test                    # unit tests, a few seconds, no emulator needed
./gradlew :app:assembleDebug            # debug APK in app/build/outputs/apk/debug/
./gradlew :app:connectedDebugAndroidTest # UI tests on a connected emulator or device
```

The UI tests use the app's own storage and clear it, so run them on an emulator, not on the phone you use.

CI (`.github/workflows/android.yml`) runs the unit tests, builds the signed release APK, runs the UI tests on an Android 14 emulator, then publishes the APK.

## How it's organized

```
android/
  core/          Plain Kotlin, no Android: date math, reminder planning, history stats,
                 templates, the editor draft and the JSON item store. Unit tested.
  app/
    ReminderGenieApp.kt     App entry and the shared objects (store, settings, reminders)
    ItemActions.kt          Changes that also touch notifications (replace, undo, snooze, delete)
    data/                   Atomic file storage and preferences
    reminders/              Alarms, notifications and their action buttons, reboot handling
    ui/                     Compose screens: list, add, editor, detail, settings
  app/src/androidTest/      Compose UI tests that drive the app
```

Items are saved as one small JSON file in the app's private storage, written atomically after each change. If the file ever can't be read, the app explains instead of starting empty, and leaves the file untouched. Item icons are stored by their iOS (SF Symbols) names and mapped to Material icons, keeping the data compatible with the iPhone app's.
