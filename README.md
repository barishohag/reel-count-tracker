# Reel Count

A daily tracker for time spent in Facebook, Instagram, and TikTok, with an optional manual Reel tally. This folder includes both a native Android project and a browser-based web app.

## Features

- Log one reel with a tap after choosing a platform.
- On Android, see today's foreground time in Facebook, Instagram, and TikTok.
- See today's manual Reel total and progress toward a daily goal.
- Review totals for the last seven days.
- Undo a log and change the daily goal.
- Keep manual counts locally on the device with Android SharedPreferences.
- Request Android Usage Access so the system can record app time while Reel Count is closed. The app reads today's usage events when opened and refreshes the dashboard every 30 seconds while it is visible.
- Collect a watch-time range and daily Reel goal during first-run onboarding.

Android Usage Access measures app foreground time, not individual Reels or video views. It is optional, controlled in Android Settings, and usage is not uploaded. Android does not provide apps a public API for exact per-video view counts in these social apps. The web app cannot inspect activity in other apps and remains a manual tracker.

## Run the web app

The web app is `index.html`. Its first-run setup asks for daily watch time, a comfortable reel goal, and counting preferences before opening the tracker. Responses and counts stay in browser local storage. The web app also includes a manifest and offline service worker.

Run the small local server from this folder:

```text
node preview-server.js
```

Then open `http://127.0.0.1:8765` in a browser. A secure local or HTTPS origin is needed for offline service-worker support. On a supported mobile browser, use its **Add to Home Screen** or **Install app** option.

## Open the Android app in Android Studio

Open this folder as a project and let Gradle sync. The Android app uses Kotlin and Android's built-in views; it has no third-party runtime dependencies. It requires Android SDK 35 to build and runs on Android 8.0 (API 26) and newer.

After onboarding, tap **Enable automatic tracking** and allow **Usage Access** for Reel Count in Android Settings. If you skip setup, you can enable it later from the dashboard. Manual Reel logging remains available because Android's Usage Access reports app time, not per-video counts.

