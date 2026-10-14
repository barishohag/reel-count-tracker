# Reel Count

A daily tracker for short-form videos watched on Instagram, TikTok, and YouTube. This folder includes both a native Android project and a browser-based web app.

## Features

- Log one reel with a tap after choosing a platform.
- See today's total and progress toward a daily goal.
- Review totals for the last seven days.
- Undo a log and change the daily goal.
- Keep counts locally on the device with Android SharedPreferences.
- Collect watch-time range, daily reel goal, and counting preferences during first-run onboarding.

## Run the web app

The web app is `index.html`. Its first-run setup asks for daily watch time, a comfortable reel goal, and counting preferences before opening the tracker. Responses and counts stay in browser local storage. The web app also includes a manifest and offline service worker.

Run the small local server from this folder:

```text
node preview-server.js
```

Then open `http://127.0.0.1:8765` in a browser. A secure local or HTTPS origin is needed for offline service-worker support. On a supported mobile browser, use its **Add to Home Screen** or **Install app** option.

## Open the Android app in Android Studio

Open this folder as a project and let Gradle sync. The Android app uses Kotlin and Android's built-in views; it has no third-party runtime dependencies. It requires Android SDK 35 to build and runs on Android 8.0 (API 26) and newer.

This first version uses manual logging. Android does not expose per-video watch counts from other apps. Automatic counting would require monitoring another app's screen through an Accessibility Service, which can be unreliable when apps change their interfaces and raises privacy concerns.
