# v2.0 — the first stable build

**Release date:** 2026-09-15
**APK:** `releases/TimeTable-v2.0-release.apk` (1.8 MB, signed release build)
**Requires:** Android 8.0+ (API 26)

> ⚠️ **TU Dublin only.** This app supports one institution and is an unofficial student project.

## Read this first — v2.0 is signed with a new key

Every release up to v1.29 was an **unsigned debug** APK. v2.0 is a proper signed release build, so
**Android will refuse to install it over an existing install** — a different signing key means a
different app as far as the package manager is concerned. (The in-app updater would hit the same
wall; this is a one-time migration, not a broken update.)

To move to v2.0:

1. **Uninstall** the old app (long-press the icon → App info → Uninstall), or
   `adb uninstall com.example.timetablescraper`.
2. Install `releases/TimeTable-v2.0-release.apk` (tap it, or `adb install …`).

**Uninstalling clears the app's local data** — the pinned course, bookmarks, search history and
settings live in app storage, so they do not come across. Re-pin and re-save your course once; it
takes a few seconds. Timetable data itself is re-fetched, so nothing is lost.
Keep the APK safe: from v2.0 onward, updates install in place again (same key).

## What changed

A full-app audit ran before this release. Three tiers of findings were fixed.

### Blockers — you could not ship a stable build without these

- **There was no release build at all.** No signing config existed, so `assembleRelease` emitted an
  unsigned APK that Android refuses to install. There is now a real signing config; releases are
  R8-minified, resource-shrunk and signed, and the APK dropped from ~25 MB to **1.8 MB**.
- **The updater could not see a release.** `UpdateChecker` matched only `-debug` filenames, so
  publishing `TimeTable-v2.0.apk` would have reported "No valid APK files found" and silently
  stopped offering updates. It now matches both the release and legacy debug names.
- **A broken database was an unbreakable crash loop.** "Clear Cache & Restart" cleared the cache
  *through Room* — which cannot work when the database is what fails to open — so nothing ever
  deleted the bad file. It can now delete the database files directly.
- **A corrupt saved day index crashed the timetable** while composing (`days[selectedDayIndex]`,
  unclamped). The index is clamped on read and on use.
- **Cache writes were not atomic.** The foreground load and the background sync both replace the
  same week as two separate calls, which could interleave into duplicate rows or be interrupted
  between them, leaving a student with no cached week. Both now go through one `@Transaction`.
- **Duplicate classes after a refresh.** The repository returned the raw response while caching the
  de-duplicated set, so the visible class count could change with no timetable change. It now
  returns what it cached.
- **Your data was being uploaded to Google.** `allowBackup` was on with template rules, so the
  cached timetable (lecturer names, rooms, class groups), bookmarked courses and search history
  went to cloud backup — contradicting the privacy policy. Backup is now off, with explicit
  excludes.

### Correctness

- **Rotation no longer loses your place.** Every screen, the open course and the search results
  were plain `remember`, so rotating dropped you back to the start screen. They are saveable now.
- **One API client, one rate-limit budget.** The app built a second `TimetableApiService` while the
  rest of the app used the shared singleton, so the documented "5 requests / 10 s" limit was really
  10/10 s and the connection pool was doubled.
- **Empty weeks are cached.** A week that was fetched successfully but has no classes stored no
  rows and so was never recognised as cached: every visit hit the network, and offline students got
  an error instead of "no classes this week".
- **Request de-duplication actually de-duplicates.** The losing caller of a concurrent request
  evicted the winner's in-flight entry, so a third caller issued a duplicate HTTP request.

### Polish

- **The privacy policy and the design doc now describe the app.** Both named v1.16, PRIVACY.md
  pointed at the wrong GitHub API, and it claimed on-device-only while backup was uploading data.
- **Nothing about your course is written to logcat** any more — it is readable from a bug report.
- **The launcher icon was 5.2 MB** inside a 25 MB APK; it is 117 KB now.
- **Dead code removed**: `NetworkResult`, `WeekCacheIndex`, `CACHE_TTL_MS`, `apiBase`,
  `SquircleShape`, and two unreferenced exception flags.

## Verified

- `tools/jvm-test-harness.sh` → 180 tests, 0 failures. `gradle testDebugUnitTest` → **245 tests,
  0 failures** (including new `DublinTimeTest` and `SafePrefs.float` coverage).
- `gradle assembleRelease` → BUILD SUCCESSFUL; `apksigner` confirms a V2 signer.
- On an emulator with the minified release APK: launches, searches, opens a course, renders
  classes, the Room cache round-trips, Settings/themes work, and rotating the device keeps the
  screen and the search — **0 fatal exceptions**, so Room, WorkManager, OkHttp and org.json all
  survived R8.
- v2.0 installs **in place over the previous release-signed build** (the path every future update
  will take).

## Known limitations (not fixed in v2.0)

- **English only.** User-facing strings are Kotlin literals, not `strings.xml`, so the app cannot be
  translated. Deliberate: it supports one institution and one language today, and a partial
  extraction would be worse than a complete pass. Recommended as its own change.
- **Some touch targets are under 48 dp** (the semester segmented control, the search field's clear
  button) and `tertiaryLabel` text is low-contrast.
- **`EventKey` keeps raw wall-clock event identity.** A fixed lecture's UTC clock shifts by an hour
  across the DST boundary, so the change feed can still emit a spurious "Time: 08:00 → 09:00" for an
  otherwise unchanged week at the end of October.
