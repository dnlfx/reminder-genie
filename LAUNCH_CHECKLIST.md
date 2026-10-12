# Launch checklist

What's left between this repo and an App Store release, in the order it needs doing.

## On your Mac, before anything else

- [ ] Open the project in Xcode 26. If it offers **Update to recommended settings**, accept it, then build. The code hasn't been compiled since the go-live fixes, because they were made without Xcode.
- [ ] The project is now in Swift 6 language mode. Fix anything the compiler flags; the concurrency changes were made by reading the code, not compiling it.
- [ ] Run the tests (⌘U). `ScheduleTests` covers the date math and reminder plan, including the snooze rules.
- [ ] Smoke test on a real iPhone:
  - Add an item with a 1-day interval, last replaced yesterday, reminder a couple of minutes out. Lock the phone. The reminder should arrive and the badge should read 1.
  - Long-press the reminder and tap **Replaced it**. The badge should clear without opening the app.
  - Add an item due in a few days with a heads-up today, tap **Remind me tomorrow** on the heads-up, and check the detail screen still shows the due-day reminder.
  - Swipe away the newest history entry on an item and confirm the countdown goes back to the previous one.
  - Try Siri: "What's due in Reminder Genie".

## App icon for iOS 26

The asset catalog now has dark and tinted versions of the icon, so it already adapts on iOS 18 and 26. For the full Liquid Glass look:

- [ ] Open `Design/AppIcon.icon` in Icon Composer (it ships with Xcode 26). It holds the icon as separate layers (track, progress ring, star, sparkle) over the purple gradient. Check the Default, Dark, Clear and Tinted previews and adjust glass and shadow to taste.
- [ ] Drag `AppIcon.icon` into the ReminderGenie folder in Xcode and remove the AppIcon set from `Assets.xcassets`, so the target's app icon name `AppIcon` points at the new file. Xcode generates the older-iOS sizes from it.

## App Store Connect

- [ ] Create the app record with your final bundle ID.
- [ ] **Privacy policy URL** (required even though nothing is collected). A one-page "Reminder Genie stores everything on your device and collects no data" is enough.
- [ ] **App Privacy**: answer "Data Not Collected". The bundled `PrivacyInfo.xcprivacy` matches this.
- [ ] **Export compliance** is answered in the build (`ITSAppUsesNonExemptEncryption = NO`), so TestFlight won't ask.
- [ ] Screenshots: 6.9" iPhone. The app also runs on iPad, so 13" iPad screenshots are required too, or set the target to iPhone only.
- [ ] Support URL, description, keywords, age rating (4+), category (Productivity).
- [ ] Ship a TestFlight build to yourself and use it for a few days before submitting.

## Worth doing before or soon after 1.0

- **Widgets.** A "what's due next" Home and Lock Screen widget is the most requested feature for this kind of app. It needs an App Group shared between the app and widget, which is a signing change.
- **Time-sensitive reminders.** With the Time Sensitive Notifications capability, due-day reminders can break through Focus. Also a signing change.
- **Localization.** Strings are English-only and built with interpolation; moving to a String Catalog is the first step.
