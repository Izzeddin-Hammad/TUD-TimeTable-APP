# v1.28 — correct class times, working Semester 2, and a readable change feed

**Release date:** 2026-09-15
**APK:** `releases/TimeTable-v1.28-debug.apk`
**Requires:** Android 8.0+ (API 26)

> ⚠️ Still a **TU Dublin–only prototype**. Unsigned debug build.

Four student-facing fixes: three to the timetable telling the truth, one to how it tells you it
changed. Class times are now shown in Irish local time, the Semester 2 tab always switches, the
group picker offers real cohorts instead of combinations, and the change notification is a tappable
banner with a compact, expandable list instead of a modal that opens itself.

---

## 1. Classes were an hour early through Irish Summer Time

**Symptom.** Every class in the timetable read an hour earlier than the published schedule, from
late March to late October.

**Cause.** The Scientia Publish API serialises every session in **UTC** — a 09:00 lecture arrives as
`2026-09-14T08:00:00+00:00` — and the app rendered that clock verbatim. Through Irish Summer Time
(UTC+1) that is an hour early, and near midnight it also put the class on the wrong day. The
official site at `timetables.tudublin.ie` renders the same session as `9:00:00 AM GMT+01:00`, so the
app disagreed with it by an hour.

**Fix.** Timestamps are projected from their own offset into `Europe/Dublin` before anything is
shown. The single definition is a new pure, Android-free `api/DublinTime.kt`, used by the timetable
grid (`toUiEvent`), the week classifier (`classifyWeeks`) **and** the change feed. Because the
conversion happens per event instant, it self-corrects after the clocks change — nothing to do in
October.

## 2. "Semester 2" now always switches

**Symptom.** On a course whose Semester 2 is not published yet, tapping the **Semester 2** tab did
nothing visible — the screen stayed on the Semester 1 week.

**Cause.** Every week in that semester is "empty", so the optional **Hide empty weeks** filter
(Settings, on by default) reduced Semester 2's week list to nothing. With an empty list, the code
that moves the view into the semester was skipped.

**Fix.** "Hide empty weeks" can no longer empty out a whole semester: if every week in the selected
semester is empty, the full semester range is used instead. Switching now always lands in the chosen
semester and shows "No classes this week" when there genuinely are none.

## 3. The group picker offers cohorts, not combinations

**Symptom.** The group dropdown listed options such as `TU859/Y1/G1 + TU859/Y1/G2`.

**Cause.** A shared lecture's "Class Group" field names several cohorts at once (`"TU859/Y1/G1 +
TU859/Y1/G2"`), and the whole string was offered as a single choice — reading as though
`TU859/Y1/G1` had other groups "added as a +". That is not a cohort anyone is enrolled in.

**Fix.** Each cohort named by the field is its own option (`TU859/Y1/G1` and `TU859/Y1/G2`
separately). The shared class still appears under each of them, because matching parses the row the
same way. This affects the timetable's group filter and the sub-group chips in search.

## 4. "Timetable changes" is readable

**Before.** A modal opened itself on every refresh that found a change and printed, per class, one
semicolon-joined sentence:

```
🟡 Tue 10:00 - 12:00
   CMPU3021 — Software Engineering
   Room: A214 → B102; Lecturer: Dr. A. Byrne → Dr. C. Nolan (Modified)
```

**Now.** A compact, dismissible banner — `3 timetable changes · Tap to review` — is shown instead;
nothing opens on its own and the timetable stays in view. Tapping it opens a short, scannable list,
one row per class (`COMP H4006 — Systems Ops & Dev`, `Mon · 09:00 - 11:00 · Room changed`), where a
tap expands only the fields that changed (`Room   A214 → B102`), or the class's own details for a
cancellation or a new class. Change times are shown in the same Irish local time as the timetable.

## Verified

`tools/jvm-test-harness.sh` → 179 tests, 0 failures. `gradle testDebugUnitTest` → **231 tests,
0 failures**. `gradle assembleDebug` → BUILD SUCCESSFUL (`versionName` 1.28); the APK installs and
launches clean on an emulator. The three timetable fixes and the change banner were each reproduced
first and confirmed fixed on-device against the live API.
