# v2.6 — Week numbers that mean what you mean, and a *Now* button

**Release date:** 2026-10-12
**APK:** `releases/TimeTable-v2.6-release.apk` (1.8 MB, signed release build)
**Requires:** Android 8.0+ (API 26)

> ⚠️ **TU Dublin only.** Unofficial student project, not affiliated with TU Dublin.

Installs in place over v2.0–v2.5 (same key).

## What's New

### Week numbering now starts at the first teaching week
The week picker numbered weeks by their position in the whole academic year, and that list opens
on the Monday on or before 1 September. The two (or three) pre-teaching weeks before classes
start were therefore counted, so **the first week you actually had classes read "W3"** — in
2025/26 (teaching began Mon 15 Sep) and 2026/27 (Mon 14 Sep) alike.

- **W1 is the first week of the semester that has classes.** 15 Sep 2025 reads "W1", not "W3".
- **Semester 2 numbers from its own week 1**, so "week 1 of Semester 2" is what the tab shows.
- Weeks before the first teaching week (still reachable when "Hide empty weeks" is off) show
  their dates only — no invented number.
- Numbering is anchored on the semester's first *active* week rather than a position in the
  visible list, so flipping "Hide empty weeks" can no longer renumber the semester.

### Jump to *Now*
- **A *Now* button beside the week picker** lands on the week that contains today, switches to
  the semester that week is in, and opens today's day tab — the date-by-date hunt through ~30
  near-identical ranges is gone.
- **The background year-classifier can no longer move you.** Once you pick a week yourself (from
  the dropdown or with *Now*), the classifier finishing no longer drags you to the first active
  week — which it did a second or two after you tapped, on a course being classified for the
  first time.

## Verified
- JVM test harness (`tools/jvm-test-harness.sh`) → **191 tests, 0 failures** (6 new tests pin the
  week-number rule: first teaching week is week 1, the offset ignores gaps, a week before it has
  no number, Semester 2 restarts, and numbering survives the calendar-year boundary)
- `./gradlew testDebugUnitTest` → **0 failures**
- `./gradlew assembleRelease` → **BUILD SUCCESSFUL**, R8-minified and signed with the release key
