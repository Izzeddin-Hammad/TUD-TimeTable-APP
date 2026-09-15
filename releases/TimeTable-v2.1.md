# v2.1 — privacy hardening

**Release date:** 2026-09-15
**APK:** `releases/TimeTable-v2.1-release.apk` (1.8 MB, signed release build)
**Requires:** Android 8.0+ (API 26)

> ⚠️ **TU Dublin only.** Unofficial student project, not affiliated with TU Dublin.

Installs in place over v2.0 (same key). If you are still on v1.29 or earlier, see the v2.0 notes —
those builds were signed with a different key and need a one-time uninstall.

## Privacy first

Three read-only audits were run over what the app stores on the device, what it sends off it, and
what the privacy policy claims. The findings that mattered are fixed.

- **You can now erase everything, from inside the app.** Settings → **Privacy → Erase all app data**
  deletes every byte this app has stored — the cached timetable, saved and pinned courses, search
  history, all settings, the theme, and the crash record — and restarts the app. Until now there was
  no such control: the crash record, the theme and its custom hue, the anchor weeks, the
  cached-week markers and the saved update download id survived every in-app button and could only
  be removed through Android's "Clear storage".
- **A downloaded update is verified before it is installed.** The updater used to hand whatever
  landed on disk straight to the system installer; the download URL was only allow-listed by *host*
  when it was checked, and DownloadManager follows redirects. The app now confirms the APK is signed
  with its own key first, and tells you if it is not instead of silently doing nothing.
- **The automatic update check can be turned off.** It runs on every launch and contacts GitHub,
  which discloses your IP address and when you opened the app. Settings → Privacy has the switch;
  the manual "Check for updates" button keeps working either way.
- **Permissions are least-privilege.** `WRITE_EXTERNAL_STORAGE` was declared and never used — the
  update APK goes to the app's own scoped directory, which needs no permission. Removed.
- **Notifications are hidden on a locked screen** (`VISIBILITY_PRIVATE`), so a sync completion is
  not readable over your shoulder.

## The privacy policy now describes the app

PRIVACY.md was rewritten against the code. It previously:

- claimed the app holds "nothing to access, rectify, erase" **while listing the bookmarks, pinned
  course, search queries and cached timetable it stores** — it contradicted itself;
- said "no crash reports", which read as "nothing crash-related is kept", when a crash's message and
  stack trace are in fact written to app storage (they are never transmitted — that is now stated);
- pointed at a "Clear Search History" control that does not exist;
- named "GitHub Releases API" when the code calls the **Contents** API, and listed only the update
  *check* while omitting the APK *download* as a second transfer;
- never mentioned that the app can drive the system installer (`REQUEST_INSTALL_PACKAGES`).

It now lists every stored datum with a per-item delete, states plainly what each permission is for,
and gives the in-app eraser as the answer to "how do I remove all of it".

Verified in passing: the signing key and its passwords have **never** been committed to the
repository.

## Also in this release

- **The event key is now the local wall clock, not the raw digits.** The API serialises in UTC, so
  something as ordinary as a `-05:00` timestamp was being read five hours wrong, and the same
  instant written with a different offset did not compare equal. The key is now the Dublin local
  time the timestamp denotes; a naive value is still read literally as already-local.
- **Accessibility:** the semester segmented control and the search field's clear button were under
  the 48 dp minimum touch target (32 dp and 28 dp); both are now 44 dp, and `tertiaryLabel` (used
  for hints and placeholders) was raised from 0.38 to 0.46 alpha for contrast.

## Verified

- `tools/jvm-test-harness.sh` → 181 tests, 0 failures. `gradle testDebugUnitTest` → **246 tests,
  0 failures**.
- `gradle assembleRelease` → BUILD SUCCESSFUL, signed.
- On an emulator with the release build: the Privacy card renders, **Erase all app data** wipes the
  store and restarts (the theme reset from Custom to the default, proving the preferences went with
  it), and there are 0 fatal exceptions.

## Known limitations

- **English only.** User-facing strings are Kotlin literals rather than `strings.xml`, so the app
  cannot be translated. Still deliberate (one institution, one language), still the next candidate.
- The launcher icon and the Kotlin `synchronized` database singleton remain as they were.
