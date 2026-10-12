# v2.7 — no white flash between screens, and two smaller display bugs

**Release date:** 2026-10-12
**APK:** `releases/TimeTable-v2.7-release.apk` (1.8 MB, signed release build)
**Requires:** Android 8.0+ (API 26)

> ⚠️ **TU Dublin only.** Unofficial student project, not affiliated with TU Dublin.

Installs in place over v2.0–v2.6 (same key).

## What's New

### The white flash when leaving the timetable is gone
Tapping **Search** from a timetable flashed the screen white. Two things were wrong, and both are
fixed:

- **The screen you were leaving was destroyed at the instant you tapped it.** Navigating to Search
  cleared the selected course in the same state change that switched screens, and the timetable is
  drawn from that course — so the outgoing screen rendered *nothing* for the length of the
  transition. The search screen then slid in over the bare window instead of over the timetable
  sliding away. The course is no longer cleared, so the timetable stays drawn and travels with the
  transition as it should.
- **The app never painted its own background at the window level.** The window is
  `Theme.Material.Light.NoActionBar`, whose background is near-white — so the first frame of a cold
  start or a rotation (drawing before the theme's own background arrives), and any pixel the two
  travelling screens did not cover, showed white against a cream app, or against a near-black one in
  dark mode. The window background now matches the default theme's background in both light and
  night, and the app's background is painted behind the whole screen stack, so a transition can only
  ever reveal the app's own colour.

### Returning to Search keeps its results
Results are kept when you leave the search screen, so coming back restored the list — and then
re-searched the same query, clearing that list to a spinner for a network round trip that could not
have changed anything. The screen now searches when the query *changes*, not when it is entered. A
query with nothing to show still re-runs, so a response lost to a process death heals instead of
leaving a stale "No courses found".

### The "Custom" sync-strategy button no longer shows a stray "m"
The Daily / Weekly / Custom buttons sat in a plain row, which measures its children in order and
hands each one only what is left of the width. With the chip label style this app maps
(`labelLarge = Headline`, 17 sp SemiBold) the three labels need more room than a phone's card has,
so the last button — the one that absorbs the shortfall — was squeezed until its label wrapped,
leaving the "m" of "Custom" alone on a second line. The row wraps now instead of squeezing, so no
label can be mangled. (The labels were `6h / 12h / Daily` until they were lengthened; they fitted
then.)

## Verified
- `./gradlew testDebugUnitTest` → **256 tests, 0 failures**
- `./gradlew assembleRelease` → **BUILD SUCCESSFUL**, R8-minified and signed with the release key —
  same certificate as v2.6, so it installs in place
- `aapt2 dump resources` on the built APK confirms both window-background variants are packaged
  (`() #fffbf5ec`, `(night) #ff1f1813`) and that the theme references them
- `tools/jvm-test-harness.sh` → **191 tests, 0 failures**

### Not verified here
The transitions themselves were not watched on a device — this build was verified by compiling,
by unit tests, and by inspecting the built resources. If a flash is still visible after installing,
what matters is *where* on the screen and *which* navigation, which narrows it immediately.
