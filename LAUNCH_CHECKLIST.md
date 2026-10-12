# Launch checklist

What's left between this repo and an App Store release, in the order it needs doing.

## On your Mac, before anything else

- [ ] Open the project in Xcode 26. If it offers **Update to recommended settings**, accept it, then build. The code hasn't been compiled since the go-live fixes, because they were made without Xcode.
- [ ] Run the tests (⌘U). `ScheduleTests` covers the date math and reminder plan, including the snooze rules.
- [ ] Smoke test on a real iPhone:
  - Add an item with a 1-day interval, last replaced yesterday, reminder a couple of minutes out. Lock the phone. The reminder should arrive and the badge should read 1.
  - Long-press the reminder and tap **Replaced it**. The badge should clear without opening the app.
  - Add an item due in a few days with a heads-up today, tap **Remind me tomorrow** on the heads-up, and check the detail screen still shows the due-day reminder.
  - Swipe away the newest history entry on an item and confirm the countdown goes back to the previous one.
  - Try Siri: "What's due in Reminder Genie".

## App Store Connect

- [ ] Create the app record with your final bundle ID.
- [ ] **Privacy policy URL** (required even though nothing is collected). A one-page "Reminder Genie stores everything on your device and collects no data" is enough.
- [ ] **App Privacy**: answer "Data Not Collected". The bundled `PrivacyInfo.xcprivacy` matches this.
- [ ] **Export compliance** is answered in the build (`ITSAppUsesNonExemptEncryption = NO`), so TestFlight won't ask.
- [ ] Screenshots: 6.9" iPhone. The app also runs on iPad, so 13" iPad screenshots are required too, or set the target to iPhone only.
- [ ] Support URL, description, keywords, age rating (4+), category (Productivity).
- [ ] Ship a TestFlight build to yourself and use it for a few days before submitting.

## Worth doing before or soon after 1.0

- **Versioned SwiftData schema.** Wrap today's models in a `VersionedSchema` (V1) with a `SchemaMigrationPlan` before the first public build, so later model changes can migrate safely. Today a store that fails to open stops the app at launch (`fatalError` in `AppModelContainer`).
- **iOS 26 app icon.** Rebuild the icon in Icon Composer with dark, tinted and clear variants; the current single PNG looks flat next to system icons on iOS 26.
- **Swift 6 language mode.** The project is on Swift 5 mode. Moving to Swift 6 (or turning on Approachable Concurrency) catches threading bugs at compile time; do it with Xcode open, since it needs the compiler's feedback.
- **Widgets.** A "what's due next" Home and Lock Screen widget is the most requested feature for this kind of app. It needs an App Group shared between the app and widget, which is a signing change.
- **Time-sensitive reminders.** With the Time Sensitive Notifications capability, due-day reminders can break through Focus. Also a signing change.
- **Localization.** Strings are English-only and built with interpolation; moving to a String Catalog is the first step.
