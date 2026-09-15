# v2.2 — student-facing bug sweep

**Release date:** 2026-09-15
**APK:** `releases/TimeTable-v2.2-release.apk` (1.9 MB, signed release build)
**Requires:** Android 8.0+ (API 26)

> ⚠️ **TU Dublin only.** Unofficial student project, not affiliated with TU Dublin.

Installs in place over v2.0/v2.1 (same key). Only the `main` branch is used from now on.

Six read-only agents walked the app the way a student does — first run, the core loop, every empty
and error state, careless input, settings persistence, dates, and long sessions — looking
specifically for things a student would *see* go wrong. Everything they found that a student could
actually hit is fixed.

## The one that mattered most

**Switching week showed the previous week's classes under the new week's dates.** Nothing cleared
`events` when the week changed, and the loading flag was only ever set to `false` — so while the new
week loaded, the day tabs showed the new week's dates with the *old* week's timetable beneath them.
If that week wasn't cached and the network failed, it stayed that way permanently: last week's
classes presented as this week's, with no spinner and no error. Now a week change drops the old rows
first, so the dates and the classes can never disagree.

## Also fixed

- **A stale error was never cleared.** After one offline load, every later *empty* week showed
  "No internet connection…" — telling the student they were offline while they were online.
- **A failed refresh over cached data was silent.** With rows on screen the app showed only the
  green "📦 Loaded from cache" banner, which reads as "this is current". It now says
  "⚠️ Couldn't refresh — showing the saved timetable."
- **Semester 2 was never actually auto-detected.** The boundary heuristic required *both* sides of
  the ≥21-day gap to be November-or-later, which excludes January — so the December → January gap
  could never be found and the heuristic never fired. It now looks for a long gap that resumes in
  the new year.
- **Weekend classes were invisible.** Sessions on Saturday were fetched and counted but mapped to
  day index −1 with a Mon–Fri tab strip, so a part-time student's Saturday class could not be seen,
  and its day read "No classes on Mon". The strip now grows to include a weekend day the week
  actually has.
- **The group picker vanished while the filter stayed applied.** It was shown only when a week had
  more than one cohort; dropping into a single-cohort week hid the control and left the old filter
  live — an empty week with no visible cause. It now stays whenever a group is selected, and
  choosing **All groups** is remembered (it used to be forgotten, silently re-applying the old
  cohort on the next visit).
- **Week numbers shifted when "hide empty weeks" was on.** "Week 9" was the ninth *visible* week;
  it is now the ninth week of the academic year, so the label no longer changes with a setting.
- **"Remove" on a saved course left it pinned**, so the app kept opening a timetable that was no
  longer in the list. Removing now unpins it.
- **A search with a trailing space found nothing.** Queries are trimmed.
- **Denying the notifications permission made "Sync Now" do nothing**, silently. The permission
  only gates the notification, so the sync now runs and says so.
- **Rate-limit and server errors were blamed on the student's connection** ("Connection error:
  Rate limited (429)"), and other failures dumped raw exception text. Both now read as plain
  English.
- **The day strip could follow the student around**: any refresh or group change re-ran the
  "jump to the first day with classes" correction, moving them off the day they had chosen. It now
  only fires when the week changes.
- **Performance:** the Preferences reads that ran on every recomposition of the timetable are now
  remembered, and the full-year week classification runs off the main thread.

## Verified

- `tools/jvm-test-harness.sh` → 181 tests, 0 failures. `gradle testDebugUnitTest` → **246 tests,
  0 failures**.
- `gradle assembleRelease` → BUILD SUCCESSFUL, signed.
- On an emulator with the release build: opening a course, switching weeks and switching to
  Semester 2 all render consistent week/day dates ("W22 · Jan 25 – Jan 31, 2027" for Semester 2),
  with 0 fatal exceptions.

## Known, and deliberately left

- **English only** — strings are Kotlin literals, not `strings.xml`, so the app cannot be
  translated. One institution, one language; unchanged.
- **Dragging the Custom theme sliders** still repaints the app and writes preferences on every
  frame. It is responsive on the emulator but is the most obvious remaining rough edge.
- The `active_weeks_*` / `cached_week_*` preference keys still accumulate per course and are only
  removed by **Erase all app data**.
