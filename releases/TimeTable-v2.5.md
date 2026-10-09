# v2.5 — Material 3 Modernization & Conflict Detection Engine

**Release date:** 2026-10-09
**APK:** `releases/TimeTable-v2.5-release.apk` (1.8 MB, signed release build)
**Requires:** Android 8.0+ (API 26)

> ⚠️ **TU Dublin only.** Unofficial student project, not affiliated with TU Dublin.

Installs in place over v2.0–v2.4 (same key).

## What's New

### Conflict & Overlap Detection
- **Concurrent Session Detection**: Timetable events occurring simultaneously on the same day are now detected using precise local time interval calculations (`[startMinutes, endMinutes)`).
- **Conflict Warning Badges**: Overlapping classes display a prominent `Conflict (+N)` badge with an icon directly on the card so conflicting labs or electives are immediately obvious.
- **Invalid Time Guards**: Invalid durations or timestamps where end time is before or equal to start time are guarded against and flagged with an `Invalid Time` indicator.

### Material 3 Modernization
- **Modern Elevated Cards**: Event cards now utilize Material 3 `ElevatedCard` containers with a consistent `16.dp` rounded corner shape and subtle surface elevation.
- **Current Time Indicator**: A horizontal timeline indicator with an active accent dot (`Now · HH:mm`) dynamically marks the current wall clock time when today's schedule is active.
- **Floating Action Button (FAB)**: A Material 3 FAB (`+`) allows students to add custom personal entries to their day's schedule.

### Date & Time API Modernization
- Fully replaced legacy `SimpleDateFormat` and `java.util.Date` with modern `java.time` APIs (`Instant`, `ZonedDateTime`, and `DateTimeFormatter`) in sync workers and settings.

## Verified
- `./gradlew test` → **250 tests, 0 failures**
- `./gradlew assembleRelease` → **BUILD SUCCESSFUL**, R8-minified and signed with the release key.
