# v2.3 — twenty real courses, and two parsing bugs they exposed

**Release date:** 2026-09-15
**APK:** `releases/TimeTable-v2.3-release.apk` (1.9 MB, signed release build)
**Requires:** Android 8.0+ (API 26)

> ⚠️ **TU Dublin only.** Unofficial student project, not affiliated with TU Dublin.

Twenty courses were drawn at random from the whole TU Dublin timetable and each was fetched from
the live API and checked end to end. One of them was empty (as expected for some courses, and
skipped); the other nineteen were clean — no unparseable times, no end-before-start, no sessions
outside teaching hours, no blank modules or titles, no duplicate rows. Two real display bugs did
fall out of the exercise, and both are fixed here.

## Fixed

- **A room could read `"null"`.** Upstream genuinely sends `"Location": null` for some sessions —
  12 of the 186 in one of the sampled courses — and `optString` turns that null *sentinel* into the
  literal text `null` rather than falling back. The card showed the room as **null**. A null is now
  treated as absent, so it falls back to "TBA".
- **The module code and title were swapped for a second name shape.** Upstream uses two shapes:
  `MODULE/Title/…` and `Title /MODULE …`. The parser assumed the first, so a session named
  `Machine Learning /SPEC 9270(20253C) Lab support` rendered as
  `SPEC 9270 — SPEC 9270(20253C) Lab support`. It now reads `SPEC 9270 — Machine Learning`.

Both were confirmed on a device with the real course before and after the change, and both now have
instrumented regression tests (`TimetableParserTest`, 22 tests green on an emulator).

## What the sweep also confirmed

- **Weekend teaching is common, not a curiosity.** Of the twenty courses, five have Saturday
  sessions — one has **35 of them**. Before v2.2 those classes were fetched, counted, and
  impossible to see; the day strip now grows to include the weekend day a week actually has.
- Two name-shape families, five distinct `ExtraProperties` (`Module`, `Class Group`, `Staff`,
  `Location UserText5`, `Week Range`), cohorts from a single group to a compound four-programme set,
  and sparse courses (1 event in the year) alongside dense ones (394 events over 26 weeks).

## Upgrade note

Weeks already in the local cache keep the text parsed by the older version until they refresh —
at most one sync interval (24 h by default), or immediately via **Settings → Clear All Cached Data**.
Newly fetched weeks are parsed correctly straight away.

## Verified

- `tools/jvm-test-harness.sh` → 181 tests, 0 failures. `gradle testDebugUnitTest` → **246 tests,
  0 failures**. `gradle connectedDebugAndroidTest` → **22 instrumented tests, 0 failures**.
- `gradle assembleRelease` → BUILD SUCCESSFUL, signed.
- Live-API sweep: **20/20 courses clean** (19 with data, 1 empty and replaced), then the two fixed
  shapes re-checked in the app UI on a device.

## Still known, unchanged

- English only (strings are Kotlin literals, not `strings.xml`).
- The Custom theme sliders repaint and write preferences on every drag frame.
- Per-course preference keys clear only via **Erase all app data**.
