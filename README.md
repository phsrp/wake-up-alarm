<div align="center">

# Wake Up

An Android alarm app for people who sleep through their alarms.
It rings until you solve math problems, and there is no snooze.  
**Every line of it was written by AI.**

[![Download](https://img.shields.io/badge/download-latest%20APK-c4a7e7?style=for-the-badge)](https://github.com/phsrp/wake-up-alarm/releases/latest) ![Made with AI](https://img.shields.io/badge/made%20with-AI-ebbcba?style=for-the-badge) ![Android](https://img.shields.io/badge/platform-Android-31748f?style=for-the-badge) ![Vibe coded](https://img.shields.io/badge/vibe-coded-c4a7e7?style=for-the-badge)

</div>

## Vibe coded

This app was designed, written and built by Claude (Anthropic) from plain-English instructions. It was made for one phone (a OnePlus running OxygenOS) and tested there, so it may behave differently on other devices.

## What it does

- **Alarms** with a time, repeat days, a label and a sound picked from your phone's ringtones and audio.
- **Folders** to group alarms (for example "School" or "Project work"), with one switch to turn a whole folder on or off.
- **Hard to ignore:** it rings on the alarm volume at maximum, vibrates, turns the screen on and shows over the lock screen. It rings until you stop it, or for a set time if you choose that.
- **No snooze:** the only way to stop it is to solve math problems in a row. A wrong answer starts the count again. Difficulty is easy, medium or hard.
- **Survives restarts:** alarms are set again after a reboot, an app update, or a time or timezone change.
- **Timers and a stopwatch** with laps. Timers keep running when the app is closed and ring when they run out.
- **Live Updates:** running timers and the stopwatch show a live clock in the notification, and in OnePlus's Live Alerts capsule once you turn it on for the app.
- **Folders as tiles:** folders sit in a row at the top (three at a time, scroll for more). Tap the folder icon to switch a whole folder on or off, tap the tile to open it, and drag an alarm onto a tile to move it in.
- **Next alarm countdown** on the alarms screen.
- **Themes:** Material 3 Expressive (colours from your wallpaper), Gruvbox Dark and Light, and Rosé Pine and Rosé Pine Dawn.
- **Hold to reorder and delete:** press and hold an alarm or timer, then drag it to move it, or let go to select it and delete.
- **Google can set alarms and timers:** Wake Up answers Android's standard "set an alarm" and "set a timer" requests. Voice commands can only create alarms, never dismiss them.
- **Setup guide** on first launch for permissions and the battery settings OnePlus phones need, and a **Test alarm in 10 seconds** button.

## Previews

<!--
Add screenshots to docs/screenshots/ and uncomment this block:

<div align="center">

<img src="docs/screenshots/alarms.png" alt="The alarm list with folders" width="30%"> <img src="docs/screenshots/ringing.png" alt="The ringing screen with a math problem" width="30%"> <img src="docs/screenshots/setup.png" alt="The setup guide" width="30%">

</div>
-->

*Screenshots coming soon.*

## How to install

1. On your phone, open the [latest release](https://github.com/phsrp/wake-up-alarm/releases/latest) (the purple **download** button at the top of this page does the same).
2. Under **Assets**, tap `Wake-Up.apk`.
3. Open the downloaded file. If Android asks, allow your browser or file manager to **install unknown apps**, then tap **Install**. Play Protect may warn that the app is unknown; choose **Install anyway**.
4. Open Wake Up and follow the setup guide.

To update, download `Wake-Up.apk` again and install it over the old one. Your alarms are kept. The release is replaced with the newest build every time the code changes.

> **Never use Force stop** on the app in Android settings. It cancels every alarm until you open the app again.

## Built with

- [Kotlin](https://kotlinlang.org/) and [Jetpack Compose](https://developer.android.com/compose)
- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive) through [Compose Material 3](https://developer.android.com/jetpack/androidx/releases/compose-material3) 1.5 (Apache-2.0)
- [Room](https://developer.android.com/training/data-storage/room) for storing alarms and folders (Apache-2.0)
- [AlarmManager `setAlarmClock`](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)) for exact alarms that work in Doze
- [KSP](https://github.com/google/ksp) for Room's code generation (Apache-2.0)
- [GitHub Actions](https://docs.github.com/actions) to build the APK
