# v1.24 — Student-first fixes

**Release date:** 2026-02-09
**APK:** `releases/TimeTable-v1.24-debug.apk`
**Requires:** Android 8.0+ (API 26)

> ⚠️ Still a **TU Dublin–only prototype**. Unsigned debug build; the app connects to the TU Dublin
> Scientia Publish API with anonymous auth and is not intended for production use.

## How to update

- **From v1.22/v1.23:** open the app — it checks `releases/` on launch and offers **Update Now**.
  Or tap **Settings → Check for updates**.
- **Fresh install:** [download the APK](https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP/raw/main/releases/TimeTable-v1.24-debug.apk)
  and tap it. Android asks once to allow installing unknown apps.

No data is lost on upgrade: the local database schema is unchanged (Room v7), and your saved
courses, pinned course and group choices are preserved. The app's cached timetables will refresh
on the next sync.

## What changed

Nineteen defects were found and fixed in this release. The ones you will actually notice:

### Timetable correctness
- **All-cohort lectures no longer disappear when you pick a subgroup.** A class with no specific
  group (a plenary lecture) applies to everyone; it used to be filtered out the moment you chose
  G1/G2, so the classes for your whole course were the ones that vanished.
- **Changes are now reported accurately.** The change detector compared sessions in a way that
  could report a "room changed" alert on an unchanged week, and could miss a real room change when
  two sessions shared a time slot. Sessions are now matched one-to-one, so a swap is reported once
  and nothing is reported when nothing changed.
- **A module title change is now shown** (previously invisible), and a **moved class is one alert**
  ("Time: 10:00 - 11:00 → 12:00 - 13:00") instead of "one class removed, another added".
- **Cancelled classes stay cancelled.** A week that came back empty from the API was not written to
  the cache, so the old classes kept reappearing from it on every later load.
- **Your subgroup is resolved from the authoritative field.** When the event name and the class
  group disagreed, the class group now wins — that is the field that carries shared groups
  (e.g. "G1 + G2").
- **Two parallel lab groups are both kept.** A de-duplication rule could drop one of two lab
  sessions running in different rooms at the same hour, so a whole subgroup could lose its lab.

### Stability
- **No crash loop from corrupt settings.** A stored value with an unexpected type used to throw
  during app start-up, leaving a crash the only way to clear was wiping app data. Bad values now
  fall back to sensible defaults.
- **"Clear Cache & Restart" no longer deletes your data.** It used to wipe bookmarked courses,
  search history, the pinned course and every preference. It now clears only the timetable cache
  and cache-derived state.
- **"Try Again" and the recovery screen are reliable.** The recovery button could stay disabled
  forever if clearing failed, and the crash flag could survive a restart so the error screen kept
  coming back after rotation.
- **The database is no longer reset on any schema mismatch.** Only database versions that
  genuinely have no upgrade path may be reset now; a future mismatch fails visibly instead of
  silently deleting saved courses.

### Dates, offline and battery
- **The current week is always in the week picker** — including May resits and late August, which
  previously fell outside the hardcoded September–April window.
- **Month names are always English** instead of following the device language.
- **No more 2-minute background polling.** The screen observes the local cache and repaints when
  the cached week actually changes, which removes 30 wake-ups an hour.

### Security and hygiene
- **Cleartext HTTP is denied.** Every endpoint (the Scientia API and the update check) is HTTPS, so
  nothing needed the previously global cleartext allowance.
- Removed an unused networking dependency (Retrofit) that the app never called.

## Known behaviour to be aware of

- **Version naming must stay in step.** The in-app updater only offers a build whose filename
  version is strictly newer than the installed app. If `versionName` in `app/build.gradle.kts` and
  the APK filename disagree, the prompt can return after installing; both are `1.24` here.
- The update check reads the **`releases/` directory of the `main` branch** (via the GitHub
  Contents API), so old APKs in that directory remain publicly downloadable. They do not affect
  updates — the newest version wins — but keeping only the latest APK is tidier.
- Cleartext being denied is a deliberate behaviour change: if a local HTTP test server is ever
  needed, add a debug-only `domain-config` for it rather than relaxing the base config.
- Still an unsigned debug build with in-app APK installs; expect a Play Protect / unknown-sources
  prompt on some devices. Moving to Play with staged rollout is recommended before wider use.
