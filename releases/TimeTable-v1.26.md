# v1.26 — the crash screen now tells you what happened

**Release date:** 2026-02-09
**APK:** `releases/TimeTable-v1.26-debug.apk`
**Requires:** Android 8.0+ (API 26)

> ⚠️ Still a **TU Dublin–only prototype**. Unsigned debug build.
>
> **This is a diagnostic release.** It does not fix the underlying crash reported after v1.25 —
> it makes that crash *visible and reportable*, and makes the recovery buttons work reliably.
> If you see the error screen, tap **Show error details** (it now starts expanded) and send the
> text.

## How to update

Open the app (or **Settings → Check for updates**), then **Update Now**. Fresh install:
[download the APK](https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP/raw/main/releases/TimeTable-v1.26-debug.apk).

If the app will not start at all, install this APK directly from the link above, or fall back to
`releases/TimeTable-v1.24-debug.apk`.

## What changed

Four defects in the crash-recovery path, all of which contributed to *"Something went wrong"
with nothing underneath it and no way out*:

1. **The crash message could be lost entirely.** The record was written to `SharedPreferences`
   with `apply()` — asynchronous — and the process is killed immediately after a crash, so the
   write often never landed. The marker file was written synchronously, but only its *existence*
   was checked, never its contents. The message is now committed synchronously, and the screen
   reads the marker file when the preferences copy is missing, so the details always appear.
2. **The details were hidden behind a collapsed "Show error details" button**, so the screen
   looked blank. They are now shown by default.
3. **An undeletable crash marker trapped the app.** Deletion failure was ignored, so the marker
   survived every launch and the recovery screen came back forever — with both buttons looking
   like they did nothing. Clearing now records a timestamp, and a marker older than the last
   clear is treated as stale and ignored. Recovery works even if the file cannot be removed.
4. **A marker whose text did not match the expected shape was discarded**, producing an empty
   panel. Unrecognised content is now surfaced verbatim — it is still the only evidence of what
   happened.

Nothing else changed: every fix from v1.24 and the User-Agent correction from v1.25 are included.

## If you hit the error screen

1. The details are shown automatically — read the **message** and the first few `at …` lines of
   the stacktrace. The topmost frame naming a `com.example.timetablescraper` class is the cause.
2. **Try Again** returns you to the app and clears the flag.
3. **Clear Cache & Restart** clears the cached timetables (your saved courses, pinned course and
   settings are kept) and restarts. It is no longer able to hang, and no longer deletes your data.

Verified: `gradle test` → **183 tests, 0 failures** across 15 classes; `gradle assembleDebug`
→ BUILD SUCCESSFUL. The new `CrashFlagsTest` (10) and `CrashMarkerTest` (7) cover the staleness
decision and the marker format, including records already written by earlier versions.
